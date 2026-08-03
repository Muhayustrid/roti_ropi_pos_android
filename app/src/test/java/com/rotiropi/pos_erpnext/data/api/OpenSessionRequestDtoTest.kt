package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenSessionRequestDtoTest {
    @Test
    fun `request serialization preserves server row order and canonical monetary strings`() {
        val request = OpenSessionRequestDto(
            pos_profile = "PROFILE-EXAMPLE",
            opening_balances = listOf(
                OpeningBalanceInputDto("Mode B", "12.30"),
                OpeningBalanceInputDto("Mode A", "0.00"),
            ),
        )

        assertEquals(
            """{"pos_profile":"PROFILE-EXAMPLE","opening_balances":[{"mode_of_payment":"Mode B","amount":"12.30"},{"mode_of_payment":"Mode A","amount":"0.00"}]}""",
            Json.encodeToString(request),
        )
    }
}
