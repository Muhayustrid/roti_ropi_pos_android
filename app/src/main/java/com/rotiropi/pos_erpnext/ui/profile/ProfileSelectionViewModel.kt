package com.rotiropi.pos_erpnext.ui.profile

import com.rotiropi.pos_erpnext.data.BootstrapFailure
import com.rotiropi.pos_erpnext.data.BootstrapRefreshTrigger
import com.rotiropi.pos_erpnext.data.MobilePosRepository
import com.rotiropi.pos_erpnext.data.PosProfile
import com.rotiropi.pos_erpnext.data.RepositoryResult
import com.rotiropi.pos_erpnext.recovery.RecoveryScreenState
import com.rotiropi.pos_erpnext.recovery.RecoveryUiState
import com.rotiropi.pos_erpnext.session.LogoutResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Immutable, render-equivalent snapshot for the profile-selection UI. Constructed
 * from the repository's published state; [refreshing] and the error/retry signals
 * describe the most recent [ProfileSelectionViewModel] action only, so the UI can
 * render an explicit retry affordance without any automatic retry or loop.
 */
data class ProfileSelectionUiState(
    val profiles: List<PosProfile>,
    val selectedProfileName: String?,
    val selectionRequired: Boolean,
    val refreshing: Boolean,
    val error: String?,
    val retryRequired: Boolean,
    val anyActionEnabled: Boolean,
    val logoutBlockedMessage: String? = null,
    val recovery: RecoveryScreenState = RecoveryScreenState.Hidden,
)

/**
 * Synchronous adapter over [MobilePosRepository] for the profile-selection flow.
 *
 * - Constructing the ViewModel or reading [uiState] never starts a network refresh;
 *   the repository publishes an immutable snapshot and this adapter renders it.
 * - [selectProfile] derives the authoritative refresh trigger from the pre-action
 *   state: [BootstrapRefreshTrigger.PROFILE_SELECTED] when no profile was selected
 *   before the action, [BootstrapRefreshTrigger.PROFILE_CHANGED] when a different
 *   profile was selected. Re-selecting the already selected profile is a no-op.
 * - An invalid selection (unknown name) exposes an invalid-selection error and
 *   never starts a refresh.
 * - [retry] issues exactly one [BootstrapRefreshTrigger.RETRY] refresh when the
 *   previous required refresh failed; otherwise it is a no-op.
 *
 * The caller owns the thread/dispatcher; the repository is synchronous, so no
 * Android framework, coroutine, or dependency is required.
 */
class ProfileSelectionViewModel(
    private val repository: MobilePosRepository
) {
    private val lock = Any()
    private var refreshing = false
    private var actionError: String? = null
    private var retryRequired = false
    private var actionInFlight = false
    private var actionEpoch = 0L
    private var logoutBlockedMessage: String? = null
    private var recovery: RecoveryScreenState = RecoveryScreenState.Hidden
    private val _state = MutableStateFlow(renderLocked())
    val state: StateFlow<ProfileSelectionUiState> = _state.asStateFlow()

    val uiState: ProfileSelectionUiState
        get() = synchronized(lock) {
            publishLocked()
            state.value
        }

    fun selectProfile(profileName: String) {
        val action = synchronized(lock) {
            if (actionInFlight || repository.state.selectedProfile?.name == profileName) return
            actionInFlight = true
            refreshing = true
            publishLocked()
            val trigger = if (repository.state.selectedProfile == null) {
                BootstrapRefreshTrigger.PROFILE_SELECTED
            } else {
                BootstrapRefreshTrigger.PROFILE_CHANGED
            }
            Action(actionEpoch, trigger)
        }
        if (!repository.selectProfile(profileName)) {
            synchronized(lock) {
                if (action.epoch != actionEpoch) return@synchronized
                actionError = "Profile $profileName is not available."
                refreshing = false
                retryRequired = false
                actionInFlight = false
                publishLocked()
            }
            return
        }
        finishAction(action, repository.refreshCapabilities(action.trigger))
    }

    fun retry() {
        val action = synchronized(lock) {
            if (actionInFlight || !retryRequired) return
            actionInFlight = true
            refreshing = true
            publishLocked()
            Action(actionEpoch, BootstrapRefreshTrigger.RETRY)
        }
        finishAction(action, repository.refreshCapabilities(action.trigger))
    }

    fun setRecoveryState(logoutResult: LogoutResult?, recoveryUi: RecoveryScreenState) {
        synchronized(lock) {
            val blockedMessage = (logoutResult as? LogoutResult.Blocked)?.let {
                "Sign out blocked: ${it.cashier} has ${it.state.name.lowercase()} recovery."
            }
            if (logoutBlockedMessage == blockedMessage && recovery == recoveryUi) return
            logoutBlockedMessage = blockedMessage
            recovery = recoveryUi
            publishLocked()
        }
    }

    fun setRecoveryState(logoutResult: LogoutResult?, recovery: RecoveryUiState) =
        setRecoveryState(logoutResult, recovery.retrySchedulingFailedTransactionId?.let(RecoveryScreenState::RetrySchedulingFailed)
            ?: RecoveryScreenState.Hidden)

    fun synchronizeFromRepository() {
        synchronized(lock) {
            publishLocked()
        }
    }

    fun clear() {
        synchronized(lock) {
            actionEpoch++
            refreshing = false
            actionError = null
            retryRequired = false
            actionInFlight = false
            publishLocked()
        }
    }

    private fun finishAction(action: Action, result: RepositoryResult) {
        synchronized(lock) {
            if (action.epoch != actionEpoch) return
            actionInFlight = false
            refreshing = false
            when (result) {
                is RepositoryResult.Success -> {
                    actionError = null
                    retryRequired = false
                }
                is RepositoryResult.Failure -> {
                    actionError = result.reason.message()
                    retryRequired = true
                }
                RepositoryResult.Discarded -> Unit
            }
            publishLocked()
        }
    }

    private fun publishLocked() {
        _state.value = renderLocked()
    }

    private fun renderLocked(): ProfileSelectionUiState {
        val state = repository.state
        val selectedName = state.selectedProfile?.name
        return ProfileSelectionUiState(
            profiles = state.profiles,
            selectedProfileName = selectedName,
            selectionRequired = state.hasSelection.not() && state.profiles.size > 1,
            refreshing = refreshing,
            error = actionError,
            retryRequired = retryRequired,
            anyActionEnabled = state.capabilities.hasEnabled,
            logoutBlockedMessage = logoutBlockedMessage,
            recovery = recovery,
        )
    }

    private data class Action(
        val epoch: Long,
        val trigger: BootstrapRefreshTrigger
    )

    private fun BootstrapFailure.message(): String = when (this) {
        is BootstrapFailure.AuthRequired -> "Sign-in is required. Please authenticate and retry."
        is BootstrapFailure.Unavailable -> "The profile could not be refreshed. Please retry."
        is BootstrapFailure.Protocol -> "The server returned an unexpected response. Please retry."
    }
}
