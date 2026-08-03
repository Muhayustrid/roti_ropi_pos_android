package com.rotiropi.pos_erpnext.ui.opening

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningAmountFieldTest {
    @Test
    fun `valid Indonesian grouped paste normalizes to ungrouped raw input`() {
        assertEquals("1000", normalizeOpeningAmountInput("1.000"))
        assertEquals("10000,50", normalizeOpeningAmountInput("10.000,50"))
        assertEquals("1000000,25", normalizeOpeningAmountInput("1.000.000,25"))
    }

    @Test
    fun `malformed grouping paste is rejected without a fallback value`() {
        listOf("1,000.50", "1.00.0,50", "10..000", "10,00,50", "12.34.567").forEach { input ->
            assertNull(normalizeOpeningAmountInput(input))
        }
    }

    @Test
    fun `display grouping preserves every typed decimal digit`() {
        assertEquals("10.000", formatOpeningAmount("10000"))
        assertEquals("10.000,5", formatOpeningAmount("10000,5"))
        assertEquals("10.000,50", formatOpeningAmount("10000,50"))
        assertEquals("10.000,50", formatOpeningAmount("10000.50"))
        assertEquals("10.000,", formatOpeningAmount("10000,"))
    }

    @Test
    fun `selection-aware zero replacement replaces zero with first typed digit`() {
        val current = TextFieldValue("0.00", selection = TextRange(0, 4))
        val proposed = TextFieldValue("1")

        assertEquals("1", normalizeOpeningAmountEdit(current, proposed)!!.text)
    }

    @Test
    fun `zero replacement preserves typed decimal fraction`() {
        val current = TextFieldValue("0.00", selection = TextRange(0, 4))
        val proposed = TextFieldValue("1,5")

        assertEquals("1,5", normalizeOpeningAmountEdit(current, proposed)!!.text)
    }

    @Test
    fun `dot decimal input remains raw dot and renders comma`() {
        assertEquals("10000.50", normalizeOpeningAmountInput("10000.50"))
        assertEquals("10000.", normalizeOpeningAmountInput("10000."))
        assertEquals("10.000,50", formatOpeningAmount("10000.50"))
        assertEquals("10.000,", formatOpeningAmount("10000."))
    }

    @Test
    fun `selection-aware edit preserves trailing decimal and typed fraction`() {
        val current = TextFieldValue("10000,50", selection = TextRange(8))
        val proposed = TextFieldValue("10000,5", selection = TextRange(7))

        assertEquals("10000,5", normalizeOpeningAmountEdit(current, proposed)!!.text)
        assertEquals("10000,", normalizeOpeningAmountEdit(current, TextFieldValue("10000,"))!!.text)
    }

    @Test
    fun `backspace around grouping separator keeps numerical cursor meaning`() {
        val mapping = openingAmountOffsetMapping("10000")

        assertEquals(1, mapping.transformedToOriginal(1))
        assertEquals(2, mapping.transformedToOriginal(2))
        assertEquals(2, mapping.transformedToOriginal(3))
        assertEquals(2, mapping.originalToTransformed(2))
        assertEquals(4, mapping.originalToTransformed(3))

        val current = TextFieldValue("10000", selection = TextRange(5))
        assertEquals("1000", normalizeOpeningAmountEdit(current, TextFieldValue("1000", TextRange(4)))!!.text)
    }

    @Test
    fun `decimal cursor mapping preserves separator boundary`() {
        val mapping = openingAmountOffsetMapping("10000,50")

        assertEquals(5, mapping.originalToTransformed(4))
        assertEquals(6, mapping.originalToTransformed(5))
        assertEquals(4, mapping.transformedToOriginal(5))
        assertEquals(5, mapping.transformedToOriginal(6))
        assertEquals(6, mapping.transformedToOriginal(7))
    }

    @Test
    fun `invalid edit leaves existing raw value unchanged`() {
        val current = TextFieldValue("10000,50", selection = TextRange(8))

        assertNull(normalizeOpeningAmountEdit(current, TextFieldValue("10,000.50")))
    }

    @Test
    fun `large exact values remain strings without precision loss`() {
        val raw = "9".repeat(200)

        assertEquals(raw, normalizeOpeningAmountInput(raw))
        assertEquals("99${".999".repeat(66)}", formatOpeningAmount(raw))
    }

    @Test
    fun `empty edit remains available as intermediate state`() {
        assertEquals("", normalizeOpeningAmountInput(""))
        assertTrue(normalizeOpeningAmountEdit(TextFieldValue("1"), TextFieldValue(""))!!.text.isEmpty())
    }
}
