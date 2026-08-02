package com.rotiropi.pos_erpnext.auth

import com.rotiropi.pos_erpnext.MobilePosApplication
import com.rotiropi.pos_erpnext.data.PosCapabilities
import com.rotiropi.pos_erpnext.data.PosProfile
import com.rotiropi.pos_erpnext.data.RepositoryState
import com.rotiropi.pos_erpnext.ui.AppRoute
import com.rotiropi.pos_erpnext.ui.profile.ProfileSelectionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@Config(sdk = [23], application = MobilePosApplication::class)
@RunWith(RobolectricTestRunner::class)
class ProductionAuthGraphTest {
    @Test
    fun applicationBuildsSharedGraphWithoutStartingBootstrap() {
        val application = RuntimeEnvironment.application as MobilePosApplication

        assertNotNull(application.mobilePosRepository)
        assertNotNull(application.logoutCoordinator)
        assertNotNull(application.appViewModel)
        assertNotNull(application.profileSelectionViewModel)
        assertEquals(RepositoryState(), application.mobilePosRepository.state)
        assertEquals(PosCapabilities.DISABLED, application.mobilePosRepository.state.capabilities)
        assertEquals(AppRoute.SIGN_IN, application.appViewModel.uiState.route)

        application.appViewModel.onAuthenticationStateChanged(AuthenticationState.Unauthenticated)

        assertEquals(AppRoute.SIGN_IN, application.appViewModel.uiState.route)
        assertEquals(RepositoryState(), application.mobilePosRepository.state)
        assertEquals(PosCapabilities.DISABLED, application.mobilePosRepository.state.capabilities)

        val profileState = application.profileSelectionViewModel.uiState
        assertEquals(emptyList<PosProfile>(), profileState.profiles)
        assertEquals(null, profileState.selectedProfileName)
        assertFalse(profileState.selectionRequired)
        assertFalse(profileState.anyActionEnabled)
        assertEquals(RepositoryState(), application.mobilePosRepository.state)

        application.profileSelectionViewModel.selectProfile("missing")
        assertEquals(RepositoryState(), application.mobilePosRepository.state)
        assertEquals(PosCapabilities.DISABLED, application.mobilePosRepository.state.capabilities)

        val standaloneProfileViewModel = ProfileSelectionViewModel(application.mobilePosRepository)
        assertEquals(profileState, standaloneProfileViewModel.uiState)
    }
}
