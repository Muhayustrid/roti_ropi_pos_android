package com.rotiropi.pos_erpnext

import android.app.Application
import com.rotiropi.pos_erpnext.auth.AuthenticationOwner
import com.rotiropi.pos_erpnext.auth.OAuthAttemptStore
import com.rotiropi.pos_erpnext.auth.OAuthConfiguration
import com.rotiropi.pos_erpnext.auth.OAuthCoordinator
import com.rotiropi.pos_erpnext.auth.TokenStore
import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.data.api.CanonicalBackendOrigin
import com.rotiropi.pos_erpnext.data.api.CoordinatorAuthTokenProvider
import okhttp3.OkHttpClient

/** Manual application container for one process-wide authentication owner. */
class MobilePosApplication : Application() {
    lateinit var oauthConfig: OAuthConfiguration
        private set
    lateinit var oauthCoordinator: OAuthCoordinator
        private set
    lateinit var authenticationOwner: AuthenticationOwner
        private set
    lateinit var authTokenProvider: CoordinatorAuthTokenProvider
        private set
    lateinit var mobilePosApiClient: AuthenticatedMobilePosApiClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        oauthConfig = OAuthConfiguration.create(
            canonicalOrigin = CANONICAL_ORIGIN,
            clientId = CLIENT_ID,
            redirectUri = REDIRECT_URI,
            authorizePath = OAuthConfiguration.AUTHORIZE_PATH,
            tokenPath = OAuthConfiguration.TOKEN_PATH,
            scope = OAuthConfiguration.SCOPE,
            lifetimeMinutes = OAuthConfiguration.ATTEMPT_LIFETIME_MINUTES
        )
        oauthCoordinator = OAuthCoordinator(
            this,
            oauthConfig,
            OAuthAttemptStore(this),
            TokenStore(this)
        )
        authenticationOwner = AuthenticationOwner(oauthCoordinator)
        authTokenProvider = CoordinatorAuthTokenProvider(oauthCoordinator)
        mobilePosApiClient = AuthenticatedMobilePosApiClient(
            CanonicalBackendOrigin.parse(CANONICAL_ORIGIN),
            authTokenProvider,
            OkHttpClient()
        )
    }

    companion object {
        lateinit var instance: MobilePosApplication
            private set

        const val CANONICAL_ORIGIN = "https://oauth-staging.rotiropi.web.id"
        const val CLIENT_ID = "rotiropi.mobilepos.task9.staging"
        const val REDIRECT_URI = "$CANONICAL_ORIGIN/android/oauth2redirect"
    }
}
