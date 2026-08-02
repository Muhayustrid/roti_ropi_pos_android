package com.rotiropi.pos_erpnext.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectivityStatusTest {
    @Test
    fun onlyExplicitNoNetworkIsKnownOffline() {
        assertEquals(ConnectivityStatus.KnownOffline, ConnectivityStatus.from(hasNetwork = false, validated = false))
        assertEquals(ConnectivityStatus.Unknown, ConnectivityStatus.from(hasNetwork = true, validated = false))
        assertEquals(ConnectivityStatus.Online, ConnectivityStatus.from(hasNetwork = true, validated = true))
    }
}
