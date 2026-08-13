package com.rotiropi.pos_erpnext.ui.payment

import androidx.annotation.StringRes
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.data.api.SaleDetailDto
import com.rotiropi.pos_erpnext.data.api.SaleStatus
import com.rotiropi.pos_erpnext.ui.receipt.ReceiptContent
import com.rotiropi.pos_erpnext.ui.receipt.ReceiptItemLine

object ReceiptMapper {
    fun map(sale: SaleDetailDto) = ReceiptContent(
        saleId = sale.summary.name,
        sourceReference = sale.return_against,
        customerLabel = sale.summary.walk_in_customer_name ?: sale.summary.customer,
        total = "${sale.summary.currency} ${sale.summary.grand_total}",
        paid = "${sale.summary.currency} ${sale.refund_amount ?: sale.summary.paid_amount}",
        changeAmount = "${sale.summary.currency} ${sale.summary.change_amount}",
        status = sale.summary.status.labelRes(),
        items = sale.items.map { item ->
            ReceiptItemLine(
                summary = "${item.item_name} × ${item.qty}: ${item.amount}",
                batches = item.batch_numbers
                    .ifEmpty { listOfNotNull(item.batch_no) }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(),
                serials = item.serial_numbers.takeIf { it.isNotEmpty() }?.joinToString(),
            )
        },
        taxes = sale.taxes.map { "${it.description}: ${it.tax_amount}" },
        payments = (sale.refund_allocations.ifEmpty { sale.payments }).map { "${it.mode_of_payment}: ${it.amount}" },
    )

    /**
     * `SaleStatus` is a closed enum with an explicit `UNSUPPORTED` member, so mapping to a
     * resource id cannot carry a server-supplied string to the screen.
     */
    @StringRes
    private fun SaleStatus.labelRes(): Int = when (this) {
        SaleStatus.PAID -> R.string.sale_status_paid
        SaleStatus.RETURN -> R.string.sale_status_return
        SaleStatus.CONSOLIDATED -> R.string.sale_status_consolidated
        SaleStatus.CANCELLED -> R.string.sale_status_cancelled
        SaleStatus.UNSUPPORTED -> R.string.sale_status_unsupported
    }
}
