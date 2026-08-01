package com.rotiropi.pos_erpnext

import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.data.api.CoordinatorAuthTokenProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], application = MobilePosApplication::class)
class ProductionAuthGraphTest {

    @Test
    fun `application exposes one authenticated business client backed by coordinator provider`() {
        val app = RuntimeEnvironment.application as MobilePosApplication

        assertTrue(app.authTokenProvider is CoordinatorAuthTokenProvider)
        assertTrue(app.mobilePosApiClient is AuthenticatedMobilePosApiClient)
        assertSame(app.oauthCoordinator, app.authTokenProvider.coordinator)
        assertSame(app.authTokenProvider, app.mobilePosApiClient.tokenProvider)
        assertSame(app.authenticationOwner.coordinator, app.oauthCoordinator)
    }

    @Test
    fun `application uses stable staging OAuth identity`() {
        val app = RuntimeEnvironment.application as MobilePosApplication

        assertEquals("https://oauth-staging.rotiropi.web.id", MobilePosApplication.CANONICAL_ORIGIN)
        assertEquals(
            "https://oauth-staging.rotiropi.web.id/android/oauth2redirect",
            MobilePosApplication.REDIRECT_URI
        )
        assertEquals("rotiropi.mobilepos.task9.staging", MobilePosApplication.CLIENT_ID)
        assertEquals(MobilePosApplication.CANONICAL_ORIGIN, app.oauthConfig.canonicalOrigin)
        assertEquals(MobilePosApplication.REDIRECT_URI, app.oauthConfig.redirectUri)
    }
}
