package com.rotiropi.pos_erpnext.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class ConnectivityStatus {
    Online,
    KnownOffline,
    Unknown;

    companion object {
        fun from(hasNetwork: Boolean, validated: Boolean): ConnectivityStatus = when {
            !hasNetwork -> KnownOffline
            validated -> Online
            else -> Unknown
        }
    }
}

fun interface ConnectivityStatusProvider {
    fun current(): ConnectivityStatus
}

class AndroidConnectivityStatusProvider(context: Context) : ConnectivityStatusProvider {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override fun current(): ConnectivityStatus {
        val network = manager?.activeNetwork ?: return ConnectivityStatus.KnownOffline
        val capabilities = manager.getNetworkCapabilities(network) ?: return ConnectivityStatus.Unknown
        return ConnectivityStatus.from(
            hasNetwork = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }
}
