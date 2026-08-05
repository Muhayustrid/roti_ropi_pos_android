package com.rotiropi.pos_erpnext.session

import com.rotiropi.pos_erpnext.auth.AuthenticationOwner
import com.rotiropi.pos_erpnext.data.MobilePosRepository
import com.rotiropi.pos_erpnext.recovery.PendingMutationState
import com.rotiropi.pos_erpnext.recovery.RecoveryLogoutBlocker
import com.rotiropi.pos_erpnext.recovery.SqlitePendingMutationStore
import com.rotiropi.pos_erpnext.ui.profile.ProfileSelectionViewModel

sealed interface LogoutResult {
    data object LoggedOut : LogoutResult
    data class Blocked(val cashier: String, val state: PendingMutationState) : LogoutResult
}

/**
 * Coordinates application-wide logout. Durable recovery evidence blocks cleanup.
 * Customer authority is invalidated before repository and authentication cleanup.
 */
class LogoutCoordinator internal constructor(
    private val invalidateCustomerAuthority: () -> Unit = {},
    private val cancelCustomerRequest: () -> Unit = {},
    private val clearCustomerUi: () -> Unit = {},
    private val clearRepository: () -> Unit,
    private val clearProfileUi: () -> Unit = {},
    private val clearAuthentication: () -> Unit,
    private val runCleanupIfNoRecovery: ((() -> Unit) -> RecoveryLogoutBlocker?)? = null,
) {
    constructor(
        repository: MobilePosRepository,
        profileSelectionViewModel: ProfileSelectionViewModel,
        invalidateCustomerAuthority: () -> Unit = {},
        cancelCustomerRequest: () -> Unit = {},
        clearCustomerUi: () -> Unit = {},
        authenticationOwner: AuthenticationOwner,
        pendingMutations: SqlitePendingMutationStore,
    ) : this(
        invalidateCustomerAuthority = invalidateCustomerAuthority,
        cancelCustomerRequest = cancelCustomerRequest,
        clearCustomerUi = clearCustomerUi,
        clearRepository = repository::clear,
        clearProfileUi = profileSelectionViewModel::clear,
        clearAuthentication = authenticationOwner::logout,
        runCleanupIfNoRecovery = pendingMutations::logoutIfNoRecords,
    )

    @Synchronized
    fun logout(): LogoutResult {
        val cleanup = {
            invalidateCustomerAuthority()
            cancelCustomerRequest()
            clearCustomerUi()
            clearRepository()
            clearProfileUi()
            clearAuthentication()
        }
        val blocker = runCleanupIfNoRecovery?.invoke(cleanup).also {
            if (runCleanupIfNoRecovery == null) cleanup()
        }
        return blocker?.let { LogoutResult.Blocked(it.cashier, it.state) } ?: LogoutResult.LoggedOut
    }
}
