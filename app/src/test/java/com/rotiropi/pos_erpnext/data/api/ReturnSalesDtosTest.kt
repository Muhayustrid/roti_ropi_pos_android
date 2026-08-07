package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnSalesDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `return projection and quote ignore additive fields`() {
        val sale = json.decodeFromString<SaleDetailDto>("""{"summary":{"doctype":"POS Invoice","name":"INV-1","status":"paid","customer":"Walk In","walk_in_customer_name":"Ari","currency":"IDR","grand_total":"100","paid_amount":"100","change_amount":"0","posting_date":"2026-08-07","posting_time":"10:00:00"},"items":[{"row_id":"row-1","item_code":"BREAD","item_name":"Bread","qty":"2","uom":"Nos","conversion_factor":"1","rate":"50","amount":"100","batch_no":null,"serial_numbers":[],"returnability":{"original_row_id":"row-1","item_code":"BREAD","original_qty":"2","returned_qty":"1","remaining_qty":"1","uom":"Nos","batch_numbers":[],"serial_numbers":[],"eligible":true,"rejection_reason":null}}],"taxes":[],"payments":[],"return_contract":{"quantity_policy":{"decimal_places":2,"minimum":"0.01","maximum":"999999999999.99","api_syntax":"ascii_decimal_dot","rounding":"reject","policy_version":"return-quantity/v1"},"allowed_refund_modes":[{"mode_of_payment":"Cash"}],"refund_mode_required":false},"future":true}""")
        val quote = json.decodeFromString<QuoteReturnResponseDto>("""{"return_quote":{"source_name":"INV-1","items":[],"grand_total":"-50","refund_amount":"50","refund_allocations":[{"mode_of_payment":"Cash","amount":"-50","reference_no":null}],"selected_refund_mode":"Cash","future":true}}""")

        assertEquals("1", sale.items.single().returnability!!.remaining_qty)
        assertFalse(sale.return_contract!!.refund_mode_required)
        assertEquals("50", quote.return_quote.refund_amount)
    }
}
