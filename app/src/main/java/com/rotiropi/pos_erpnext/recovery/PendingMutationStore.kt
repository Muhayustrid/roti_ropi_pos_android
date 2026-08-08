package com.rotiropi.pos_erpnext.recovery

sealed interface PendingMutationAdmission {
    data object ACCEPTED : PendingMutationAdmission
    data object UNRESOLVED_EXISTS : PendingMutationAdmission
    data class MANUAL_RECOVERY_REQUIRED(val evidenceTransactionId: String) : PendingMutationAdmission
    data object CRYPTO_FAILURE : PendingMutationAdmission
}

/** Metadata-only logout blocker. Never decrypts request or terminal evidence. */
data class RecoveryLogoutBlocker(val cashier: String, val state: PendingMutationState)

data class TerminalRecovery(
    val transactionId: String,
    val state: PendingMutationState,
)

/** Opaque terminal review version. Store revalidates before every destructive acknowledgement. */
class TerminalReadToken internal constructor(
    val transactionId: String,
    val formatVersion: Int,
)

data class ValidatedTerminalResult(
    val transactionId: String,
    val state: PendingMutationState,
    val endpoint: com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint,
    val reference: String?,
    val responseText: String,
    val token: TerminalReadToken,
)

data class ValidatedClosingResult(
    val transactionId: String,
    val reference: String,
    val responseText: String,
)

const val TERMINAL_RESULT_FORMAT_VERSION = 1

/** Identity must be supplied before this boundary decrypts a row. */
interface PendingMutationStore {
    fun prepare(record: PendingMutation): PendingMutationAdmission
    /** Atomically owns dispatch. Only expected state/count/time may become SENDING. */
    fun beginSending(
        transactionId: String,
        expectedIdentity: RecoveryIdentity,
        expectedState: PendingMutationState,
        expectedAttemptCount: Int,
        expectedNextEligibleAtMillis: Long,
    ): PendingMutation?
    /** State transition from SENDING only. Never changes terminal/manual rows. */
    fun finishSending(
        transactionId: String,
        expectedIdentity: RecoveryIdentity,
        state: PendingMutationState,
        attemptCount: Int,
        nextEligibleAtMillis: Long,
        reference: String?,
    ): Boolean
    /** Metadata-only transition for non-dispatch paths. */
    fun markManualRecovery(transactionId: String, expectedIdentity: RecoveryIdentity): Boolean
    fun persistTerminal(
        transactionId: String,
        expectedIdentity: RecoveryIdentity,
        state: PendingMutationState,
        terminalResponse: ByteArray,
        reference: String?,
    ): Boolean
    fun persistClosingQueued(
        transactionId: String,
        expectedIdentity: RecoveryIdentity,
        response: ByteArray,
        reference: String,
    ): Boolean
    fun persistClosingStatusTerminal(
        transactionId: String,
        expectedIdentity: RecoveryIdentity,
        response: ByteArray,
        reference: String,
    ): Boolean
    fun find(transactionId: String, expectedIdentity: RecoveryIdentity): PendingMutation?
    fun unresolved(expectedIdentity: RecoveryIdentity): List<PendingMutation>
    /** Metadata-only summary. It never decrypts terminal evidence. */
    fun terminalRecovery(expectedIdentity: RecoveryIdentity): TerminalRecovery?
    /** Same-identity terminal decrypt, AAD, format, UTF-8, and bounded-safe-text validation. */
    fun readTerminalResult(transactionId: String, expectedIdentity: RecoveryIdentity): ValidatedTerminalResult?
    /** Reads queued Closing response without exposing it as generic terminal recovery. */
    fun readClosingResult(transactionId: String, expectedIdentity: RecoveryIdentity): ValidatedClosingResult?
    /** Process restart only: transition exactly one persisted sending record to waiting retry. */
    fun recoverStaleSending(transactionId: String, expectedIdentity: RecoveryIdentity): Boolean
    /** Revalidates terminal evidence under one transaction before deleting reviewed terminal evidence. */
    fun acknowledge(token: TerminalReadToken, expectedIdentity: RecoveryIdentity): Boolean
    /**
     * Checks durable metadata and runs cleanup under same store transaction only when no row exists.
     * This prevents recovery from persisting identity-bound evidence after token cleanup begins.
     */
    fun logoutIfNoRecords(cleanup: () -> Unit): RecoveryLogoutBlocker?
}
