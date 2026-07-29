package com.rotiropi.pos_erpnext.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalBackendOriginTest {

    @Test
    fun accepted_origins_are_canonicalized() {
        val cases = mapOf(
            "https://pos.example.com" to "https://pos.example.com",
            "HTTPS://POS.EXAMPLE.COM/" to "https://pos.example.com",
            "https://pos.example.com:443/" to "https://pos.example.com",
            "https://pos.example.com:8443/" to "https://pos.example.com:8443"
        )

        cases.forEach { (input, expected) ->
            val origin = CanonicalBackendOrigin.parse(input)
            assertTrue("Expected valid origin: $input", origin.isValid)
            assertEquals(expected, origin.serialized)
        }
    }

    @Test
    fun rejected_origins_are_invalid() {
        val inputs = listOf(
            "",
            "http://pos.example.com",
            "https://pos.example.com/api",
            "https://user@pos.example.com",
            "https://user:pass@pos.example.com",
            "https://pos.example.com?site=x",
            "https://pos.example.com/#callback",
            " https://pos.example.com",
            "https://pos.example.com ",
            "https://pos.example.com.",
            "https://pos.example.com:99999",
            "https://",
            "https://pos.example.com//",
            "https:\\pos.example.com",
            "https://pos.example.com/%zz",
            "https://pos.example.com\n"
        )

        inputs.forEach { input ->
            assertFalse("Expected invalid origin: $input", CanonicalBackendOrigin.parse(input).isValid)
        }
    }

    @Test
    fun all_ascii_control_characters_are_rejected() {
        val controls = (0..31).map(Int::toChar) + 127.toChar()

        controls.forEach { control ->
            val input = "https://pos.example.com${control}x"
            assertFalse(
                "Expected control U+${control.code.toString(16).padStart(4, '0')} to be rejected",
                CanonicalBackendOrigin.parse(input).isValid
            )
        }
    }
}
