package com.rotiropi.pos_erpnext.ui.payment

import com.rotiropi.pos_erpnext.ui.UiText

sealed interface CheckoutUiState {
    data object Unavailable : CheckoutUiState
    data class Ready(val quote: CheckoutQuote, val payments: List<PaymentRow>, val validation: PaymentValidationResult) : CheckoutUiState
    data class PaymentInvalid(val message: UiText, val modeOfPayment: String?, val quote: CheckoutQuote, val payments: List<PaymentRow>) : CheckoutUiState
    data object OfflineNotSubmitted : CheckoutUiState
    data class PriceChanged(val message: UiText, val details: Map<String, String>) : CheckoutUiState
    data object Submitting : CheckoutUiState
    data class Error(val message: UiText) : CheckoutUiState
}
