package com.rotiropi.pos_erpnext.ui.payment

import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.data.api.PaymentAmountPolicyDto
import com.rotiropi.pos_erpnext.data.api.PaymentModeDto
import com.rotiropi.pos_erpnext.data.api.SaleItemDto
import com.rotiropi.pos_erpnext.data.api.SaleTaxDto
import com.rotiropi.pos_erpnext.ui.UiText
import com.rotiropi.pos_erpnext.ui.uiText
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
sealed interface PaymentValidationResult { data object Valid : PaymentValidationResult; data class Invalid(val reason: UiText) : PaymentValidationResult }

object PaymentAmountValidator {
    private val decimal = Regex("^[0-9]+(\\.\\d+)?$")
    fun validate(raw: String, policy: PaymentAmountPolicy): PaymentValidationResult {
        if (!decimal.matches(raw)) return invalid(R.string.amount_error_negative)
        val value = raw.toBigDecimalOrNull() ?: return invalid(R.string.amount_error_invalid)
        if (value.scale() > policy.decimalPlaces) return invalid(R.string.amount_error_decimal_places)
        if (value < policy.minimum.toBigDecimal()) return invalid(R.string.amount_error_below_minimum)
        return PaymentValidationResult.Valid
    }

    private fun invalid(id: Int) = PaymentValidationResult.Invalid(uiText(id))
}

internal fun PaymentModeDto.toDomain() = PaymentMode(mode_of_payment, default, currency)
internal fun PaymentAmountPolicyDto.toDomain() = PaymentAmountPolicy(currency, decimal_places, minimum, api_syntax, rounding, policy_version)
