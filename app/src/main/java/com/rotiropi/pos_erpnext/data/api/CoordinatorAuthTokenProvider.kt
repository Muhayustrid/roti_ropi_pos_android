package com.rotiropi.pos_erpnext.data.api

import com.rotiropi.pos_erpnext.auth.OAuthCoordinator
import com.rotiropi.pos_erpnext.auth.OAuthTokens

/**
 * Production [AuthTokenProvider] backed by [OAuthCoordinator], which owns the
 * encrypted token record and performs the refresh grant at the fixed token endpoint.
 */
class CoordinatorAuthTokenProvider(
    internal val coordinator: OAuthCoordinator
) : AuthTokenProvider {

    override fun currentAccessToken(): String? = coordinator.getAccessToken()

    override fun refreshAccessToken(): String? =
        if (coordinator.refresh()) coordinator.currentTokens()?.accessToken else null

    override fun refreshAccessTokenIfCurrent(observedAccessToken: String): String? =
        coordinator.refreshAccessTokenIfCurrent(observedAccessToken)

    override fun currentTokens(): OAuthTokens? = coordinator.currentTokens()
}
