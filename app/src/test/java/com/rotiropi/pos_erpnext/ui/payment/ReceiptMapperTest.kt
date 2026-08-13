package com.rotiropi.pos_erpnext.ui.payment

import android.content.Context
import com.rotiropi.pos_erpnext.data.api.PaymentDto
import com.rotiropi.pos_erpnext.data.api.SaleDetailDto
import com.rotiropi.pos_erpnext.data.api.SaleItemDto
import com.rotiropi.pos_erpnext.data.api.SaleStatus
import com.rotiropi.pos_erpnext.data.api.SaleSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `ReceiptContent.status` is a `@StringRes` id, so the customer-facing guarantee has to be
 * checked on the resolved string. Robolectric supplies the resource-backed `Context` and
 * lets the same mapping be read in both languages.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class ReceiptMapperTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    @Config(qualifiers = "en")
    fun `uses a customer-facing sale status label`() {
        val receipt = ReceiptMapper.map(sale())

        assertEquals("Paid", context.getString(receipt.status))
    }

    /** Indonesian lives in `values/`, so any locale without a translation resolves to it. */
    @Test
    @Config(qualifiers = "fr")
    fun `sale status falls back to indonesian`() {
        val receipt = ReceiptMapper.map(sale())

        assertEquals("Dibayar", context.getString(receipt.status))
    }

    /**
     * Batch and serial prefixes are this app's words and are resolved by the screen, so the
     * mapper must carry only the server-supplied numbers.
     */
    @Test
    fun `item lines keep server values without app-owned prefixes`() {
        val receipt = ReceiptMapper.map(
            sale(
                items = listOf(
                    SaleItemDto("row-1", "BREAD", "Bread", "2", "Nos", "1", "5000", "10000", null, listOf("B-1", "B-2"), listOf("S-1")),
                    SaleItemDto("row-2", "MILK", "Milk", "1", "Nos", "1", "8000", "8000", null, emptyList(), emptyList()),
                ),
            ),
        )

        assertEquals("Bread × 2: 10000", receipt.items[0].summary)
        assertEquals("B-1, B-2", receipt.items[0].batches)
        assertEquals("S-1", receipt.items[0].serials)
        assertEquals("Milk × 1: 8000", receipt.items[1].summary)
        assertNull(receipt.items[1].batches)
        assertNull(receipt.items[1].serials)
    }

    @Test
    fun `every sale status maps to a distinct non-blank label`() {
        val labels = SaleStatus.entries.map { status ->
            context.getString(ReceiptMapper.map(sale(status = status)).status)
        }

        labels.forEach { assertEquals(false, it.isBlank()) }
        assertEquals(SaleStatus.entries.size, labels.toSet().size)
    }

    private fun sale(
        status: SaleStatus = SaleStatus.PAID,
        items: List<SaleItemDto> = emptyList(),
    ) = SaleDetailDto(
        SaleSummaryDto("POS Invoice", "SINV-1", status, "Walk In", null, "IDR", "1", "1", "0", "2026-08-06", "10:00"),
        items,
        emptyList(),
        emptyList<PaymentDto>(),
    )
}
