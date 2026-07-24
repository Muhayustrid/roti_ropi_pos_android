package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiEnvelopeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parses_successful_envelope() {
        val jsonText = javaClass.getResourceAsStream("/api/v1/envelope-success.json")!!.bufferedReader().readText()
        val response = json.decodeFromString<FrappeResponse<SampleData>>(jsonText)
        val envelope = response.message
        assertNotNull(envelope)
        assertTrue(envelope!!.ok)
        assertEquals("cashier@example.com", envelope.data?.user)
        assertEquals("v1", envelope.meta?.api_version)
        assertEquals("req-12345", envelope.meta?.request_id)
    }

    @Test
    fun parses_expected_error_envelope() {
        val jsonText = javaClass.getResourceAsStream("/api/v1/envelope-error.json")!!.bufferedReader().readText()
        val response = json.decodeFromString<FrappeResponse<SampleData>>(jsonText)
        val envelope = response.message
        assertNotNull(envelope)
        assertFalse(envelope!!.ok)
        assertEquals("STALE_OPENING", envelope.error?.code)
        assertEquals("Prior opening session detected", envelope.error?.message)
    }

    @Test
    fun ignores_additive_fields() {
        val jsonText = javaClass.getResourceAsStream("/api/v1/additive-fields.json")!!.bufferedReader().readText()
        val response = json.decodeFromString<FrappeResponse<SampleData>>(jsonText)
        val envelope = response.message
        assertNotNull(envelope)
        assertTrue(envelope!!.ok)
        assertEquals("cashier@example.com", envelope.data?.user)
    }

    @Test
    fun detects_incompatible_api_version() {
        val jsonText = javaClass.getResourceAsStream("/api/v1/incompatible-api-version.json")!!.bufferedReader().readText()
        val response = json.decodeFromString<FrappeResponse<SampleData>>(jsonText)
        val envelope = response.message
        assertNotNull(envelope)
        assertEquals("v2", envelope!!.meta?.api_version)
        assertFalse("API version v2 is incompatible with v1 client", envelope.isCompatibleVersion("v1"))
    }
}
