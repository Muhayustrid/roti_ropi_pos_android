package com.rotiropi.pos_erpnext.ui.opening

import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.ui.UiText
import com.rotiropi.pos_erpnext.ui.uiText
import java.math.BigDecimal

internal data class OpeningAmountInputPolicy(
    val decimalPlaces: Int,
    val minimum: String,
)

internal sealed interface OpeningAmountResult {
    data class Valid(val canonical: String) : OpeningAmountResult
    data class Invalid(val reason: UiText) : OpeningAmountResult
}

internal fun canonicalizeOpeningAmount(
    input: String,
    policy: OpeningAmountInputPolicy,
): OpeningAmountResult {
    if (!UI_DECIMAL.matches(input)) return OpeningAmountResult.Invalid(uiText(R.string.amount_error_invalid))
    val decimalDot = input.replace(',', '.')
    val fractionalDigits = decimalDot.substringAfter('.', "").length
    if (fractionalDigits > policy.decimalPlaces) {
        return OpeningAmountResult.Invalid(
            uiText(R.string.opening_amount_error_decimal_places, policy.decimalPlaces),
        )
    }
    val value = BigDecimal(decimalDot)
    if (value < BigDecimal(policy.minimum)) {
        // The minimum is server-supplied and passes through verbatim.
        return OpeningAmountResult.Invalid(uiText(R.string.opening_amount_error_minimum, policy.minimum))
    }
    return OpeningAmountResult.Valid(value.setScale(policy.decimalPlaces).toPlainString())
}

private val UI_DECIMAL = Regex("^[0-9]+(?:[.,][0-9]+)?$")
