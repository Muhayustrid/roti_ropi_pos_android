package com.rotiropi.pos_erpnext.ui.receipt

data class ReceiptContent(
    val saleId: String,
    val customerLabel: String,
    val total: String,
    val paid: String,
    val changeAmount: String,
    val status: String,
    val demoData: Boolean,
)
