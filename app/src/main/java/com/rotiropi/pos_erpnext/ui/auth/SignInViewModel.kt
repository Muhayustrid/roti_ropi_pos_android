package com.rotiropi.pos_erpnext.ui.auth

import androidx.lifecycle.ViewModel
import com.rotiropi.pos_erpnext.MobilePosApplication
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
 * Maps an authentication failure reason to a cashier-facing message. It never
 * surfaces a token, code, verifier, cookie, secret, or raw OAuth error detail.
 */
fun signInErrorMessage(reason: OAuthCompletionResult.Reason): String = when (reason) {
    OAuthCompletionResult.Reason.AUTHORIZATION_CANCELLED ->
        "Sign-in was cancelled. Try again when you are ready."
    OAuthCompletionResult.Reason.AUTHORIZATION_LAUNCH_FAILED ->
        "Could not open the secure sign-in page. Please try again."
    OAuthCompletionResult.Reason.TOKEN_PERSISTENCE_FAILED ->
        "Sign-in finished but could not be saved. Please try again."
    OAuthCompletionResult.Reason.ATTEMPT_EXPIRED ->
        "Sign-in took too long. Please try again."
    OAuthCompletionResult.Reason.ATTEMPT_CONSUMED ->
        "That sign-in was already completed. Please try again."
    else ->
        "Sign-in could not be completed. Please try again."
}
