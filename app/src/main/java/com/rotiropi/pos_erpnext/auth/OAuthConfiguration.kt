package com.rotiropi.pos_erpnext.auth

import com.rotiropi.pos_erpnext.data.api.CanonicalBackendOrigin

class OAuthConfiguration private constructor(
    val canonicalOrigin: String,
    val clientId: String,
    val redirectUri: String,
    val attemptLifetimeSeconds: Long
) {
    val authorizePath: String = AUTHORIZE_PATH
    val tokenPath: String = TOKEN_PATH
    val scope: String = SCOPE
    val authorizationEndpoint: String = "$canonicalOrigin$AUTHORIZE_PATH"
    val tokenEndpoint: String = "$canonicalOrigin$TOKEN_PATH"

    companion object {
        const val AUTHORIZE_PATH = "/api/method/frappe.integrations.oauth2.authorize"
        const val TOKEN_PATH = "/api/method/frappe.integrations.oauth2.get_token"
        const val REDIRECT_PATH = "/android/oauth2redirect"
        const val SCOPE = "all"
        const val ATTEMPT_LIFETIME_MINUTES = 10L

        fun create(
            canonicalOrigin: String,
            clientId: String,
            redirectUri: String,
            authorizePath: String,
            tokenPath: String,
            scope: String,
            lifetimeMinutes: Long
        ): OAuthConfiguration {
            val parsedOrigin = CanonicalBackendOrigin.parse(canonicalOrigin)
            require(parsedOrigin.isValid && parsedOrigin.serialized == canonicalOrigin) {
                "OAuth origin must be canonical HTTPS"
            }
            require(clientId.isNotBlank() && clientId == clientId.trim()) {
                "OAuth client ID must be fixed and non-blank"
            }
            require(authorizePath == AUTHORIZE_PATH) { "Unexpected OAuth authorize path" }
            require(tokenPath == TOKEN_PATH) { "Unexpected OAuth token path" }
            require(scope == SCOPE) { "Unexpected OAuth scope" }
            require(redirectUri == "$canonicalOrigin$REDIRECT_PATH") {
                "OAuth redirect URI must match configured origin"
            }
            require(lifetimeMinutes == ATTEMPT_LIFETIME_MINUTES) {
                "Unexpected OAuth attempt lifetime"
            }
            return OAuthConfiguration(
                canonicalOrigin,
                clientId,
                redirectUri,
                lifetimeMinutes * 60L
            )
        }
    }
}
