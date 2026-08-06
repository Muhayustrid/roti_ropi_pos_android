package com.rotiropi.pos_erpnext.ui.payment

import com.rotiropi.pos_erpnext.data.api.PaymentAmountPolicyDto
import com.rotiropi.pos_erpnext.data.api.PaymentModeDto
import com.rotiropi.pos_erpnext.data.api.SaleItemDto
import com.rotiropi.pos_erpnext.data.api.SaleTaxDto
import java.math.BigDecimal

data class CheckoutQuote(
    val grandTotal: String,
    val payable: String,
    val currency: String,
    val paymentModes: List<PaymentMode>,
    val paymentAmountPolicy: PaymentAmountPolicy,
    val items: List<SaleItemDto>,
    val taxes: List<SaleTaxDto>,
    val quoteGeneration: Long,
)

data class PaymentMode(val modeOfPayment: String, val isDefault: Boolean, val currency: String)
data class PaymentAmountPolicy(val currency: String, val decimalPlaces: Int, val minimum: String, val apiSyntax: String, val rounding: String, val policyVersion: String)
data class PaymentRow(val modeOfPayment: String, val amount: String, val referenceNo: String? = null, val isDefault: Boolean = false)
sealed interface PaymentValidationResult { data object Valid : PaymentValidationResult; data class Invalid(val reason: String) : PaymentValidationResult }

object PaymentAmountValidator {
    private val decimal = Regex("^[0-9]+(\\.\\d+)?$")
    fun validate(raw: String, policy: PaymentAmountPolicy): PaymentValidationResult {
        if (!decimal.matches(raw)) return PaymentValidationResult.Invalid("Enter a non-negative decimal amount.")
        val value = raw.toBigDecimalOrNull() ?: return PaymentValidationResult.Invalid("Enter a valid amount.")
        if (value.scale() > policy.decimalPlaces) return PaymentValidationResult.Invalid("Amount has too many decimal places.")
        if (value < policy.minimum.toBigDecimal()) return PaymentValidationResult.Invalid("Amount is below the minimum.")
        return PaymentValidationResult.Valid
    }
}

internal fun PaymentModeDto.toDomain() = PaymentMode(mode_of_payment, default, currency)
internal fun PaymentAmountPolicyDto.toDomain() = PaymentAmountPolicy(currency, decimal_places, minimum, api_syntax, rounding, policy_version)
