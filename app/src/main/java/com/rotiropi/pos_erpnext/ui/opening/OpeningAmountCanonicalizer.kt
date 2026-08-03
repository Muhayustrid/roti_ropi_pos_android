package com.rotiropi.pos_erpnext.ui.opening

import java.math.BigDecimal

internal data class OpeningAmountInputPolicy(
    val decimalPlaces: Int,
    val minimum: String,
)

internal sealed interface OpeningAmountResult {
    data class Valid(val canonical: String) : OpeningAmountResult
    data class Invalid(val reason: String) : OpeningAmountResult
}

internal fun canonicalizeOpeningAmount(
    input: String,
    policy: OpeningAmountInputPolicy,
): OpeningAmountResult {
    if (!UI_DECIMAL.matches(input)) return OpeningAmountResult.Invalid("Enter a valid amount.")
    val decimalDot = input.replace(',', '.')
    val fractionalDigits = decimalDot.substringAfter('.', "").length
    if (fractionalDigits > policy.decimalPlaces) {
        return OpeningAmountResult.Invalid("Use at most ${policy.decimalPlaces} decimal places.")
    }
    val value = BigDecimal(decimalDot)
    if (value < BigDecimal(policy.minimum)) {
        return OpeningAmountResult.Invalid("Amount must be at least ${policy.minimum}.")
    }
    return OpeningAmountResult.Valid(value.setScale(policy.decimalPlaces).toPlainString())
}

private val UI_DECIMAL = Regex("^[0-9]+(?:[.,][0-9]+)?$")
