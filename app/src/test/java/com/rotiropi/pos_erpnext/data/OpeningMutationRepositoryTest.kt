package com.rotiropi.pos_erpnext.data

import com.rotiropi.pos_erpnext.data.api.AuthTokenProvider
import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.data.api.CanonicalBackendOrigin
import com.rotiropi.pos_erpnext.data.api.OpenSessionRequestDto
import com.rotiropi.pos_erpnext.data.api.OpenSessionResponseDto
import com.rotiropi.pos_erpnext.recovery.RecoveryExecution
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class OpeningMutationRepositoryTest {
    @Test
    fun `production opening recovery spec uses sessions open serializers`() {
        val request = OpenSessionRequestDto("PROFILE-EXAMPLE", emptyList())

        val spec = openingRecoverySpec(request, Json)

        assertEquals(com.rotiropi.pos_erpnext.data.api.MobilePosEndpoint.SESSIONS_OPEN, spec.endpoint)
        assertEquals(request, spec.body)
        assertEquals(OpenSessionRequestDto.serializer(), spec.bodySerializer)
        assertEquals(OpenSessionResponseDto.serializer(), spec.responseDeserializer)
    }

    @Test
    fun `open session sends canonical request through durable recovery boundary`() {
        var captured: OpenSessionRequestDto? = null
        val repository = MobilePosRepository(
            AuthenticatedMobilePosApiClient(
                CanonicalBackendOrigin.parse("https://example.test"),
                object : AuthTokenProvider {
                    override fun currentAccessToken() = "token"
                    override fun refreshAccessToken(): String? = null
                    override fun currentTokens(): com.rotiropi.pos_erpnext.auth.OAuthTokens? = null
                },
                OkHttpClient(),
            ),
            openSession = { request ->
                captured = request
                RecoveryExecution.Completed("123e4567-e89b-42d3-a456-426614174000")
            },
        )
        val request = OpenSessionRequestDto("PROFILE-EXAMPLE", emptyList())

        val result = repository.openSession(request)

        assertEquals(request, captured)
        assertEquals(RecoveryExecution.Completed("123e4567-e89b-42d3-a456-426614174000"), result)
    }
}
