package com.rotiropi.pos_erpnext.ui.returning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnInputTest {
    private val policy = ReturnQuantityPolicy(2, "0.01", "999999999999.99", "ascii_decimal_dot", "reject", "return-quantity/v1")

    @Test fun `valid selection retains server decimal text without rounding`() {
        val result = validateReturnInput("Damaged package", "1.25", "2.00", policy)

        assertTrue(result is ReturnInputValidation.Valid)
        assertEquals("1.25", (result as ReturnInputValidation.Valid).quantity)
    }

    @Test fun `reason quantity scale and remaining quantity are validated locally`() {
        assertFalse(validateReturnInput(" ", "1", "2", policy).isValid)
        assertFalse(validateReturnInput("Reason", "1.234", "2", policy).isValid)
        assertFalse(validateReturnInput("Reason", "2.01", "2", policy).isValid)
        assertFalse(validateReturnInput("Reason", "0", "2", policy).isValid)
        assertFalse(validateReturnInput("Reason", "1e1", "20", policy).isValid)
    }
}
