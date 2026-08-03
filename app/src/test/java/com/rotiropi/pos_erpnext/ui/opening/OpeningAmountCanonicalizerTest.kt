package com.rotiropi.pos_erpnext.ui.opening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningAmountCanonicalizerTest {
    private val policy = OpeningAmountInputPolicy(decimalPlaces = 2, minimum = "0.00")

    @Test
    fun `dot and comma input canonicalize to exact decimal-dot strings`() {
        assertEquals(OpeningAmountResult.Valid("12.30"), canonicalizeOpeningAmount("12.3", policy))
        assertEquals(OpeningAmountResult.Valid("12.30"), canonicalizeOpeningAmount("12,3", policy))
        assertEquals(OpeningAmountResult.Valid("12.00"), canonicalizeOpeningAmount("12", policy))
    }

    @Test
    fun `fractional digits beyond server scale are rejected without rounding`() {
        assertTrue(canonicalizeOpeningAmount("1.001", policy) is OpeningAmountResult.Invalid)
        assertTrue(canonicalizeOpeningAmount("1,001", policy) is OpeningAmountResult.Invalid)
    }

    @Test
    fun `negative exponent grouping whitespace mixed and malformed input are rejected`() {
        listOf(
            "-0.01",
            "+1.00",
            "1e2",
            "1E2",
            "1,000.00",
            "1.000,00",
            " 1.00",
            "1.00 ",
            "1.",
            "1,",
            ".1",
            ",1",
            "",
        ).forEach { input ->
            assertTrue("Expected invalid input: $input", canonicalizeOpeningAmount(input, policy) is OpeningAmountResult.Invalid)
        }
    }

    @Test
    fun `server minimum is enforced exactly`() {
        val minimum = OpeningAmountInputPolicy(decimalPlaces = 2, minimum = "10.25")

        assertTrue(canonicalizeOpeningAmount("10.24", minimum) is OpeningAmountResult.Invalid)
        assertEquals(OpeningAmountResult.Valid("10.25"), canonicalizeOpeningAmount("10.25", minimum))
    }

    @Test
    fun `no Android maximum or total precision limit is invented`() {
        val large = "9".repeat(200)

        assertEquals(OpeningAmountResult.Valid("$large.00"), canonicalizeOpeningAmount(large, policy))
    }
}
