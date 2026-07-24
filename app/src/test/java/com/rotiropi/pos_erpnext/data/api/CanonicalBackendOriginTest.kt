package com.rotiropi.pos_erpnext.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalBackendOriginTest {

    @Test
    fun valid_https_origin_without_path_is_accepted() {
        val origin = CanonicalBackendOrigin.parse("https://erpnext.example.com")
        assertTrue(origin.isValid)
        assertEquals("https://erpnext.example.com", origin.serialized)
    }

    @Test
    fun valid_https_origin_with_port_is_accepted() {
        val origin = CanonicalBackendOrigin.parse("https://erpnext.example.com:8443")
        assertTrue(origin.isValid)
        assertEquals("https://erpnext.example.com:8443", origin.serialized)
    }

    @Test
    fun trailing_slash_is_normalized() {
        val origin = CanonicalBackendOrigin.parse("https://erpnext.example.com/")
        assertTrue(origin.isValid)
        assertEquals("https://erpnext.example.com", origin.serialized)
    }

    @Test
    fun http_scheme_is_rejected() {
        val origin = CanonicalBackendOrigin.parse("http://erpnext.example.com")
        assertFalse(origin.isValid)
    }

    @Test
    fun origin_with_path_is_rejected() {
        val origin = CanonicalBackendOrigin.parse("https://erpnext.example.com/api")
        assertFalse(origin.isValid)
    }

    @Test
    fun origin_with_query_or_fragment_is_rejected() {
        assertFalse(CanonicalBackendOrigin.parse("https://erpnext.example.com?query=1").isValid)
        assertFalse(CanonicalBackendOrigin.parse("https://erpnext.example.com#fragment").isValid)
    }

    @Test
    fun origin_with_userinfo_is_rejected() {
        assertFalse(CanonicalBackendOrigin.parse("https://user:pass@erpnext.example.com").isValid)
    }

    @Test
    fun origin_with_whitespace_or_control_chars_is_rejected() {
        assertFalse(CanonicalBackendOrigin.parse("https://erpnext.example.com ").isValid)
        assertFalse(CanonicalBackendOrigin.parse("https://erpnext\n.example.com").isValid)
    }

    @Test
    fun origin_with_trailing_dot_host_is_rejected() {
        assertFalse(CanonicalBackendOrigin.parse("https://erpnext.example.com.").isValid)
    }
}
