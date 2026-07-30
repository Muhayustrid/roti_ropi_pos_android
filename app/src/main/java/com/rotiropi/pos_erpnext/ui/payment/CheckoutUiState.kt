package com.rotiropi.pos_erpnext.ui.payment

sealed interface CheckoutUiState {
    data object Unavailable : CheckoutUiState
    data object OfflineNotSubmitted : CheckoutUiState
    data class PriceChanged(val message: String) : CheckoutUiState
    data object Submitting : CheckoutUiState
    data class Error(val message: String) : CheckoutUiState
}
