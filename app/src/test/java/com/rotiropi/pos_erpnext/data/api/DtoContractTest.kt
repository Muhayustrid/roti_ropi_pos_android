package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DtoContractTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val examples = resource("dto-contract-examples.json").let(json::parseToJsonElement).jsonObject

    @Test
    fun parses_contract_snapshot_dtos_and_preserves_decimal_strings() {
        assertEquals("cashier@example.com", decode<BootstrapResponseDto>("bootstrap").user.name)
        assertEquals(null, decode<SessionCurrentResponseDto>("session_current").opening_session)
        assertEquals(20, decode<CustomerSearchResponseDto>("customers").page.limit)
        assertEquals("15000.0000", decode<CatalogSearchResponseDto>("catalog").items.single().price_list_rate)
        assertEquals("6.000", decode<CatalogScanResponseDto>("scan").scan.conversion_factor)
        assertEquals("2.00", decode<QuoteItemResponseDto>("quote").item.qty)
        assertEquals(SaleStatus.PAID, decode<SubmitSaleResponseDto>("sale").sale.summary.status)
        assertEquals(0, decode<SaleListResponseDto>("sale_list").sales.size)
        assertEquals(SaleStatus.RETURN, decode<CreateReturnResponseDto>("return_sale").return_sale.summary.status)
        assertEquals(ClosingStatus.QUEUED, decode<SubmitClosingResponseDto>("closing").closing.status)
    }

    @Test
    fun unknown_status_maps_to_unsupported() {
        val summary = json.decodeFromJsonElement(SaleSummaryDto.serializer(), examples.getValue("unknown_sale_status"))
        assertEquals(SaleStatus.UNSUPPORTED, summary.status)
    }

    @Test
    fun missing_required_fields_and_wrong_types_are_rejected() {
        assertTrue(runCatching { json.decodeFromString<BootstrapResponseDto>("{}") }.isFailure)
        assertTrue(
            runCatching { json.decodeFromString<PageDto>("""{"start":{},"limit":20,"has_more":false}""") }
                .isFailure
        )
    }

    @Test
    fun additive_fields_are_ignored() {
        val text = examples.getValue("customers").jsonObject.toMutableMap().also {
            it["future"] = json.parseToJsonElement("true")
        }
        assertEquals(1, json.decodeFromJsonElement(CustomerSearchResponseDto.serializer(), JsonObject(text)).customers.size)
    }

    @Test
    fun fixture_manifest_covers_every_payload_fixture() {
        val manifest = resource("fixture-manifest.json").let(json::parseToJsonElement).jsonObject
        assertEquals("b2a09d2", manifest.getValue("backend_sha").jsonPrimitive.content)
        assertFalse(manifest.getValue("contains_credentials").jsonPrimitive.boolean)
        assertFalse(manifest.getValue("contains_production_pii").jsonPrimitive.boolean)
        assertFalse(manifest.getValue("runtime_integration_evidence").jsonPrimitive.boolean)

        val declared = manifest.getValue("fixtures").jsonArray
            .map { it.jsonObject.getValue("path").jsonPrimitive.content }
            .toSet()
        val expected = setOf(
            "endpoint-contracts.json", "envelope-success.json", "envelope-error.json",
            "native-401.json", "native-403.json", "native-404.json", "native-429.json",
            "native-500.json", "native-503.json", "malformed-response.json",
            "incompatible-api-version.json", "additive-fields.json", "unknown-enum.json",
            "dto-contract-examples.json"
        )
        assertEquals(expected, declared)
    }

    private inline fun <reified T> decode(key: String): T =
        json.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), examples.getValue(key))

    private fun resource(name: String) =
        javaClass.getResourceAsStream("/api/v1/$name")!!.bufferedReader().readText()
}
