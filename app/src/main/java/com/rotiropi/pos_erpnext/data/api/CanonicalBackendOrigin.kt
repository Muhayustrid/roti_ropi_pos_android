package com.rotiropi.pos_erpnext.data.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class CanonicalBackendOrigin private constructor(val serialized: String, val isValid: Boolean) {

    companion object {
        fun parse(urlInput: String): CanonicalBackendOrigin {
            if (urlInput.isEmpty()) {
                return CanonicalBackendOrigin("", false)
            }
            if (urlInput.any { it.isWhitespace() || it.isISOControl() }) {
                return CanonicalBackendOrigin("", false)
            }

            val trimmed = urlInput.trimEnd('/')
            if (!trimmed.startsWith("https://")) {
                return CanonicalBackendOrigin("", false)
            }

            val httpUrl = trimmed.toHttpUrlOrNull() ?: return CanonicalBackendOrigin("", false)

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
