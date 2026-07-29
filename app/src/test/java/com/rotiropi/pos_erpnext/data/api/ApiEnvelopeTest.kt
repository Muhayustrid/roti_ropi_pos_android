package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiEnvelopeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parses_success_error_and_additive_fields() {
        val success = fixture("envelope-success.json")
        assertTrue(success.message.ok)
        assertEquals("cashier@example.com", success.message.data!!.jsonObject["user"]!!.jsonPrimitive.content)
        assertEquals("req-12345", success.message.meta.request_id)

        val failure = fixture("envelope-error.json")
        assertFalse(failure.message.ok)
        assertEquals("STALE_OPENING", failure.message.error?.code)
        assertFalse(failure.message.error!!.retryable)
        assertNotNull(failure.message.error.details)

        assertTrue(fixture("additive-fields.json").message.ok)
    }

    @Test
    fun exact_major_version_is_required() {
        val envelope = fixture("incompatible-api-version.json").message
        assertFalse(envelope.isCompatibleVersion("v1"))
        assertFalse(ApiEnvelope(true, json.parseToJsonElement("{}"), ApiMeta("v10", "r", "t")).isCompatibleVersion("v1"))
    }

    @Test
    fun missing_outer_message_is_rejected() {
        val malformed = javaClass.getResourceAsStream("/api/v1/malformed-response.json")!!.bufferedReader().readText()
        assertTrue(runCatching { json.decodeFromString<FrappeResponse>(malformed) }.exceptionOrNull() is SerializationException)
    }

    @Test
    fun retry_after_accepts_delta_and_http_date_only() {
        assertEquals("30", RetryAfter.parse("30")?.raw)
        assertEquals("Wed, 21 Oct 2030 07:28:00 GMT", RetryAfter.parse("Wed, 21 Oct 2030 07:28:00 GMT")?.raw)
        assertEquals(null, RetryAfter.parse("tomorrow"))
        assertEquals(null, RetryAfter.parse("-1"))
    }

    private fun fixture(name: String): FrappeResponse {
        val text = javaClass.getResourceAsStream("/api/v1/$name")!!.bufferedReader().readText()
        return json.decodeFromString(text)
    }
}
