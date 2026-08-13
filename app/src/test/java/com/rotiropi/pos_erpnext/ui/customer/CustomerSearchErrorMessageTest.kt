package com.rotiropi.pos_erpnext.ui.customer

import android.content.Context
import com.rotiropi.pos_erpnext.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * `CustomerSearchError.toUiMessage()` returns a `@StringRes` id, so the guarantees these tests
 * check — non-blank, no class name, no endpoint, no server code — have to be verified against
 * the resolved string. Resolving through Robolectric also proves the ids exist in `values/`.
 *
 * Split out of `CustomerSearchViewModelTest` because that class is a plain JVM test and these
 * cases need a resource-backed `Context`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class CustomerSearchErrorMessageTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun message(error: CustomerSearchError): String = context.getString(error.toUiMessage())

    @Test
    fun authentication_required_is_user_facing_without_technical_details() {
        val message = message(CustomerSearchError.AuthenticationRequired)
        assertFalse("must not be empty", message.isBlank())
        assertFalse("must not contain class name", message.contains("AuthenticationRequired"))
        assertFalse("must not contain endpoint", message.contains("http"))
        assertFalse("must not contain stack trace marker", message.contains("at com."))
        assertFalse("must not contain token", message.contains("token", ignoreCase = true))
    }

    @Test
    fun authorization_denied_is_user_facing_without_technical_details() {
        val message = message(CustomerSearchError.AuthorizationDenied)
        assertFalse("must not be empty", message.isBlank())
        assertFalse("must not contain class name", message.contains("AuthorizationDenied"))
        assertFalse("must not contain endpoint", message.contains("http"))
    }

    @Test
    fun unavailable_is_user_facing_without_technical_details() {
        val message = message(CustomerSearchError.Unavailable)
        assertFalse("must not be empty", message.isBlank())
        assertFalse("must not contain class name", message.contains("Unavailable"))
        assertFalse("must not contain endpoint", message.contains("http"))
    }

    @Test
    fun stable_does_not_leak_the_server_error_code() {
        val message = message(CustomerSearchError.Stable(code = "ERR_INTERNAL_SERVER_ERROR"))
        assertFalse("must not be empty", message.isBlank())
        assertFalse("must not contain raw server code", message.contains("ERR_INTERNAL_SERVER_ERROR"))
        assertFalse("must not contain class name", message.contains("Stable("))
    }

    @Test
    fun protocol_is_user_facing_without_technical_details() {
        val message = message(CustomerSearchError.Protocol)
        assertFalse("must not be empty", message.isBlank())
        assertFalse("must not contain class name", message.contains("Protocol"))
        assertFalse("must not contain endpoint", message.contains("http"))
    }

    @Test
    fun every_error_maps_to_a_non_blank_message_and_auth_errors_are_not_the_generic_one() {
        val messages = listOf(
            message(CustomerSearchError.AuthenticationRequired),
            message(CustomerSearchError.AuthorizationDenied),
            message(CustomerSearchError.Unavailable),
            message(CustomerSearchError.Stable(code = "CODE")),
            message(CustomerSearchError.Protocol),
        )
        messages.forEach { assertFalse("message must not be blank", it.isBlank()) }

        val generic = message(CustomerSearchError.Stable(code = "X"))
        assertFalse(
            "auth errors must be distinct from generic error message",
            message(CustomerSearchError.AuthenticationRequired) == generic &&
                message(CustomerSearchError.AuthorizationDenied) == generic,
        )
    }

    /**
     * The mapper must not carry a server-supplied `code` as a format argument, or the
     * "no raw server code" guarantee above would depend on the code's contents.
     */
    @Test
    fun stable_and_protocol_share_the_same_generic_resource() {
        assertEquals(
            CustomerSearchError.Stable(code = "A").toUiMessage(),
            CustomerSearchError.Stable(code = "B").toUiMessage(),
        )
        assertEquals(
            R.string.customer_error_failed,
            CustomerSearchError.Protocol.toUiMessage(),
        )
    }
}
