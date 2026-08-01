package com.rotiropi.pos_erpnext.data.api

import com.rotiropi.pos_erpnext.auth.OAuthTokens

/**
 * Narrow seam the authenticated client uses to read and refresh credentials.
 * Backed in production by `OAuthCoordinator` + `TokenStore`; tests substitute a fake.
 */
interface AuthTokenProvider {

    /** Current access token, or null when no usable token is stored. */
    fun currentAccessToken(): String?

    /**
     * Perform one refresh-token grant at the fixed token endpoint.
     * Returns the new access token, or null when refresh is impossible/failed.
     */
    fun refreshAccessToken(): String?

    /** Refresh only if caller's observed token still owns the decision. */
    fun refreshAccessTokenIfCurrent(observedAccessToken: String): String? =
        refreshAccessToken()

    /** Full token record, used for expiry inspection. */
    fun currentTokens(): OAuthTokens?
}
