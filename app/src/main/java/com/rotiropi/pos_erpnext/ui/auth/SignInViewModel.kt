package com.rotiropi.pos_erpnext.ui.auth

import androidx.lifecycle.ViewModel
import com.rotiropi.pos_erpnext.MobilePosApplication
import com.rotiropi.pos_erpnext.auth.AuthenticationOwner
import com.rotiropi.pos_erpnext.auth.AuthenticationState
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
