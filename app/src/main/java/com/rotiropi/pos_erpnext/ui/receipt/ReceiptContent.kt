package com.rotiropi.pos_erpnext.ui.receipt

import androidx.annotation.StringRes

/**
 * One printed receipt line. The parts are kept separate rather than pre-joined because
 * [summary] is server-owned text while the batch and serial prefixes are this app's
 * words, so only the latter resolve from resources at the UI edge.
 */
data class ReceiptItemLine(
    val summary: String,
    val batches: String? = null,
    val serials: String? = null,
)

data class ReceiptContent(
    val saleId: String,
    val sourceReference: String? = null,
    val customerLabel: String,
    val total: String,
    val paid: String,
    val changeAmount: String,
    /**
     * A resource id rather than text: the sale status is a closed enum, so no server
     * string can reach the screen through it, and the label follows the selected
     * interface language instead of the language in force when the sale was mapped.
     */
    @StringRes val status: Int,
    val items: List<ReceiptItemLine> = emptyList(),
    val taxes: List<String> = emptyList(),
    val payments: List<String> = emptyList(),
)
