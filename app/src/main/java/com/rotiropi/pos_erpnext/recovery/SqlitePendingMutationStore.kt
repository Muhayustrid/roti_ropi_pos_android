package com.rotiropi.pos_erpnext.recovery

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Keystore-encrypted pending mutation rows. No plaintext body reaches SQLite. */
interface PendingMutationCryptoProvider {
    fun existing(): PendingMutationCrypto?
    fun createOnFirstInstall(): PendingMutationCrypto
}

private class AndroidPendingMutationCryptoProvider(private val alias: String) : PendingMutationCryptoProvider {
    override fun existing(): PendingMutationCrypto? = PendingMutationCrypto.existingAndroidKey(alias)
    override fun createOnFirstInstall(): PendingMutationCrypto = PendingMutationCrypto.forAndroidKey(alias)
}

class SqlitePendingMutationStore(
    context: Context,
    internal val databaseNameForTest: String = DATABASE_NAME,
    suppliedCryptoProvider: PendingMutationCryptoProvider? = null,
) : SQLiteOpenHelper(context, databaseNameForTest, null, DATABASE_VERSION), PendingMutationStore {
    private val keyAlias = if (databaseNameForTest == DATABASE_NAME) KEY_ALIAS else "$KEY_ALIAS:$databaseNameForTest"
    private val cryptoProvider = suppliedCryptoProvider ?: AndroidPendingMutationCryptoProvider(keyAlias)
    private fun existingCrypto(): PendingMutationCrypto? = runCatching { cryptoProvider.existing() }.getOrNull()

    private data class EncryptedEvidenceRow(
        val transactionId: String,
        val identity: RecoveryIdentity?,
        val unresolved: Boolean,
        val body: EncryptedMutationBody,
        val terminal: EncryptedMutationBody?,
        val bodyAad: ByteArray?,
        val terminalAad: ByteArray?,
        val terminalMalformed: Boolean,
    )

    private fun SQLiteDatabase.unresolvedEvidence(identity: RecoveryIdentity): List<EncryptedEvidenceRow> =
        encryptedEvidence().filter { it.identity == identity && it.unresolved }

    private fun SQLiteDatabase.encryptedEvidence(): List<EncryptedEvidenceRow> = query(
        "mutations", null, null, null, null, null, "id",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val terminalColumns = listOf("terminal_iv", "terminal_ciphertext", "terminal_tag")
                val terminalPresent = terminalColumns.count { !cursor.isNull(cursor.column(it)) }
                val record = runCatching { cursor.metadata(cursor.identity()) }.getOrNull()
                add(EncryptedEvidenceRow(
                    transactionId = cursor.string("id"),
                    identity = record?.identity,
                    unresolved = record?.state?.terminal == false,
                    body = EncryptedMutationBody(cursor.blob("body_iv"), cursor.blob("body_ciphertext"), cursor.blob("body_tag")),
                    terminal = if (terminalPresent == terminalColumns.size) {
                        EncryptedMutationBody(cursor.blob("terminal_iv"), cursor.blob("terminal_ciphertext"), cursor.blob("terminal_tag"))
                    } else null,
                    bodyAad = record?.let(PendingMutationAad::request),
                    terminalAad = record?.takeIf {
                        it.state.terminal || it.state == PendingMutationState.CLOSING_QUEUED
                    }?.let { PendingMutationAad.terminal(it, it.state) },
                    terminalMalformed = terminalPresent != 0 && terminalPresent != terminalColumns.size,
                ))
            }
        }
    }

    private fun EncryptedEvidenceRow.decryptsWith(crypto: PendingMutationCrypto): Boolean = runCatching {
        crypto.decrypt(body, checkNotNull(bodyAad))
        check(!terminalMalformed)
        terminal?.let { crypto.decrypt(it, checkNotNull(terminalAad)) }
    }.isSuccess

    private fun SQLiteDatabase.quarantineUnresolved(identity: RecoveryIdentity): Int = update(
        "mutations",
        ContentValues().apply { put("state", PendingMutationState.MANUAL_RECOVERY.name) },
        "cashier=? AND origin=? AND client=? AND state NOT IN (?, ?, ?)",
        arrayOf(
            identity.cashier,
            identity.canonicalOrigin,
            identity.clientId,
            PendingMutationState.COMPLETED.name,
            PendingMutationState.REJECTED.name,
            PendingMutationState.MANUAL_RECOVERY.name,
        ),
    )

    private fun SQLiteDatabase.quarantineEvidence(transactionId: String): Int {
        execSQL("PRAGMA ignore_check_constraints = ON")
        return try {
            update(
                "mutations",
                ContentValues().apply { put("state", PendingMutationState.MANUAL_RECOVERY.name) },
                "id=?",
                arrayOf(transactionId),
            )
        } finally {
            execSQL("PRAGMA ignore_check_constraints = OFF")
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE mutations (
                id TEXT PRIMARY KEY,
                cashier TEXT NOT NULL,
                origin TEXT NOT NULL,
                client TEXT NOT NULL,
                endpoint TEXT NOT NULL,
                body_iv BLOB NOT NULL,
                body_ciphertext BLOB NOT NULL,
                body_tag BLOB NOT NULL,
                content_type TEXT NOT NULL,
                serializer TEXT NOT NULL,
                body_format INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                state TEXT NOT NULL,
                attempts INTEGER NOT NULL,
                next_eligible_at INTEGER NOT NULL,
                reference TEXT,
                terminal_iv BLOB,
                terminal_ciphertext BLOB,
                terminal_tag BLOB,
                CHECK ((terminal_iv IS NULL AND terminal_ciphertext IS NULL AND terminal_tag IS NULL) OR

                    (terminal_iv IS NOT NULL AND terminal_ciphertext IS NOT NULL AND terminal_tag IS NOT NULL))
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX one_unresolved_mutation_per_identity ON mutations(cashier, origin, client) " +
                "WHERE state NOT IN ('COMPLETED', 'REJECTED')",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun prepare(record: PendingMutation): PendingMutationAdmission = synchronized(RecoveryLogoutGate.lock) {
        require(record.state == PendingMutationState.PREPARED)
        writableDatabase.transactionResult {
            val matchingEvidence = unresolvedEvidence(record.identity)
            val crypto = try { cryptoProvider.existing() } catch (_: Exception) { null }
            if (matchingEvidence.isNotEmpty()) {
                val readable = crypto != null && matchingEvidence.all { it.decryptsWith(crypto) }
                if (!readable) {
                    quarantineUnresolved(record.identity)
                    return@transactionResult PendingMutationAdmission.MANUAL_RECOVERY_REQUIRED(
                        matchingEvidence.first().transactionId,
                    )
                }
                return@transactionResult PendingMutationAdmission.UNRESOLVED_EXISTS
            }
            val evidence = encryptedEvidence()
            val invalidEvidence = evidence.firstOrNull { crypto == null || !it.decryptsWith(crypto) }
            if (invalidEvidence != null) {
                quarantineEvidence(invalidEvidence.transactionId)
                return@transactionResult PendingMutationAdmission.MANUAL_RECOVERY_REQUIRED(
                    invalidEvidence.transactionId,
                )
            }
            val usableCrypto = try {
                crypto ?: cryptoProvider.createOnFirstInstall()
            } catch (_: Exception) {
                return@transactionResult PendingMutationAdmission.CRYPTO_FAILURE
            }
            val body = try {
                usableCrypto.encrypt(record.body, PendingMutationAad.request(record))
            } catch (_: Exception) {
                return@transactionResult PendingMutationAdmission.CRYPTO_FAILURE
            }
            try {
                insertOrThrow("mutations", null, record.prepareValues(body))
                PendingMutationAdmission.ACCEPTED
            } catch (error: SQLiteConstraintException) {
                if (isUnresolvedIdentityConflict(error)) PendingMutationAdmission.UNRESOLVED_EXISTS else throw error
            }
        }
    }

    private fun isUnresolvedIdentityConflict(error: SQLiteConstraintException): Boolean =
        error.message?.contains("one_unresolved_mutation_per_identity") == true
            || error.message?.contains("mutations.cashier, mutations.origin, mutations.client") == true
            || error.message?.contains("UNIQUE constraint failed") == true

    override fun beginSending(
        transactionId: String,
        expectedIdentity: RecoveryIdentity,
        expectedState: PendingMutationState,
        expectedAttemptCount: Int,
        expectedNextEligibleAtMillis: Long,
    ): PendingMutation? = writableDatabase.transactionResult {
        if (expectedState == PendingMutationState.MANUAL_RECOVERY || expectedState.terminal) return@transactionResult null
        val changed = update(
            "mutations",
            ContentValues().apply {
                put("state", PendingMutationState.SENDING.name)
                put("attempts", expectedAttemptCount + 1)
            },
            "${identityWhere(transactionId)} AND state=? AND attempts=? AND next_eligible_at=?",
            identityArgs(transactionId, expectedIdentity) + arrayOf(
                expectedState.name,
                expectedAttemptCount.toString(),
                expectedNextEligibleAtMillis.toString(),
            ),
        )
        if (changed != 1) return@transactionResult null
        query("mutations", null, identityWhere(transactionId), identityArgs(transactionId, expectedIdentity), null, null, null)
            .use {
                check(it.moveToFirst())
                decodeOrManualRecovery(it).takeIf {
                    it.state == PendingMutationState.SENDING && it.body.isNotEmpty()
                }
            }
    }

    override fun finishSending(
        transactionId: String,
        expectedIdentity: RecoveryIdentity,
        state: PendingMutationState,
        attemptCount: Int,
        nextEligibleAtMillis: Long,
        reference: String?,
    ): Boolean {
        require(state != PendingMutationState.SENDING && state != PendingMutationState.MANUAL_RECOVERY && !state.terminal)
        return writableDatabase.transactionResult {
            update(
                "mutations",
                ContentValues().apply {
                    put("state", state.name)
                    put("attempts", attemptCount)
                    put("next_eligible_at", nextEligibleAtMillis)
                    put("reference", reference)
                },
                "${identityWhere(transactionId)} AND state=?",
                identityArgs(transactionId, expectedIdentity) + arrayOf(PendingMutationState.SENDING.name),
            ) == 1
        }
    }

    override fun markManualRecovery(transactionId: String, expectedIdentity: RecoveryIdentity): Boolean =
        writableDatabase.transactionResult {
            update(
                "mutations",
                ContentValues().apply { put("state", PendingMutationState.MANUAL_RECOVERY.name) },
                "${identityWhere(transactionId)} AND state NOT IN (?, ?, ?)",
                identityArgs(transactionId, expectedIdentity) + arrayOf(
                    PendingMutationState.COMPLETED.name,
                    PendingMutationState.REJECTED.name,
                    PendingMutationState.MANUAL_RECOVERY.name,
                ),
            ) == 1
        }

    override fun persistTerminal(
        transactionId: String,
        expectedIdentity: RecoveryIdentity,
        state: PendingMutationState,
        terminalResponse: ByteArray,
        reference: String?,
    ): Boolean {
        require(state.terminal)
        val terminal = try {
            val record = findMetadata(transactionId, expectedIdentity)
                ?: return false
            (existingCrypto() ?: throw PendingMutationCryptoException(IllegalStateException("Pending mutation key missing")))
                .encrypt(terminalResponse, PendingMutationAad.terminal(record, state))
        } catch (_: Exception) {
            markManualRecovery(transactionId, expectedIdentity)
            return false
        }
        return writableDatabase.transactionResult {
            update(
                "mutations",
                ContentValues().apply {
                    put("state", state.name)
                    put("reference", reference)
                    put("terminal_iv", terminal.iv)
                    put("terminal_ciphertext", terminal.ciphertext)
                    put("terminal_tag", terminal.tag)
                },
                "${identityWhere(transactionId)} AND state=?",
                identityArgs(transactionId, expectedIdentity) + arrayOf(PendingMutationState.SENDING.name),
            ) == 1
        }
    }

    override fun persistClosingQueued(
        transactionId: String,
        expectedIdentity: RecoveryIdentity,
        response: ByteArray,
        reference: String,
    ): Boolean = persistClosingEvidence(
        transactionId,
        expectedIdentity,
        PendingMutationState.SENDING,
        PendingMutationState.CLOSING_QUEUED,
        response,
        reference,
    )

    override fun persistClosingStatusTerminal(
        transactionId: String,
        expectedIdentity: RecoveryIdentity,
        response: ByteArray,
        reference: String,
    ): Boolean {
        val expectedState = findMetadata(transactionId, expectedIdentity)?.state
            ?.takeIf { it in setOf(PendingMutationState.CLOSING_QUEUED, PendingMutationState.MANUAL_RECOVERY) }
            ?: return false
        return persistClosingEvidence(
            transactionId,
            expectedIdentity,
            expectedState,
            PendingMutationState.COMPLETED,
            response,
            reference,
        )
    }

    private fun persistClosingEvidence(
        transactionId: String,
        expectedIdentity: RecoveryIdentity,
        expectedState: PendingMutationState,
        targetState: PendingMutationState,
        response: ByteArray,
        reference: String,
    ): Boolean {
        val terminal = try {
            val record = findMetadata(transactionId, expectedIdentity)
                ?.takeIf { it.endpoint == MobilePosEndpoint.CLOSING_SUBMIT && it.state == expectedState }
                ?: return false
            (existingCrypto() ?: throw PendingMutationCryptoException(IllegalStateException("Pending mutation key missing")))
                .encrypt(response, PendingMutationAad.terminal(record, targetState))
        } catch (_: Exception) {
            markManualRecovery(transactionId, expectedIdentity)
            return false
        }
        return writableDatabase.transactionResult {
            update(
                "mutations",
                ContentValues().apply {
                    put("state", targetState.name)
                    put("reference", reference)
                    put("terminal_iv", terminal.iv)
                    put("terminal_ciphertext", terminal.ciphertext)
                    put("terminal_tag", terminal.tag)
                },
                "${identityWhere(transactionId)} AND endpoint=? AND state=?",
                identityArgs(transactionId, expectedIdentity) + arrayOf(
                    MobilePosEndpoint.CLOSING_SUBMIT.name,
                    expectedState.name,
                ),
            ) == 1
        }
    }

    override fun find(transactionId: String, expectedIdentity: RecoveryIdentity): PendingMutation? = readableDatabase.query(
        "mutations", null, identityWhere(transactionId), identityArgs(transactionId, expectedIdentity), null, null, null,
    ).use { if (it.moveToFirst()) decodeOrManualRecovery(it) else null }

    private fun findMetadata(transactionId: String, expectedIdentity: RecoveryIdentity): PendingMutation? = readableDatabase.query(
        "mutations", null, identityWhere(transactionId), identityArgs(transactionId, expectedIdentity), null, null, null,
    ).use { if (it.moveToFirst()) runCatching { it.metadata(expectedIdentity) }.getOrNull() else null }

    override fun readClosingResult(
        transactionId: String,
        expectedIdentity: RecoveryIdentity,
    ): ValidatedClosingResult? = writableDatabase.transactionResult {
        val record = findMetadata(transactionId, expectedIdentity)
            ?.takeIf {
                it.endpoint == MobilePosEndpoint.CLOSING_SUBMIT &&
                    it.state in setOf(PendingMutationState.CLOSING_QUEUED, PendingMutationState.COMPLETED)
            }
            ?: return@transactionResult null
        val response = query(
            "mutations", null, identityWhere(transactionId), identityArgs(transactionId, expectedIdentity), null, null, null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            runCatching {
                val bytes = (existingCrypto() ?: error("Pending mutation key missing"))
                    .decrypt(cursor.requiredTerminalBlob(), PendingMutationAad.terminal(record, record.state))
                bytes.decodeToSafeTerminalText()
            }.getOrNull()
        }
        if (response == null) {
            quarantineEvidence(transactionId)
            return@transactionResult null
        }
        ValidatedClosingResult(
            transactionId = transactionId,
            reference = record.reference ?: return@transactionResult null,
            responseText = response,
        )
    }

    override fun readTerminalResult(
        transactionId: String,
        expectedIdentity: RecoveryIdentity,
    ): ValidatedTerminalResult? = writableDatabase.transactionResult {
        val record = findMetadata(transactionId, expectedIdentity) ?: return@transactionResult null
        if (!record.state.terminal) return@transactionResult null
        val terminal = query(
            "mutations", null, identityWhere(transactionId), identityArgs(transactionId, expectedIdentity), null, null, null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            runCatching {
                val encrypted = cursor.requiredTerminalBlob()
                val bytes = (existingCrypto() ?: error("Pending mutation key missing"))
                    .decrypt(encrypted, PendingMutationAad.terminal(record, record.state))
                bytes.decodeToSafeTerminalText()
            }.getOrNull()
        }
        if (terminal == null) {
            quarantineEvidence(transactionId)
            return@transactionResult null
        }
        ValidatedTerminalResult(
            transactionId = transactionId,
            state = record.state,
            endpoint = record.endpoint,
            reference = record.reference,
            responseText = terminal,
            token = TerminalReadToken(transactionId, TERMINAL_RESULT_FORMAT_VERSION),
        )
    }

    override fun acknowledge(token: TerminalReadToken, expectedIdentity: RecoveryIdentity): Boolean =
        writableDatabase.transactionResult {
            if (token.formatVersion != TERMINAL_RESULT_FORMAT_VERSION) return@transactionResult false
            val record = findMetadata(token.transactionId, expectedIdentity) ?: return@transactionResult false
            if (!record.state.terminal) return@transactionResult false
            val valid = query(
                "mutations", null, identityWhere(token.transactionId), identityArgs(token.transactionId, expectedIdentity), null, null, null,
            ).use { cursor ->
                cursor.moveToFirst() && runCatching {
                    val bytes = (existingCrypto() ?: error("Pending mutation key missing"))
                        .decrypt(cursor.requiredTerminalBlob(), PendingMutationAad.terminal(record, record.state))
                    bytes.decodeToSafeTerminalText()
                }.isSuccess
            }
            if (!valid) {
                quarantineEvidence(token.transactionId)
                return@transactionResult false
            }
            delete(
                "mutations", "${identityWhere(token.transactionId)} AND state IN (?, ?)",
                identityArgs(token.transactionId, expectedIdentity) + arrayOf(
                    PendingMutationState.COMPLETED.name, PendingMutationState.REJECTED.name,
                ),
            ) == 1
        }

    private fun ByteArray.decodeToSafeTerminalText(): String {
        require(size <= MAX_TERMINAL_RESPONSE_BYTES)
        val text = decodeToString(throwOnInvalidSequence = true)
        require(text.none { it.code < 0x20 && it !in setOf('\n', '\r', '\t') || it.code == 0x7f })
        return text
    }

    private fun Cursor.requiredTerminalBlob(): EncryptedMutationBody {
        check(!isNull(column("terminal_iv")) && !isNull(column("terminal_ciphertext")) && !isNull(column("terminal_tag")))
        return bodyBlob("terminal")
    }

    override fun unresolved(expectedIdentity: RecoveryIdentity): List<PendingMutation> {
        val cursor = readableDatabase.query(
            "mutations",
            null,
            "cashier=? AND origin=? AND client=? AND state NOT IN (?, ?)",
            arrayOf(
                expectedIdentity.cashier,
                expectedIdentity.canonicalOrigin,
                expectedIdentity.clientId,
                PendingMutationState.COMPLETED.name,
                PendingMutationState.REJECTED.name,
            ),
            null,
            null,
            null,
        )
        cursor.use { return buildList { while (it.moveToNext()) add(decodeOrManualRecovery(it)) } }
    }

    override fun terminalRecovery(expectedIdentity: RecoveryIdentity): TerminalRecovery? = readableDatabase.query(
        "mutations",
        arrayOf("id", "state"),
        "cashier=? AND origin=? AND client=? AND state IN (?, ?)",
        arrayOf(
            expectedIdentity.cashier,
            expectedIdentity.canonicalOrigin,
            expectedIdentity.clientId,
            PendingMutationState.COMPLETED.name,
            PendingMutationState.REJECTED.name,
        ),
        null,
        null,
        "created_at, id",
        "1",
    ).use {
        if (it.moveToFirst()) TerminalRecovery(it.string("id"), PendingMutationState.valueOf(it.string("state"))) else null
    }

    override fun recoverStaleSending(transactionId: String, expectedIdentity: RecoveryIdentity): Boolean =
        writableDatabase.transactionResult {
            update(
                "mutations",
                ContentValues().apply { put("state", PendingMutationState.WAITING_RETRY.name) },
                "${identityWhere(transactionId)} AND state=?",
                identityArgs(transactionId, expectedIdentity) + arrayOf(PendingMutationState.SENDING.name),
            ) == 1
        }

    override fun logoutIfNoRecords(cleanup: () -> Unit): RecoveryLogoutBlocker? = synchronized(RecoveryLogoutGate.lock) {
        writableDatabase.transactionResult {
        query(
            "mutations",
            arrayOf("cashier", "state"),
            null,
            null,
            null,
            null,
            "created_at, id",
            "1",
        ).use {
            if (it.moveToFirst()) return@transactionResult RecoveryLogoutBlocker(
                cashier = it.string("cashier"),
                state = PendingMutationState.valueOf(it.string("state")),
            )
        }
            cleanup()
            null
        }
    }

    private fun decodeOrManualRecovery(cursor: Cursor): PendingMutation {
        val identity = cursor.identity()
        val record = cursor.metadata(identity)
        if (record.bodyFormatVersion != PendingMutation.BODY_FORMAT_VERSION) {
            markManualRecovery(record.transactionId, identity)
            return record.copy(state = PendingMutationState.MANUAL_RECOVERY, body = ByteArray(0))
        }
        return try {
            val crypto = existingCrypto() ?: throw PendingMutationCryptoException(IllegalStateException("Pending mutation key missing"))
            record.copy(
                body = crypto.decrypt(cursor.bodyBlob("body"), PendingMutationAad.request(record)),
                terminalResponse = cursor.nullableBlob("terminal")?.let {
                    crypto.decrypt(it, PendingMutationAad.terminal(record, record.state))
                },
            )
        } catch (_: Exception) {
            markManualRecovery(record.transactionId, identity)
            record.copy(state = PendingMutationState.MANUAL_RECOVERY, body = ByteArray(0), terminalResponse = null)
        }
    }

    private fun PendingMutation.prepareValues(body: EncryptedMutationBody): ContentValues = ContentValues().apply {
        put("id", transactionId)
        put("cashier", identity.cashier)
        put("origin", identity.canonicalOrigin)
        put("client", identity.clientId)
        put("endpoint", endpoint.name)
        put("body_iv", body.iv)
        put("body_ciphertext", body.ciphertext)
        put("body_tag", body.tag)
        put("content_type", contentType)
        put("serializer", serializerIdentity)
        put("body_format", bodyFormatVersion)
        put("created_at", createdAtMillis)
        put("state", state.name)
        put("attempts", attemptCount)
        put("next_eligible_at", nextEligibleAtMillis)
        put("reference", reference)
    }

    private fun Cursor.metadata(identity: RecoveryIdentity): PendingMutation = PendingMutation(
        transactionId = string("id"),
        identity = identity,
        endpoint = MobilePosEndpoint.valueOf(string("endpoint")),
        body = ByteArray(0),
        contentType = string("content_type"),
        serializerIdentity = string("serializer"),
        bodyFormatVersion = int("body_format"),
        createdAtMillis = long("created_at"),
        state = PendingMutationState.valueOf(string("state")),
        attemptCount = int("attempts"),
        nextEligibleAtMillis = long("next_eligible_at"),
        reference = nullableString("reference"),
    )

    private fun Cursor.identity() = RecoveryIdentity(string("cashier"), string("origin"), string("client"))
    private fun Cursor.bodyBlob(prefix: String) = EncryptedMutationBody(blob("${prefix}_iv"), blob("${prefix}_ciphertext"), blob("${prefix}_tag"))
    private fun Cursor.nullableBlob(prefix: String): EncryptedMutationBody? =
        if (isNull(column(prefix + "_iv"))) null else bodyBlob(prefix)

    internal data class EncryptedEvidence(
        val bodyIv: ByteArray,
        val bodyCiphertext: ByteArray,
        val bodyTag: ByteArray,
        val terminalIv: ByteArray?,
        val terminalCiphertext: ByteArray?,
        val terminalTag: ByteArray?,
        val manualRecovery: Boolean,
    )

    internal fun encryptedEvidenceForTest(transactionId: String): EncryptedEvidence? = readableDatabase.query(
        "mutations", null, "id=?", arrayOf(transactionId), null, null, null,
    ).use {
        if (!it.moveToFirst()) return null
        EncryptedEvidence(
            bodyIv = it.blob("body_iv"),
            bodyCiphertext = it.blob("body_ciphertext"),
            bodyTag = it.blob("body_tag"),
            terminalIv = if (it.isNull(it.column("terminal_iv"))) null else it.blob("terminal_iv"),
            terminalCiphertext = if (it.isNull(it.column("terminal_ciphertext"))) null else it.blob("terminal_ciphertext"),
            terminalTag = if (it.isNull(it.column("terminal_tag"))) null else it.blob("terminal_tag"),
            manualRecovery = it.string("state") == PendingMutationState.MANUAL_RECOVERY.name,
        )
    }

    internal fun tamperBodyTagForTest(transactionId: String) {
        writableDatabase.transaction {
            rawQuery("SELECT body_tag FROM mutations WHERE id=?", arrayOf(transactionId)).use {
                check(it.moveToFirst())
                val tag = it.blob("body_tag").also { bytes -> bytes[0] = (bytes[0].toInt() xor 1).toByte() }
                update("mutations", ContentValues().apply { put("body_tag", tag) }, "id=?", arrayOf(transactionId))
            }
        }
    }

    internal fun tamperTerminalTagForTest(transactionId: String) {
        tamperTerminalColumnForTest(transactionId, "terminal_tag")
    }

    internal fun tamperTerminalCiphertextForTest(transactionId: String) {
        tamperTerminalColumnForTest(transactionId, "terminal_ciphertext")
    }

    private fun tamperTerminalColumnForTest(transactionId: String, column: String) {
        writableDatabase.transaction {
            rawQuery("SELECT $column FROM mutations WHERE id=?", arrayOf(transactionId)).use {
                check(it.moveToFirst())
                val bytes = it.blob(column).also { value -> value[0] = (value[0].toInt() xor 1).toByte() }
                update("mutations", ContentValues().apply { put(column, bytes) }, "id=?", arrayOf(transactionId))
            }
        }
    }

    internal fun clearTerminalTagForTest(transactionId: String) {
        writableDatabase.execSQL("PRAGMA ignore_check_constraints = ON")
        try {
            writableDatabase.update("mutations", ContentValues().apply { putNull("terminal_tag") }, "id=?", arrayOf(transactionId))
        } finally {
            writableDatabase.execSQL("PRAGMA ignore_check_constraints = OFF")
        }
    }

    internal fun forceBodyFormatForTest(transactionId: String, version: Int) {
        writableDatabase.update("mutations", ContentValues().apply { put("body_format", version) }, "id=?", arrayOf(transactionId))
    }

    internal fun deleteKeyForTest() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(keyAlias) }
    }

    internal fun hasKeyForTest(): Boolean = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(keyAlias)

    private fun identityWhere(transactionId: String) = "id=? AND cashier=? AND origin=? AND client=?"
    private fun identityArgs(transactionId: String, identity: RecoveryIdentity) = arrayOf(
        transactionId,
        identity.cashier,
        identity.canonicalOrigin,
        identity.clientId,
    )
    private fun Cursor.column(name: String) = getColumnIndexOrThrow(name)
    private fun Cursor.string(name: String) = getString(column(name))
    private fun Cursor.nullableString(name: String) = if (isNull(column(name))) null else string(name)
    private fun Cursor.int(name: String) = getInt(column(name))
    private fun Cursor.long(name: String) = getLong(column(name))
    private fun Cursor.blob(name: String) = getBlob(column(name))
    private fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }
    private fun <T> SQLiteDatabase.transactionResult(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        return try {
            block().also { setTransactionSuccessful() }
        } finally {
            endTransaction()
        }
    }

    internal fun tamperMetadataForTest(transactionId: String, column: String, value: String) {
        require(column in setOf("id", "cashier", "origin", "client", "endpoint", "content_type", "serializer", "body_format", "state"))
        writableDatabase.update("mutations", ContentValues().apply { put(column, value) }, "id=?", arrayOf(transactionId))
    }

    internal fun replaceMetadataForTest(transactionId: String, replacement: PendingMutation) {
        writableDatabase.update("mutations", ContentValues().apply {
            put("id", replacement.transactionId)
            put("cashier", replacement.identity.cashier)
            put("origin", replacement.identity.canonicalOrigin)
            put("client", replacement.identity.clientId)
            put("endpoint", replacement.endpoint.name)
            put("content_type", replacement.contentType)
            put("serializer", replacement.serializerIdentity)
            put("body_format", replacement.bodyFormatVersion)
        }, "id=?", arrayOf(transactionId))
    }

    internal fun tamperTerminalStateForTest(transactionId: String, state: PendingMutationState) {
        writableDatabase.update("mutations", ContentValues().apply { put("state", state.name) }, "id=?", arrayOf(transactionId))
    }

    internal fun tamperTerminalAadFormatForTest(transactionId: String) {
        writableDatabase.update("mutations", ContentValues().apply { put("serializer", "tampered-terminal-format") }, "id=?", arrayOf(transactionId))
    }

    internal fun tamperTerminalResponseForTest(transactionId: String) {
        tamperTerminalCiphertextForTest(transactionId)
    }

    internal fun acknowledgeManualRecoveryForTest(transactionId: String): Boolean =
        writableDatabase.delete(
            "mutations",
            "id=? AND state=?",
            arrayOf(transactionId, PendingMutationState.MANUAL_RECOVERY.name),
        ) == 1

    companion object {
        private const val DATABASE_NAME = "pending-mutations.db"
        private const val DATABASE_VERSION = 3
        private const val KEY_ALIAS = "mobile_pos_pending_mutations_v1"
        private const val MAX_TERMINAL_RESPONSE_BYTES = 64 * 1024
    }
}

fun PendingMutationCrypto.Companion.existingAndroidKey(alias: String): PendingMutationCrypto? {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    return (store.getKey(alias, null) as? SecretKey)?.let(PendingMutationCrypto::fromKey)
}

fun PendingMutationCrypto.Companion.forAndroidKey(alias: String): PendingMutationCrypto {
    existingAndroidKey(alias)?.let { return it }
    val key = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        "AndroidKeyStore",
    ).apply {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build(),
        )
    }.generateKey()
    return PendingMutationCrypto.fromKey(key)
}
