package com.rotiropi.pos_erpnext.data.api

import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobilePosApiContractTest {

    @Test
    fun production_source_does_not_hard_code_opening_fixture_values() {
        val sourceRoot = listOf(Paths.get("app/src/main"), Paths.get("src/main"))
            .first { Files.isDirectory(it) }
        val forbidden = listOf("\"Cash\"", "\"200000.00\"")
        val matches = Files.walk(sourceRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .toList()
                .flatMap { path ->
                    Files.readAllLines(path).withIndex()
                        .filter { (_, line) -> forbidden.any(line::contains) }
                        .map { (index, line) -> "${path}:${index + 1}:$line" }
                }
        }

        assertFalse("Production source contains fixture literals: $matches", matches.isNotEmpty())
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun contractTable(): EndpointContractTable {
        val text = javaClass.getResourceAsStream("/api/v1/endpoint-contracts.json")!!.bufferedReader().readText()
        return json.decodeFromString(text)
    }

    @Test
    fun contract_table_matches_closed_production_catalog() {
        val table = contractTable()
        assertEquals("v1", table.api_version)
        assertEquals(16, table.endpoints.size)
        assertEquals(16, table.endpoints.map { it.path }.toSet().size)
        assertEquals(MobilePosEndpoint.entries.map { it.path }.toSet(), table.endpoints.map { it.path }.toSet())

        table.endpoints.forEach { row ->
            val endpoint = MobilePosEndpoint.fromPath(row.path)
            assertNotNull("Unknown endpoint ${row.path}", endpoint)
            endpoint!!
            assertEquals(endpoint.method.name, row.http_method)
            assertEquals(endpoint.requestLocation.name.lowercase(), row.request_location)
            assertEquals(endpoint.requiredRequestFields, row.required_request_fields)
            assertEquals(endpoint.optionalRequestFields, row.optional_request_fields)
            assertEquals(endpoint.requiresIdempotency, row.requires_idempotency)
            assertEquals(endpoint.retryClass.name.lowercase(), row.retry_class)
            assertEquals(endpoint.serializerIdentity, row.serializer_identity)
            assertTrue(row.requires_bearer)
            assertTrue(row.required_response_fields.isNotEmpty())
        }
    }

    @Test
    fun exactly_four_mutations_require_idempotency() {
        val idempotent = MobilePosEndpoint.entries.filter { it.requiresIdempotency }
        assertEquals(
            setOf(
                MobilePosEndpoint.SESSIONS_OPEN,
                MobilePosEndpoint.SALES_SUBMIT,
                MobilePosEndpoint.SALES_CREATE_RETURN,
                MobilePosEndpoint.CLOSING_SUBMIT
            ),
            idempotent.toSet()
        )
    }

    @Test
    fun forbidden_endpoint_shapes_are_absent() {
        MobilePosEndpoint.entries.forEach { endpoint ->
            assertFalse(endpoint.path.contains("health"))
            assertFalse(endpoint.path.contains("return_preview"))
            assertFalse(endpoint.path.startsWith("http"))
            assertTrue(endpoint.path.startsWith("/api/method/roti_ropi_pos.api.v1."))
        }
        assertEquals(null, MobilePosEndpoint.fromPath("/api/resource/Item"))
        assertEquals(null, MobilePosEndpoint.fromPath("https://evil.example/api/method/x"))
    }

    @Test
    fun quote_contract_forbids_serial_fields() {
        val row = contractTable().endpoints.single { it.method_name == "quote_item" }
        assertEquals(setOf("serial_numbers"), row.forbidden_request_fields)
        assertFalse(row.optional_request_fields.contains("serial_no"))
        assertFalse(row.optional_request_fields.contains("serial_numbers"))
    }

    @Test
    fun quote_cart_contract_has_authoritative_payment_fields() {
        val row = contractTable().endpoints.single { it.method_name == "quote_cart" }
        assertEquals(setOf("pos_profile", "items"), row.required_request_fields)
        assertEquals(setOf("customer", "walk_in_customer_name"), row.optional_request_fields)
        assertEquals("QuoteCartResponseDto", row.serializer_identity)
    }
}
