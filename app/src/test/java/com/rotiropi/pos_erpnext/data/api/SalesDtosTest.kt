package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SalesDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `quote item accepts null row id and ignores additive fields`() {
        val response = json.decodeFromString<QuoteCartResponseDto>("""{"grand_total":"55000.00","payable":"55000.00","currency":"IDR","items":[{"row_id":null,"item_code":"BREAD","item_name":"Bread","qty":"1","uom":"Nos","conversion_factor":"1","rate":"55000.00","amount":"55000.00","batch_no":null,"serial_numbers":[],"future":true}],"taxes":[],"payment_modes":[],"payment_amount_policy":{"currency":"IDR","decimal_places":2,"minimum":"0.01","api_syntax":"ascii_decimal_dot","rounding":"reject","policy_version":"payment-amount/v1"}}""")
        assertNull(response.items.single().row_id)
    }

    @Test fun `submitted sale item retains row id`() {
        val item = json.decodeFromString<SaleItemDto>("""{"row_id":"abc123","item_code":"BREAD","item_name":"Bread","qty":"1","uom":"Nos","conversion_factor":"1","rate":"55000.00","amount":"55000.00","batch_no":null,"serial_numbers":[]}""")
        assertEquals("abc123", item.row_id)
    }
}
