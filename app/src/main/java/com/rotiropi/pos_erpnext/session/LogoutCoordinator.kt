package com.rotiropi.pos_erpnext.session

import com.rotiropi.pos_erpnext.auth.AuthenticationOwner
import com.rotiropi.pos_erpnext.data.MobilePosRepository
import com.rotiropi.pos_erpnext.ui.profile.ProfileSelectionViewModel

/**
 * Coordinates application-wide logout.
 *
 * The public constructor wires the real in-memory repository clear and the real
 * authentication logout. [logout] runs repository clearing before authentication
 * logout, so [com.rotiropi.pos_erpnext.auth.AuthenticationState.Unauthenticated] —
 * the sign-in routing signal — is published only after all stale bootstrap,
 * profile, opening, and capability state has been cleared.
 *
 * The primary constructor is internal and holds two zero-arg operations so tests
 * can assert the exact synchronous order; production callers use the public
 * two-argument constructor.
 */
class LogoutCoordinator internal constructor(
    private val clearRepository: () -> Unit,
    private val clearProfileUi: () -> Unit = {},
    private val clearAuthentication: () -> Unit
) {
    constructor(
        repository: MobilePosRepository,
        profileSelectionViewModel: ProfileSelectionViewModel,
        authenticationOwner: AuthenticationOwner
    ) : this(
        clearRepository = repository::clear,
        clearProfileUi = profileSelectionViewModel::clear,
        clearAuthentication = authenticationOwner::logout
    )

    fun logout() {
        clearRepository()
        clearProfileUi()
        clearAuthentication()
    }
}
