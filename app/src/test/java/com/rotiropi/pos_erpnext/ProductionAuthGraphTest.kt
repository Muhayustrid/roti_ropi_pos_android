package com.rotiropi.pos_erpnext

import com.rotiropi.pos_erpnext.data.api.AuthenticatedMobilePosApiClient
import com.rotiropi.pos_erpnext.data.api.ClosingStatus
import com.rotiropi.pos_erpnext.data.api.CoordinatorAuthTokenProvider
import com.rotiropi.pos_erpnext.data.api.FrappeResponse
import com.rotiropi.pos_erpnext.data.api.SubmitClosingResponseDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], application = MobilePosApplication::class)
class ProductionAuthGraphTest {

    @Test
    fun `application exposes one authenticated business client backed by coordinator provider`() {
        val app = RuntimeEnvironment.application as MobilePosApplication

        assertTrue(app.authTokenProvider is CoordinatorAuthTokenProvider)
        assertTrue(app.mobilePosApiClient is AuthenticatedMobilePosApiClient)
        assertSame(app.oauthCoordinator, app.authTokenProvider.coordinator)
        assertSame(app.authTokenProvider, app.mobilePosApiClient.tokenProvider)
        assertSame(app.authenticationOwner.coordinator, app.oauthCoordinator)
    }

    @Test
    fun `application uses stable staging OAuth identity`() {
        val app = RuntimeEnvironment.application as MobilePosApplication

        assertEquals("https://oauth-staging.rotiropi.web.id", MobilePosApplication.CANONICAL_ORIGIN)
        assertEquals(
            "https://oauth-staging.rotiropi.web.id/android/oauth2redirect",
            MobilePosApplication.REDIRECT_URI
        )
        assertEquals("rotiropi.mobilepos.task9.staging", MobilePosApplication.CLIENT_ID)
        assertEquals(MobilePosApplication.CANONICAL_ORIGIN, app.oauthConfig.canonicalOrigin)
        assertEquals(MobilePosApplication.REDIRECT_URI, app.oauthConfig.redirectUri)
    }

    @Test
    fun `application wires one process-wide Closing state machine`() {
        val app = RuntimeEnvironment.application as MobilePosApplication

        assertSame(app.closingViewModel, app.closingViewModel)
    }

    @Test
    fun `Closing evidence decoder accepts queued and submitted envelopes`() {
        assertEquals(
            ClosingStatus.QUEUED,
            MobilePosApplication.decodeClosingReceipt(closingEnvelope("queued"))?.status,
        )
        assertEquals(
            ClosingStatus.SUBMITTED,
            MobilePosApplication.decodeClosingReceipt(closingEnvelope("submitted"))?.status,
        )
    }

    @Test
    fun `Closing evidence decoder rejects malformed and failed envelopes`() {
        assertEquals(null, MobilePosApplication.decodeClosingReceipt("not-json"))
        assertEquals(
            null,
            MobilePosApplication.decodeClosingReceipt(
                """{"message":{"ok":false,"meta":{"api_version":"v1","request_id":"request","server_time":"now"},"error":{"code":"CLOSING_FAILED","message":"failed","details":{},"retryable":false}}}""",
            ),
        )
    }

    private fun closingEnvelope(status: String): String {
        val fixture = Json.decodeFromString<FrappeResponse>(CLOSING_ENVELOPE)
        val closing = requireNotNull(fixture.message.data).let {
            Json.decodeFromJsonElement(SubmitClosingResponseDto.serializer(), it)
        }
        return Json.encodeToString(
            FrappeResponse.serializer(),
            fixture.copy(
                message = fixture.message.copy(
                    data = Json.encodeToJsonElement(
                        SubmitClosingResponseDto.serializer(),
                        closing.copy(closing = closing.closing.copy(status = when (status) {
                            "queued" -> ClosingStatus.QUEUED
                            else -> ClosingStatus.SUBMITTED
                        })),
                    ),
                ),
            ),
        )
    }

    private companion object {
        const val CLOSING_ENVELOPE =
            """{"message":{"ok":true,"data":{"closing":{"name":"CLOSING-1","opening_entry":"OPENING-1","pos_profile":"OUTLET-01","status":"queued","invoice_count":1,"grand_total":"100.00","net_total":"90.00","total_quantity":"1.00","total_taxes_and_charges":"10.00","payments":[{"mode_of_payment":"Cash","opening_amount":"0.00","expected_amount":"100.00","counted_amount":"100.00","difference":"0.00"}],"reconciliation":{"expected_total":"100.00","counted_total":"100.00","difference_total":"0.00"},"failure":null}},"meta":{"api_version":"v1","request_id":"request","server_time":"now"}}}"""
    }
}
