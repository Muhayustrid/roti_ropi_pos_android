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
    fun catalog_feature_fixtures_parse_through_reviewed_dtos() {
        assertEquals("CROISSANT-PACK", decodeFixture<CatalogSearchResponseDto>("catalog-page.json").items.single().item_code)
        assertEquals("BATCH-QR-0001", decodeFixture<CatalogScanResponseDto>("catalog-scan.json").scan.batch_no)
        assertEquals("MISSING_UOM_CONVERSION", decodeFixture<QuoteItemResponseDto>("catalog-quote.json").warnings.single().code)
    }

    @Test
    fun scan_with_null_conversion_factor_is_supported() {
        val scan = json.decodeFromString<CatalogScanDto>(
            """{"item_code":"SCALE","barcode":"SER-1","batch_no":null,"serial_no":"SER-1","uom":"Nos","conversion_factor":null,"warehouse":"Outlet 01 - RR"}""",
        )
        assertEquals(null, scan.conversion_factor)
    }

    @Test
    fun quote_request_without_uom_is_not_representable() {
        // uom is non-nullable in QuoteItemRequestDto, so a serialized request must always carry it
        val request = QuoteItemRequestDto(
            pos_profile = "OUTLET-01",
            item_code = "SCALE",
            qty = "1",
            uom = "Nos",
        )
        assertEquals("Nos", request.uom)
        assertTrue(json.encodeToString(QuoteItemRequestDto.serializer(), request).contains("\"uom\":\"Nos\""))
    }

    @Test
    fun fixture_manifest_covers_every_payload_fixture() {
        val manifest = resource("fixture-manifest.json").let(json::parseToJsonElement).jsonObject
        assertEquals("2b0ee79e5644d4b67b607c9627b4b2ba75260856", manifest.getValue("backend_sha").jsonPrimitive.content)
        assertEquals("docs/mobile-pos/api-contract.md", manifest.getValue("backend_contract_path").jsonPrimitive.content)
        assertEquals("contract_example", manifest.getValue("source_type").jsonPrimitive.content)
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
            "dto-contract-examples.json",
            "bootstrap-one-profile.json", "bootstrap-multiple-profiles.json",
            "bootstrap-stale-opening.json", "session-current.json", "session-opened.json", "customer-page.json",
            "catalog-page.json", "catalog-scan.json", "catalog-quote.json"
        )
        assertEquals(expected, declared)
    }

    @Test
    fun bootstrap_one_profile_parses_opening_contract_projection() {
        val profile = decodeFixture<BootstrapResponseDto>("bootstrap-one-profile.json").profiles.single()
        assertEquals(listOf("Cash", "Bank"), profile.opening_payment_modes.map { it.mode_of_payment })
        assertEquals("200000.00", profile.opening_payment_modes[0].suggested_opening_amount)
        assertTrue(profile.opening_payment_modes[0].amount_editable)
        assertEquals("IDR", profile.opening_amount_policy?.currency)
        assertEquals(2, profile.opening_amount_policy?.decimal_places)
        assertEquals("0.00", profile.opening_amount_policy?.minimum)
        assertEquals("ascii_decimal_dot", profile.opening_amount_policy?.api_syntax)
        assertEquals("reject", profile.opening_amount_policy?.rounding)
        assertEquals("opening-amount/v1", profile.opening_amount_policy?.policy_version)
    }

    @Test
    fun profile_with_absent_opening_contract_fields_remains_compatible() {
        val profile = json.decodeFromString<ProfileDto>("""
            {
              "name": "OUTLET-LEGACY",
              "company": "Legacy Company",
              "warehouse": "Legacy Warehouse",
              "currency": "IDR",
              "selling_price_list": "Legacy Price List",
              "customer": "Walk In Customer",
              "allow_partial_payment": false,
              "invoice_mode": "POS Invoice"
            }
        """.trimIndent())

        assertTrue(profile.opening_payment_modes.isEmpty())
        assertEquals(null, profile.opening_amount_policy)
    }

    @Test
    fun profile_with_empty_opening_payment_modes_is_parseable() {
        val profile = json.decodeFromString<ProfileDto>("""
            {
              "name": "OUTLET-EMPTY",
              "company": "Example Company",
              "warehouse": "Example Warehouse",
              "currency": "IDR",
              "selling_price_list": "Example Price List",
              "customer": "Walk In Customer",
              "allow_partial_payment": false,
              "invoice_mode": "POS Invoice",
              "opening_payment_modes": [],
              "opening_amount_policy": null
            }
        """.trimIndent())

        assertTrue(profile.opening_payment_modes.isEmpty())
        assertEquals(null, profile.opening_amount_policy)
    }

    @Test
    fun opening_contract_ignores_unknown_additive_fields() {
        val profile = json.decodeFromString<ProfileDto>("""
            {
              "name": "OUTLET-UNKNOWN",
              "company": "Example Company",
              "warehouse": "Example Warehouse",
              "currency": "IDR",
              "selling_price_list": "Example Price List",
              "customer": "Walk In Customer",
              "allow_partial_payment": false,
              "invoice_mode": "POS Invoice",
              "opening_payment_modes": [],
              "opening_amount_policy": null,
              "future_opening_field": "ignored"
            }
        """.trimIndent())

        assertTrue(profile.opening_payment_modes.isEmpty())
    }

    @Test
    fun bootstrap_one_profile_selects_single_profile() {
        val bootstrap = decodeFixture<BootstrapResponseDto>("bootstrap-one-profile.json")
        assertEquals(1, bootstrap.profiles.size)
        assertEquals("OUTLET-01", bootstrap.selected_profile?.name)
        assertEquals(null, bootstrap.opening_session)
        assertTrue(bootstrap.capabilities.open_session)
        assertFalse(bootstrap.capabilities.submit_sale)
        assertFalse(bootstrap.capabilities.create_return)
        assertFalse(bootstrap.capabilities.cancel_sale)
        assertFalse(bootstrap.capabilities.close_session)
    }

    @Test
    fun bootstrap_multiple_profiles_leave_selection_pending() {
        val bootstrap = decodeFixture<BootstrapResponseDto>("bootstrap-multiple-profiles.json")
        assertEquals(2, bootstrap.profiles.size)
        assertEquals(null, bootstrap.selected_profile)
        assertFalse(bootstrap.capabilities.open_session)
        assertFalse(bootstrap.capabilities.submit_sale)
        assertFalse(bootstrap.capabilities.create_return)
        assertFalse(bootstrap.capabilities.cancel_sale)
        assertFalse(bootstrap.capabilities.close_session)
    }

    @Test
    fun bootstrap_stale_opening_reports_stale_warning_code() {
        val bootstrap = decodeFixture<BootstrapResponseDto>("bootstrap-stale-opening.json")
        assertEquals(OpeningStatus.OPEN, bootstrap.opening_session?.status)
        assertEquals("STALE_OPENING", bootstrap.opening_session?.warnings?.single()?.code)
        assertTrue(bootstrap.capabilities.submit_sale)
    }

    private inline fun <reified T> decode(key: String): T =
        json.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), examples.getValue(key))

    private inline fun <reified T> decodeFixture(file: String): T =
        json.decodeFromString(kotlinx.serialization.serializer<T>(), resource(file))

    private fun resource(name: String) =
        javaClass.getResourceAsStream("/api/v1/$name")!!.bufferedReader().readText()
}
