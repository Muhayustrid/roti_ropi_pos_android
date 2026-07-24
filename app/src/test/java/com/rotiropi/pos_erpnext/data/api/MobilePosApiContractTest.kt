package com.rotiropi.pos_erpnext.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobilePosApiContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun contract_table_has_exactly_14_unique_business_endpoints() {
        val jsonText = javaClass.getResourceAsStream("/api/v1/endpoint-contracts.json")!!.bufferedReader().readText()
        val contractTable = json.decodeFromString<EndpointContractTable>(jsonText)

        assertEquals("v1", contractTable.api_version)
        assertEquals(14, contractTable.endpoints.size)

        val uniquePaths = contractTable.endpoints.map { it.path }.toSet()
        assertEquals(14, uniquePaths.size)
    }

    @Test
    fun contract_table_excludes_health_and_return_preview() {
        val jsonText = javaClass.getResourceAsStream("/api/v1/endpoint-contracts.json")!!.bufferedReader().readText()
        val contractTable = json.decodeFromString<EndpointContractTable>(jsonText)

        val paths = contractTable.endpoints.map { it.path }
        assertFalse(paths.any { it.contains("health") })
        assertFalse(paths.any { it.contains("return_preview") })
    }

    @Test
    fun contract_table_requires_idempotency_on_exactly_4_mutations() {
        val jsonText = javaClass.getResourceAsStream("/api/v1/endpoint-contracts.json")!!.bufferedReader().readText()
        val contractTable = json.decodeFromString<EndpointContractTable>(jsonText)

        val idempotentEndpoints = contractTable.endpoints.filter { it.requires_idempotency }
        assertEquals(4, idempotentEndpoints.size)

        val idempotentNames = idempotentEndpoints.map { "${it.module}.${it.method_name}" }.toSet()
        assertTrue(idempotentNames.contains("sessions.open"))
        assertTrue(idempotentNames.contains("sales.submit"))
        assertTrue(idempotentNames.contains("sales.create_return"))
        assertTrue(idempotentNames.contains("closing.submit"))
    }
}
