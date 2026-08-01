package com.rotiropi.pos_erpnext.auth

data class OAuthAttempt(
    val canonicalOrigin: String,
    val clientId: String,
    val state: String,
    val codeVerifier: String,
    val codeChallenge: String,
    val redirectUri: String,
    val createdAt: Long, // epoch milliseconds
    val expiresAt: Long, // epoch milliseconds
    val status: Status
) {
    enum class Status {
        PENDING,
        CONSUMED
    }
}

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long, // epoch milliseconds
    val canonicalOrigin: String = "",
    val clientId: String = "",
    val recordVersion: Int = TokenStore.RECORD_VERSION
)
