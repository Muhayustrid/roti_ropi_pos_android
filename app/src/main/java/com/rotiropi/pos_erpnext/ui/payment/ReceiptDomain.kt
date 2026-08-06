package com.rotiropi.pos_erpnext.ui.payment

import com.rotiropi.pos_erpnext.data.api.SaleDetailDto
import com.rotiropi.pos_erpnext.data.api.SaleStatus
import com.rotiropi.pos_erpnext.ui.receipt.ReceiptContent

object ReceiptMapper {
    fun map(sale: SaleDetailDto) = ReceiptContent(
        saleId = sale.summary.name,
        customerLabel = sale.summary.walk_in_customer_name ?: sale.summary.customer,
        total = "${sale.summary.currency} ${sale.summary.grand_total}",
        paid = "${sale.summary.currency} ${sale.summary.paid_amount}",
        changeAmount = "${sale.summary.currency} ${sale.summary.change_amount}",
        status = sale.summary.status.displayLabel(),
        items = sale.items.map { "${it.item_name} × ${it.qty}: ${it.amount}" },
        taxes = sale.taxes.map { "${it.description}: ${it.tax_amount}" },
        payments = sale.payments.map { "${it.mode_of_payment}: ${it.amount}" },
)

private fun SaleStatus.displayLabel(): String = when (this) {
    SaleStatus.PAID -> "Paid"
    SaleStatus.RETURN -> "Return"
    SaleStatus.CONSOLIDATED -> "Consolidated"
    SaleStatus.CANCELLED -> "Cancelled"
    SaleStatus.UNSUPPORTED -> "Unsupported"
}
}
