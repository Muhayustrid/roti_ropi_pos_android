package com.rotiropi.pos_erpnext.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.rotiropi.pos_erpnext.MobilePosApplication
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.auth.AuthenticationOwner
import com.rotiropi.pos_erpnext.auth.AuthenticationState
import com.rotiropi.pos_erpnext.auth.OAuthCompletionResult
import kotlinx.coroutines.flow.StateFlow

/** Thin UI adapter over application-scoped authentication owner. */
class SignInViewModel(
    private val authenticationOwner: AuthenticationOwner =
        MobilePosApplication.instance.authenticationOwner
) : ViewModel() {
    val uiState: StateFlow<AuthenticationState> = authenticationOwner.state

    fun onSignInClick() {
        authenticationOwner.beginAuthorization()
    }

    fun logout() {
        authenticationOwner.logout()
    }
}

/**
 * Maps an authentication failure reason to a cashier-facing message resource. It never
 * surfaces a token, code, verifier, cookie, secret, or raw OAuth error detail. The
 * resource is resolved by the caller at the UI edge so the message follows the
 * selected interface language.
 */
@StringRes
fun signInErrorMessage(reason: OAuthCompletionResult.Reason): Int = when (reason) {
    OAuthCompletionResult.Reason.AUTHORIZATION_CANCELLED -> R.string.sign_in_error_cancelled
    OAuthCompletionResult.Reason.AUTHORIZATION_LAUNCH_FAILED -> R.string.sign_in_error_browser
    OAuthCompletionResult.Reason.TOKEN_PERSISTENCE_FAILED -> R.string.sign_in_error_not_saved
    OAuthCompletionResult.Reason.ATTEMPT_EXPIRED -> R.string.sign_in_error_timeout
    OAuthCompletionResult.Reason.ATTEMPT_CONSUMED -> R.string.sign_in_error_already_used
    else -> R.string.sign_in_error_generic
}
