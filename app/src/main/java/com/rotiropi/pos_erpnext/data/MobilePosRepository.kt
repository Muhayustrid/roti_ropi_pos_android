package com.rotiropi.pos_erpnext.data

import com.rotiropi.pos_erpnext.data.api.ApiResult
import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.data.api.BootstrapResponseDto
import com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint
import com.rotiropi.pos_erpnext.data.api.MobilePosRequest
import com.rotiropi.pos_erpnext.data.api.TransportFailureKind
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.JsonElement

/**
 * Domain model for a POS profile, kept separate from the wire DTOs.
 */
data class PosProfile(
    val name: String,
    val company: String,
    val warehouse: String,
    val currency: String,
    val sellingPriceList: String,
    val customer: String,
    val allowPartialPayment: Boolean,
    val invoiceMode: String,
    val openingPaymentModes: List<OpeningPaymentMode>,
    val openingAmountPolicy: OpeningAmountPolicy?
)

data class OpeningPaymentMode(
    val modeOfPayment: String,
    val suggestedOpeningAmount: String,
    val amountEditable: Boolean
)

data class OpeningAmountPolicy(
    val currency: String,
    val decimalPlaces: Int,
    val minimum: String,
    val apiSyntax: String,
    val rounding: String,
    val policyVersion: String
)

/**
 * Domain opening status, independent of the transport serializer enum.
 */
enum class OpeningStatus {
    OPEN,
    CLOSED,
    CANCELLED,
    UNSUPPORTED;

    companion object {
        fun from(dto: com.rotiropi.pos_erpnext.data.api.OpeningStatus): OpeningStatus = when (dto) {
            com.rotiropi.pos_erpnext.data.api.OpeningStatus.OPEN -> OPEN
            com.rotiropi.pos_erpnext.data.api.OpeningStatus.CLOSED -> CLOSED
            com.rotiropi.pos_erpnext.data.api.OpeningStatus.CANCELLED -> CANCELLED
            com.rotiropi.pos_erpnext.data.api.OpeningStatus.UNSUPPORTED -> UNSUPPORTED
        }
    }
}

data class OpeningWarning(
    val code: String,
    val message: String,
    val details: Map<String, JsonElement>
)

data class OpeningBalance(val modeOfPayment: String, val openingAmount: String)

data class OpeningSession(
    val name: String,
    val posProfile: String,
    val company: String,
    val user: String,
    val status: OpeningStatus,
    val postingDate: String,
    val periodStartDate: String,
    val openingBalances: List<OpeningBalance>,
    val warnings: List<OpeningWarning>
)

data class PosCapabilities(
    val openSession: Boolean,
    val submitSale: Boolean,
    val createReturn: Boolean,
    val cancelSale: Boolean,
    val closeSession: Boolean
) {
    val any: Boolean
        get() = openSession || submitSale || createReturn || cancelSale || closeSession

    val hasEnabled: Boolean
        get() = any

    companion object {
        val DISABLED = PosCapabilities(
            openSession = false,
            submitSale = false,
            createReturn = false,
            cancelSale = false,
            closeSession = false
        )
    }
}

data class BootstrapUser(val name: String, val fullName: String)

/**
 * Immutable, render-equivalent snapshot mapped from the versioned bootstrap DTO.
 */
data class BootstrapData(
    val user: BootstrapUser,
    val profiles: List<PosProfile>,
    val selectedProfile: PosProfile?,
    val opening: OpeningSession?,
    val capabilities: PosCapabilities,
    val posMode: String
)

sealed interface BootstrapFailure {
    data object AuthRequired : BootstrapFailure
    data object Unavailable : BootstrapFailure
    data class Protocol(val reason: String) : BootstrapFailure
}

/**
 * Snapshot published by [MobilePosRepository]. Reads are side-effect free and never
 * trigger a refresh; the repository owns all in-memory capability state.
 */
internal fun com.rotiropi.pos_erpnext.data.api.ProfileDto.toDomain(): PosProfile = PosProfile(
    name = name,
    company = company,
    warehouse = warehouse,
    currency = currency,
    sellingPriceList = selling_price_list,
    customer = customer,
    allowPartialPayment = allow_partial_payment,
    invoiceMode = invoice_mode,
    openingPaymentModes = opening_payment_modes.map {
        OpeningPaymentMode(
            modeOfPayment = it.mode_of_payment,
            suggestedOpeningAmount = it.suggested_opening_amount,
            amountEditable = it.amount_editable
        )
    },
    openingAmountPolicy = opening_amount_policy?.let {
        OpeningAmountPolicy(
            currency = it.currency,
            decimalPlaces = it.decimal_places,
            minimum = it.minimum,
            apiSyntax = it.api_syntax,
            rounding = it.rounding,
            policyVersion = it.policy_version
        )
    }
)

data class RepositoryState(
    val bootstrap: BootstrapData? = null,
    val bootstrapFailure: BootstrapFailure? = null
) {
    val selectedProfile: PosProfile?
        get() = bootstrap?.selectedProfile

    val opening: OpeningSession?
        get() = bootstrap?.opening

    val capabilities: PosCapabilities
        get() = bootstrap?.capabilities ?: PosCapabilities.DISABLED

    val profiles: List<PosProfile>
        get() = bootstrap?.profiles ?: emptyList()

    val hasSelection: Boolean
        get() = bootstrap?.selectedProfile != null
}

/**
 * Explicit refresh trigger. Only the authoritative events listed in the API
 * integration contract may request a refresh; [RETRY] re-enables mutations after
 * a failed required refresh.
 */
enum class BootstrapRefreshTrigger {
    APP_OPEN,
    AUTH_SUCCESS,
    PROFILE_SELECTED,
    PROFILE_CHANGED,
    RETRY
}

sealed interface RepositoryResult {
    data class Success(val state: RepositoryState) : RepositoryResult
    data class Failure(val reason: BootstrapFailure) : RepositoryResult
    data object Discarded : RepositoryResult
}

/**
 * Sole in-memory owner of bootstrap, profile, opening, and capability state.
 *
 * - `bootstrap(profileName)` calls the versioned bootstrap endpoint and maps the
 *   DTOs to immutable domain models.
 * - One profile is auto-selected even when the server reports no selection;
 *   multiple profiles without a selection require explicit selection and force
 *   every mutation capability off.
 * - Capabilities are server-derived only while a selection exists.
 * - `refreshCapabilities(trigger)` coalesces concurrent callers into exactly one
 *   in-flight bootstrap request; every caller observes the same result.
 * - Bootstrap completion, state observation, render-equivalent reads, and refresh
 *   failure never recursively trigger a refresh.
 */
class MobilePosRepository(
    private val client: AuthenticatedMobilePosApiClient
) {
    private val lock = Any()
    @Volatile
    private var currentState: RepositoryState = RepositoryState()
    private var epoch = 0L
    private var inFlightRefresh: PendingRefresh? = null

    val state: RepositoryState
        get() = currentState

    /**
     * Initial bootstrap. [profileName] is the caller-requested profile, forwarded
     * as the optional `pos_profile` query field.
     */
    fun bootstrap(profileName: String?): RepositoryResult {
        val query = if (profileName != null) mapOf("pos_profile" to profileName) else emptyMap()
        val requestEpoch = synchronized(lock) { epoch }
        return executeBootstrap(query, requestEpoch)
    }

    /**
     * Refreshes capabilities after an authoritative trigger. Concurrent callers
     * share one in-flight bootstrap network request and all observe its result.
     */
    fun refreshCapabilities(trigger: BootstrapRefreshTrigger): RepositoryResult {
        val pending = synchronized(lock) {
            inFlightRefresh ?: PendingRefresh(epoch, refreshQueryLocked()).also { inFlightRefresh = it }
        }
        if (pending.owner.compareAndSet(false, true)) {
            try {
                pending.result.set(executeBootstrap(pending.query, pending.epoch))
            } finally {
                pending.done.countDown()
                synchronized(lock) {
                    if (inFlightRefresh === pending) inFlightRefresh = null
                }
            }
        } else {
            pending.done.await()
        }
        return pending.result.get()
    }

    /**
     * Locally selects [profileName] from the current bootstrap profiles without any
     * network call. Returns false and changes nothing when [profileName] is unknown
     * or when no bootstrap state exists yet.
     *
     * A valid local selection immediately disables every mutation capability so the
     * profile-selection UI never enables actions on unverified state; only a later
     * authoritative [refreshCapabilities] with `PROFILE_SELECTED` or
     * `PROFILE_CHANGED` restores server-derived capabilities. The profile list, user,
     * and opening are retained so the selection UI keeps rendering.
     */
    fun selectProfile(profileName: String): Boolean {
        synchronized(lock) {
            val bootstrap = currentState.bootstrap ?: return false
            val profile = bootstrap.profiles.firstOrNull { it.name == profileName } ?: return false
            currentState = currentState.copy(
                bootstrap = bootstrap.copy(
                    selectedProfile = profile,
                    capabilities = PosCapabilities.DISABLED
                )
            )
            return true
        }
    }

    /**
     * Resets bootstrap, profile, opening, and capability state.
     */
    fun clear() {
        synchronized(lock) {
            epoch++
            currentState = RepositoryState()
            inFlightRefresh = null
        }
    }

    /**
     * Refresh preserves the currently selected profile so a multi-profile server
     * does not lose the selection during a capability refresh.
     */
    private fun refreshQueryLocked(): Map<String, String> =
        currentState.bootstrap?.selectedProfile?.name
            ?.let { mapOf("pos_profile" to it) }
            ?: emptyMap()

    private fun executeBootstrap(query: Map<String, String>, requestEpoch: Long): RepositoryResult {
        val request = MobilePosRequest.get(MobilePosEndpoint.BOOTSTRAP_GET, query)
        return when (val result = client.execute(request, BootstrapResponseDto.serializer())) {
            is ApiResult.Success -> publishSuccess(result.data.toDomain(), requestEpoch)
            is ApiResult.TransportFailure -> {
                val failure = when (result.kind) {
                    TransportFailureKind.AUTHENTICATION_REQUIRED -> BootstrapFailure.AuthRequired
                    else -> BootstrapFailure.Unavailable
                }
                publishFailure(failure, requestEpoch)
            }
            is ApiResult.ExpectedFailure -> publishFailure(BootstrapFailure.Unavailable, requestEpoch)
            is ApiResult.ProtocolFailure -> publishFailure(
                BootstrapFailure.Protocol(result.reason),
                requestEpoch
            )
        }
    }

    private fun publishSuccess(mapped: BootstrapData, requestEpoch: Long): RepositoryResult =
        synchronized(lock) {
            if (epoch != requestEpoch) return@synchronized RepositoryResult.Discarded
            currentState = RepositoryState(bootstrap = mapped)
            RepositoryResult.Success(currentState)
        }

    /**
     * Records a bootstrap/refresh failure and disables every mutation capability so
     * stale enabled capabilities are never observed after a failed required refresh.
     */
    private fun publishFailure(failure: BootstrapFailure, requestEpoch: Long): RepositoryResult =
        synchronized(lock) {
            if (epoch != requestEpoch) return@synchronized RepositoryResult.Discarded
            val previous = currentState.bootstrap
            currentState = if (previous != null) {
                currentState.copy(
                    bootstrap = previous.copy(capabilities = PosCapabilities.DISABLED),
                    bootstrapFailure = failure
                )
            } else {
                currentState.copy(bootstrapFailure = failure)
            }
            RepositoryResult.Failure(failure)
        }

    private fun BootstrapResponseDto.toDomain(): BootstrapData {
        val profiles = profiles.map { it.toDomain() }
        val selected = when {
            selected_profile != null -> selected_profile!!.toDomain()
            profiles.size == 1 -> profiles.single()
            else -> null
        }
        val capabilities = if (selected != null) {
            PosCapabilities(
                openSession = capabilities.open_session,
                submitSale = capabilities.submit_sale,
                createReturn = capabilities.create_return,
                cancelSale = capabilities.cancel_sale,
                closeSession = capabilities.close_session
            )
        } else {
            PosCapabilities.DISABLED
        }
        return BootstrapData(
            user = BootstrapUser(name = user.name, fullName = user.full_name),
            profiles = profiles,
            selectedProfile = selected,
            opening = opening_session?.toDomain(),
            capabilities = capabilities,
            posMode = pos_mode
        )
    }

    private fun com.rotiropi.pos_erpnext.data.api.OpeningSessionDto.toDomain(): OpeningSession = OpeningSession(
        name = name,
        posProfile = pos_profile,
        company = company,
        user = user,
        status = OpeningStatus.from(status),
        postingDate = posting_date,
        periodStartDate = period_start_date,
        openingBalances = opening_balances.map {
            OpeningBalance(modeOfPayment = it.mode_of_payment, openingAmount = it.opening_amount)
        },
        warnings = warnings.map {
            OpeningWarning(code = it.code, message = it.message, details = it.details)
        }
    )

    private class PendingRefresh(
        val epoch: Long,
        val query: Map<String, String>
    ) {
        val owner = AtomicBoolean(false)
        val done = CountDownLatch(1)
        val result = AtomicReference<RepositoryResult>()
    }
}
