package com.rotiropi.pos_erpnext.ui.returning

import java.math.BigDecimal

data class ReturnQuantityPolicy(
    val decimalPlaces: Int,
    val minimum: String,
    val maximum: String,
    val apiSyntax: String,
    val rounding: String,
    val policyVersion: String,
)

sealed interface ReturnInputValidation {
    val isValid: Boolean

    data class Valid(val quantity: String) : ReturnInputValidation { override val isValid = true }
    data class Invalid(val reason: String) : ReturnInputValidation { override val isValid = false }
}

fun validateReturnInput(
    reason: String,
    quantity: String,
    remaining: String,
    policy: ReturnQuantityPolicy,
): ReturnInputValidation {
    if (reason.isBlank()) return ReturnInputValidation.Invalid("reason_required")
    if (policy.policyVersion != "return-quantity/v1" || policy.apiSyntax != "ascii_decimal_dot" ||
        policy.rounding != "reject" || policy.decimalPlaces !in 0..9) {
        return ReturnInputValidation.Invalid("unsupported_quantity_policy")
    }
    if (!QUANTITY.matches(quantity)) return ReturnInputValidation.Invalid("malformed_decimal")
    if (quantity.substringAfter('.', "").length > policy.decimalPlaces) return ReturnInputValidation.Invalid("excessive_scale")
    val requested = quantity.toBigDecimalOrNull() ?: return ReturnInputValidation.Invalid("malformed_decimal")
    val available = remaining.toBigDecimalOrNull() ?: return ReturnInputValidation.Invalid("invalid_remaining_quantity")
    if (requested <= BigDecimal.ZERO) return ReturnInputValidation.Invalid("zero_or_negative_quantity")
    if (requested < (policy.minimum.toBigDecimalOrNull() ?: return ReturnInputValidation.Invalid("unsupported_quantity_policy"))) {
        return ReturnInputValidation.Invalid("below_minimum_quantity")
    }
    if (requested > (policy.maximum.toBigDecimalOrNull() ?: return ReturnInputValidation.Invalid("unsupported_quantity_policy"))) {
        return ReturnInputValidation.Invalid("above_maximum_quantity")
    }
    if (requested > available) return ReturnInputValidation.Invalid("return_limit_exceeded")
    return ReturnInputValidation.Valid(quantity)
}

private val QUANTITY = Regex("[0-9]+(?:\\.[0-9]+)?")
