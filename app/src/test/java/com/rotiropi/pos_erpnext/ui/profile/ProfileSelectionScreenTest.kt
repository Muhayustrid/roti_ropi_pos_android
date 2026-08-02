package com.rotiropi.pos_erpnext.ui.profile

import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.robolectric.RuntimeEnvironment
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.data.PosCapabilities
import com.rotiropi.pos_erpnext.data.PosProfile
import com.rotiropi.pos_erpnext.recovery.PendingMutationState
import com.rotiropi.pos_erpnext.recovery.RecoveryIdentity
import com.rotiropi.pos_erpnext.recovery.RecoveryScreenState
import com.rotiropi.pos_erpnext.recovery.RecoveryTerminalResult
import com.rotiropi.pos_erpnext.recovery.TerminalReadToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class ProfileSelectionScreenTest {
    private val context = ContextThemeWrapper(
        RuntimeEnvironment.getApplication(),
        R.style.Theme_POSERPNext,
    )
    private val profiles = listOf(profile("counter"), profile("terrace"))

    @Test
    fun loading_and_error_retry_states_are_mutually_exclusive() {
        val screen = ProfileSelectionScreen(context)

        screen.render(state(refreshing = true))
        assertTrue(screen.findViewById<android.view.View>(R.id.profile_selection_loading).visibility == android.view.View.VISIBLE)
        assertEquals(android.view.View.GONE, screen.findViewById<android.view.View>(R.id.profile_selection_error).visibility)
        assertEquals(android.view.View.GONE, screen.findViewById<android.view.View>(R.id.profile_selection_retry).visibility)

        screen.render(state(error = "Could not load profiles", retryRequired = true))
        assertEquals(android.view.View.GONE, screen.findViewById<android.view.View>(R.id.profile_selection_loading).visibility)
        assertEquals(android.view.View.VISIBLE, screen.findViewById<TextView>(R.id.profile_selection_error).visibility)
        assertEquals(android.view.View.VISIBLE, screen.findViewById<Button>(R.id.profile_selection_retry).visibility)
        assertEquals(android.view.View.GONE, screen.findViewById<android.view.View>(R.id.profile_selection_rows).visibility)
    }

    @Test
    fun bounded_profile_buttons_select_and_mark_selected_profile() {
        var selected: String? = null
        val screen = ProfileSelectionScreen(context, onProfileSelected = { selected = it })

        screen.render(state())
        val rows = screen.findViewById<LinearLayout>(R.id.profile_selection_rows)
        assertEquals(2, rows.childCount)
        assertEquals("counter", rows.getChildAt(0).contentDescription)

        rows.getChildAt(1).performClick()
        assertEquals("terrace", selected)

        screen.render(state(selectedProfileName = "terrace"))
        assertTrue(rows.getChildAt(1).isSelected)
        assertFalse(rows.getChildAt(0).isSelected)
    }

    @Test
    fun logout_callback_and_retry_callback_fire_once_per_click() {
        var retries = 0
        var logouts = 0
        val screen = ProfileSelectionScreen(
            context,
            onRetry = { retries++ },
            onLogout = { logouts++ },
        )

        screen.render(state(error = "retry", retryRequired = true))
        screen.findViewById<Button>(R.id.profile_selection_retry).performClick()
        screen.findViewById<Button>(R.id.profile_selection_logout).performClick()

        assertEquals(1, retries)
        assertEquals(1, logouts)
    }

    @Test
    fun blockedLogoutShowsRecoveryDirectionAndAcknowledgesExactTerminal() {
        var acknowledged: String? = null
        val terminal = RecoveryScreenState.Terminal(
            identity = RecoveryIdentity("cashier-1", "https://example.test", "client"),
            generation = 1,
            transactionId = "123e4567-e89b-42d3-a456-426614174000",
            result = RecoveryTerminalResult.Rejected("INVALID", "Review action", "REQ-1"),
            token = TerminalReadToken("123e4567-e89b-42d3-a456-426614174000", 1),
        )
        val screen = ProfileSelectionScreen(context, onAcknowledgeRecovery = { acknowledged = it })

        screen.render(state(logoutBlockedMessage = "Sign out blocked: cashier-1 has rejected recovery.", recovery = terminal))

        assertEquals(android.view.View.VISIBLE, screen.findViewById<TextView>(R.id.profile_selection_recovery).visibility)
        assertTrue(screen.findViewById<TextView>(R.id.profile_selection_recovery).text.contains("cashier-1"))
        screen.findViewById<Button>(R.id.profile_selection_acknowledge_recovery).performClick()
        assertEquals(terminal.transactionId, acknowledged)
    }

    @Test
    fun all_profiles_are_reachable_through_bounded_pages() {
        var selected: String? = null
        val screen = ProfileSelectionScreen(context, onProfileSelected = { selected = it })
        screen.render(state(profiles = (1..21).map { profile("profile-$it") }))
        val rows = screen.findViewById<LinearLayout>(R.id.profile_selection_rows)
        val next = screen.findViewById<Button>(R.id.profile_selection_next)
        val previous = screen.findViewById<Button>(R.id.profile_selection_previous)

        assertEquals(5, rows.childCount)
        assertFalse(previous.isEnabled)
        repeat(4) { next.performClick() }

        assertEquals(1, rows.childCount)
        assertEquals("profile-21", rows.getChildAt(0).contentDescription)
        assertFalse(next.isEnabled)
        rows.getChildAt(0).performClick()
        assertEquals("profile-21", selected)

        previous.performClick()
        assertEquals(5, rows.childCount)
        assertEquals("profile-16", rows.getChildAt(0).contentDescription)
        assertTrue(next.isEnabled)
    }

    @Test
    fun refreshing_disables_profile_and_paging_actions() {
        val screen = ProfileSelectionScreen(context)
        screen.render(state(profiles = (1..8).map { profile("profile-$it") }))
        screen.findViewById<Button>(R.id.profile_selection_next).performClick()

        screen.render(state(refreshing = true, profiles = (1..8).map { profile("profile-$it") }))

        assertFalse(screen.findViewById<Button>(R.id.profile_selection_next).isEnabled)
        assertFalse(screen.findViewById<Button>(R.id.profile_selection_previous).isEnabled)
    }

    @Test
    fun listener_setters_update_existing_view() {
        var selected = "old"
        var retries = 0
        var logouts = 0
        val screen = ProfileSelectionScreen(context, onProfileSelected = { selected = "old" })
        screen.render(state(error = "retry", retryRequired = true))
        screen.setOnProfileSelected { selected = it }
        screen.setOnRetry { retries++ }
        screen.setOnLogout { logouts++ }

        screen.findViewById<LinearLayout>(R.id.profile_selection_rows).getChildAt(0).performClick()
        screen.findViewById<Button>(R.id.profile_selection_retry).performClick()
        screen.findViewById<Button>(R.id.profile_selection_logout).performClick()

        assertEquals("counter", selected)
        assertEquals(1, retries)
        assertEquals(1, logouts)
    }

    @Test
    fun repeated_render_reuses_bounded_rows_without_duplicate_listeners() {
        var selections = 0
        val screen = ProfileSelectionScreen(context, onProfileSelected = { selections++ })

        repeat(3) { screen.render(state()) }
        val rows = screen.findViewById<LinearLayout>(R.id.profile_selection_rows)
        assertEquals(2, rows.childCount)
        rows.getChildAt(0).performClick()
        assertEquals(1, selections)
    }

    @Test
    fun actions_meet_touch_target_and_profile_labels_are_accessible() {
        val screen = ProfileSelectionScreen(context)
        screen.render(state())

        assertTrue(screen.findViewById<Button>(R.id.profile_selection_logout).minimumHeight >= dp(48))
        screen.findViewById<LinearLayout>(R.id.profile_selection_rows).childrenSequence().forEach {
            assertTrue(it.minimumHeight >= dp(48))
            assertTrue(it.contentDescription.isNullOrBlank().not())
        }
    }

    private fun state(
        refreshing: Boolean = false,
        error: String? = null,
        retryRequired: Boolean = false,
        selectedProfileName: String? = null,
        profiles: List<PosProfile> = this.profiles,
        logoutBlockedMessage: String? = null,
        recovery: RecoveryScreenState = RecoveryScreenState.Hidden,
    ) = ProfileSelectionUiState(
        profiles = profiles,
        selectedProfileName = selectedProfileName,
        selectionRequired = true,
        refreshing = refreshing,
        error = error,
        retryRequired = retryRequired,
        anyActionEnabled = PosCapabilities.DISABLED.any,
        logoutBlockedMessage = logoutBlockedMessage,
        recovery = recovery,
    )

    private fun profile(name: String) = PosProfile(
        name = name,
        company = "Company",
        warehouse = "Warehouse",
        currency = "USD",
        sellingPriceList = "Standard",
        customer = "Customer",
        allowPartialPayment = true,
        invoiceMode = "POS",
    )

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}

private fun android.view.ViewGroup.childrenSequence(): Sequence<android.view.View> =
    (0 until childCount).asSequence().map(::getChildAt)
