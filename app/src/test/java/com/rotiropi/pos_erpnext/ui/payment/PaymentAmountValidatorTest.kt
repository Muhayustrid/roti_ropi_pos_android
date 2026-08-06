package com.rotiropi.pos_erpnext.ui.payment

import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentAmountValidatorTest {
    private val policy = PaymentAmountPolicy("IDR", 2, "0.01", "ascii_decimal_dot", "reject", "payment-amount/v1")

    @Test fun `accepts canonical amounts at allowed scale`() {
        listOf("55000", "55000.00", "0.01").forEach { assertTrue(PaymentAmountValidator.validate(it, policy) is PaymentValidationResult.Valid) }
    }

    @Test fun `rejects invalid syntax scale and zero`() {
        listOf("-1", "abc", "", "55000.000", "0").forEach { assertTrue(PaymentAmountValidator.validate(it, policy) is PaymentValidationResult.Invalid) }
    }

    @Test fun `accepts backend compatible leading zero amounts`() {
        assertTrue(PaymentAmountValidator.validate("001", policy) is PaymentValidationResult.Valid)
    }
}
