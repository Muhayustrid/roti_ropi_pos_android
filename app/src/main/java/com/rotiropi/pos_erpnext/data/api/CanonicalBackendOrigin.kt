package com.rotiropi.pos_erpnext.data.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class CanonicalBackendOrigin private constructor(val serialized: String, val isValid: Boolean) {

    companion object {
        private val MALFORMED_PERCENT_ESCAPE = Regex("%(?![0-9A-Fa-f]{2})")

        fun parse(urlInput: String): CanonicalBackendOrigin {
            if (urlInput.isEmpty() || urlInput != urlInput.trim()) {
                return CanonicalBackendOrigin("", false)
            }
            if (urlInput.any { it.isWhitespace() || it.isISOControl() } || '\\' in urlInput) {
                return CanonicalBackendOrigin("", false)
            }
            if (MALFORMED_PERCENT_ESCAPE.containsMatchIn(urlInput)) {
                return CanonicalBackendOrigin("", false)
            }

            val schemeSeparator = urlInput.indexOf("://")
            if (schemeSeparator <= 0 || !urlInput.substring(0, schemeSeparator).equals("https", ignoreCase = true)) {
                return CanonicalBackendOrigin("", false)
            }
            val normalizedScheme = "https${urlInput.substring(schemeSeparator)}"
            val authorityAndPath = normalizedScheme.substring(schemeSeparator + 3)
            val slashIndex = authorityAndPath.indexOf('/')
            val rawAuthority = if (slashIndex == -1) authorityAndPath else authorityAndPath.substring(0, slashIndex)
            val rawHost = rawAuthority.substringAfterLast('@').substringBefore(':')
            if (rawHost.endsWith('.')) {
                return CanonicalBackendOrigin("", false)
            }
            val rawPath = if (slashIndex == -1) "" else authorityAndPath.substring(slashIndex)
            if (rawPath.isNotEmpty() && rawPath != "/") {
                return CanonicalBackendOrigin("", false)
            }
            val httpUrl = normalizedScheme.toHttpUrlOrNull() ?: return CanonicalBackendOrigin("", false)

            if (httpUrl.scheme != "https") return CanonicalBackendOrigin("", false)
            if (httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) return CanonicalBackendOrigin("", false)

            val pathSegments = httpUrl.pathSegments
            if (pathSegments.size > 1 || (pathSegments.size == 1 && pathSegments[0].isNotEmpty())) {
                return CanonicalBackendOrigin("", false)
            }
            if (httpUrl.query != null || httpUrl.fragment != null) {
                return CanonicalBackendOrigin("", false)
            }

            val host = httpUrl.host
            if (host.endsWith(".")) return CanonicalBackendOrigin("", false)

            val portSuffix = if (httpUrl.port != 443) ":${httpUrl.port}" else ""
            val normalized = "https://$host$portSuffix"
            return CanonicalBackendOrigin(normalized, true)
        }
    }
}
