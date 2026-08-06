package com.rotiropi.pos_erpnext.ui.payment

import com.rotiropi.pos_erpnext.data.api.PaymentDto
import com.rotiropi.pos_erpnext.data.api.SaleDetailDto
import com.rotiropi.pos_erpnext.data.api.SaleStatus
import com.rotiropi.pos_erpnext.data.api.SaleSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptMapperTest {
    @Test
    fun `uses a customer-facing sale status label`() {
        val receipt = ReceiptMapper.map(
            SaleDetailDto(
                SaleSummaryDto("POS Invoice", "SINV-1", SaleStatus.PAID, "Walk In", null, "IDR", "1", "1", "0", "2026-08-06", "10:00"),
                emptyList(), emptyList(), emptyList<PaymentDto>(),
            ),
        )

        assertEquals("Paid", receipt.status)
    }
}
