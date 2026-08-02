package com.rotiropi.pos_erpnext

import android.app.Application
import com.rotiropi.pos_erpnext.auth.AuthenticationOwner
import com.rotiropi.pos_erpnext.auth.OAuthAttemptStore
import com.rotiropi.pos_erpnext.auth.OAuthConfiguration
import com.rotiropi.pos_erpnext.auth.OAuthCoordinator
import com.rotiropi.pos_erpnext.auth.TokenStore
import com.rotiropi.pos_erpnext.data.MobilePosRepository
import com.rotiropi.pos_erpnext.data.RepositoryResult
import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.session.LogoutCoordinator
import com.rotiropi.pos_erpnext.data.AndroidConnectivityStatusProvider
import com.rotiropi.pos_erpnext.recovery.RecoveryCoordinator
import com.rotiropi.pos_erpnext.recovery.RecoveryIdentity
import com.rotiropi.pos_erpnext.recovery.RecoveryTransport
import com.rotiropi.pos_erpnext.recovery.SqlitePendingMutationStore
import com.rotiropi.pos_erpnext.recovery.RetryPendingMutationWorker
import com.rotiropi.pos_erpnext.recovery.RetryScheduler
import com.rotiropi.pos_erpnext.recovery.ColdRecovery
import kotlinx.serialization.DeserializationStrategy
import com.rotiropi.pos_erpnext.recovery.PendingMutation
import com.rotiropi.pos_erpnext.ui.AppViewModel
import com.rotiropi.pos_erpnext.ui.profile.ProfileSelectionViewModel
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
    lateinit var mobilePosRepository: MobilePosRepository
        private set
    lateinit var logoutCoordinator: LogoutCoordinator
        private set
    private val pendingMutations by lazy { SqlitePendingMutationStore(this) }
    val recoveryCoordinator: RecoveryCoordinator by lazy {
        RecoveryCoordinator(
            store = pendingMutations,
            transport = object : RecoveryTransport {
                override fun <T> execute(request: PendingMutation, deserializer: DeserializationStrategy<T>) =
                    mobilePosApiClient.execute(
                        com.rotiropi.pos_erpnext.data.api.MobilePosRequest.replayPost(
                            request.endpoint,
                            request.body,
                            request.transactionId,
                        ),
                        deserializer,
                    )
            },
            connectivity = AndroidConnectivityStatusProvider(this)::current,
            identity = {
                mobilePosRepository.state.bootstrap?.user?.name?.let {
                    RecoveryIdentity(it, CANONICAL_ORIGIN, CLIENT_ID)
                }
            },
            scheduler = object : RetryScheduler {
                override fun schedule(
                    transactionId: String,
                    nextEligibleAtMillis: Long,
                    completion: (Throwable?) -> Unit,
                ) = RetryPendingMutationWorker.schedule(this@MobilePosApplication, transactionId, nextEligibleAtMillis, completion)
            },
        )
    }
    fun currentRecoveryIdentity(): RecoveryIdentity? =
        mobilePosRepository.state.bootstrap?.user?.name?.let { RecoveryIdentity(it, CANONICAL_ORIGIN, CLIENT_ID) }

    /** Called only after bootstrap publishes authenticated cashier identity. */
    fun recoverPendingMutationsAfterBootstrap(allowReauthenticationResume: Boolean = true) {
        recoveryCoordinator.recoverAtAuthenticatedStartup()
        if (allowReauthenticationResume && authenticationOwner.consumeSuccessfulAuthorization()) {
            recoveryCoordinator.resumeAfterSuccessfulReauthentication()
        }
    }

    /** WorkManager cold-process boundary. Never derives identity from persisted mutation evidence. */
    fun retryPendingMutationAfterColdBootstrap(transactionId: String) = ColdRecovery(
        hasStoredAuth = { authenticationOwner.isAuthenticated },
        currentBootstrapIdentity = {
            mobilePosRepository.state.bootstrap?.user?.name?.let {
                RecoveryIdentity(it, CANONICAL_ORIGIN, CLIENT_ID)
            }
        },
        bootstrap = { mobilePosRepository.bootstrap(null) is RepositoryResult.Success },
        recoverStaleSending = recoveryCoordinator::recoverStaleSending,
        retryAction = recoveryCoordinator::retry,
    ).retry(transactionId)

    lateinit var appViewModel: AppViewModel
        private set
    lateinit var profileSelectionViewModel: ProfileSelectionViewModel
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
        mobilePosRepository = MobilePosRepository(mobilePosApiClient)
        appViewModel = AppViewModel(mobilePosRepository)
        profileSelectionViewModel = ProfileSelectionViewModel(mobilePosRepository)
        logoutCoordinator = LogoutCoordinator(
            mobilePosRepository,
            profileSelectionViewModel,
            authenticationOwner,
            pendingMutations,
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
