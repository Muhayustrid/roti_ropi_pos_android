package com.rotiropi.pos_erpnext

import android.app.Application
import com.rotiropi.pos_erpnext.auth.AuthenticationOwner
import com.rotiropi.pos_erpnext.auth.OAuthAttemptStore
import com.rotiropi.pos_erpnext.auth.OAuthConfiguration
import com.rotiropi.pos_erpnext.auth.OAuthCoordinator
import com.rotiropi.pos_erpnext.auth.TokenStore
import com.rotiropi.pos_erpnext.data.MobilePosRepository
import com.rotiropi.pos_erpnext.data.RepositoryResult
import com.rotiropi.pos_erpnext.data.openingRecoverySpec
import com.rotiropi.pos_erpnext.data.saleRecoverySpec
import com.rotiropi.pos_erpnext.data.api.FrappeResponse
import com.rotiropi.pos_erpnext.data.api.SubmitSaleResponseDto
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromJsonElement
import com.rotiropi.pos_erpnext.recovery.PendingMutation
import com.rotiropi.pos_erpnext.ui.AppViewModel
import com.rotiropi.pos_erpnext.ui.customer.CustomerSearchIdentity
import com.rotiropi.pos_erpnext.ui.customer.CustomerSearchViewModel
import com.rotiropi.pos_erpnext.ui.cashier.CashierViewModel
import com.rotiropi.pos_erpnext.ui.profile.ProfileSelectionViewModel
import com.rotiropi.pos_erpnext.ui.opening.OpeningRoutingGate
import com.rotiropi.pos_erpnext.data.api.CanonicalBackendOrigin
import com.rotiropi.pos_erpnext.data.api.CoordinatorAuthTokenProvider
import okhttp3.OkHttpClient
import kotlinx.coroutines.Dispatchers

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
    lateinit var customerSearchViewModel: CustomerSearchViewModel
        private set
    lateinit var cashierViewModel: CashierViewModel
        private set

    internal var openingRoutingGateFactory: ((MobilePosRepository) -> OpeningRoutingGate)? = null

    internal fun openingRoutingGate(): OpeningRoutingGate =
        openingRoutingGateFactory?.invoke(mobilePosRepository) ?: OpeningRoutingGate(
            currentSession = mobilePosRepository::currentSession,
            refreshCapabilities = mobilePosRepository::refreshCapabilities,
        )

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
        mobilePosRepository = MobilePosRepository(
            mobilePosApiClient,
            openSession = { request -> recoveryCoordinator.execute(openingRecoverySpec(request, Json)) },
            submitSale = { request -> recoveryCoordinator.execute(saleRecoverySpec(request, Json)) },
        )
        appViewModel = AppViewModel(mobilePosRepository)
        customerSearchViewModel = CustomerSearchViewModel(Dispatchers.IO, search = { request, cancellation ->
            mobilePosRepository.searchCustomers(
                request.query,
                request.posProfile,
                request.start,
                request.limit,
                cancellation,
            )
        })
        cashierViewModel = CashierViewModel(
            dispatcher = Dispatchers.IO,
            searchCatalog = { request, cancellation ->
                mobilePosRepository.searchCatalog(
                    request.query,
                    request.posProfile,
                    request.start,
                    request.limit,
                    cancellation,
                )
            },
            scanCatalog = { request, cancellation ->
                mobilePosRepository.scanCatalog(request.posProfile, request.value, cancellation)
            },
            quoteItem = { request, cancellation ->
                mobilePosRepository.quoteItem(request, cancellation)
            },
            quoteCart = { request, cancellation -> mobilePosRepository.quoteCart(request, cancellation) },
            submitSale = mobilePosRepository::submitSale,
            completedSale = { transactionId ->
                recoveryCoordinator.readTerminalResult(transactionId)?.let { result ->
                    runCatching {
                        Json.decodeFromString(FrappeResponse.serializer(), result.responseText).message.data
                            ?.let { Json.decodeFromJsonElement(SubmitSaleResponseDto.serializer(), it).sale }
                    }.getOrNull()
                }
            },
            rejectedSale = { transactionId ->
                recoveryCoordinator.readTerminalResult(transactionId)?.let { result ->
                    runCatching {
                        Json.decodeFromString(FrappeResponse.serializer(), result.responseText).message.error?.let { error ->
                            com.rotiropi.pos_erpnext.ui.cashier.SaleSubmissionRejection(
                                error.code,
                                error.details.mapValues { it.value.toString() },
                            )
                        }
                    }.getOrNull()
                }
            },
        )
        profileSelectionViewModel = ProfileSelectionViewModel(mobilePosRepository) {
            val bootstrap = mobilePosRepository.state.bootstrap
            val profile = bootstrap?.selectedProfile
            val cashier = bootstrap?.user?.name
            if (profile == null || cashier == null) customerSearchViewModel.clear()
            else customerSearchViewModel.bind(CustomerSearchIdentity(cashier, profile.name, profile.customer))
        }
        logoutCoordinator = LogoutCoordinator(
            repository = mobilePosRepository,
            profileSelectionViewModel = profileSelectionViewModel,
            invalidateCustomerAuthority = customerSearchViewModel::invalidateAuthority,
            cancelCustomerRequest = customerSearchViewModel::cancelActiveRequest,
            clearCustomerUi = customerSearchViewModel::clearUi,
            authenticationOwner = authenticationOwner,
            pendingMutations = pendingMutations,
            clearCashierUi = cashierViewModel::clear,
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
