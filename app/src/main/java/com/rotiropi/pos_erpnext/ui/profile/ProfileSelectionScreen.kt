package com.rotiropi.pos_erpnext.ui.profile

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.rotiropi.pos_erpnext.R
import com.rotiropi.pos_erpnext.data.PosProfile
import com.rotiropi.pos_erpnext.recovery.RecoveryScreenState
import com.rotiropi.pos_erpnext.ui.resolve
import com.rotiropi.pos_erpnext.databinding.ProfileSelectionScreenBinding

class ProfileSelectionScreen @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    onProfileSelected: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onLogout: () -> Unit = {},
    onAcknowledgeRecovery: (String) -> Unit = {},
) : FrameLayout(context, attrs, defStyleAttr) {
    private val binding = ProfileSelectionScreenBinding.inflate(LayoutInflater.from(context), this, true)
    private var profileSelected = onProfileSelected
    private var retry = onRetry
    private var logout = onLogout
    private var acknowledgeRecovery = onAcknowledgeRecovery
    private var reauthenticateRecovery: () -> Unit = {}
    private var recovery: RecoveryScreenState = RecoveryScreenState.Hidden
    private var profiles: List<PosProfile> = emptyList()
    private var selectedProfileName: String? = null
    private var page = 0
    private var actionsEnabled = true

    init {
        binding.profileSelectionRetry.setOnClickListener { retry() }
        binding.profileSelectionLogout.setOnClickListener { logout() }
        binding.profileSelectionAcknowledgeRecovery.setOnClickListener {
            (recovery as? RecoveryScreenState.Terminal)?.transactionId?.let(acknowledgeRecovery)
        }
        binding.profileSelectionReauthenticateRecovery.setOnClickListener { reauthenticateRecovery() }
        binding.profileSelectionPrevious.setOnClickListener {
            if (page > 0) {
                page--
                renderPage()
            }
        }
        binding.profileSelectionNext.setOnClickListener {
            if (page < lastPage()) {
                page++
                renderPage()
            }
        }
        binding.profileSelectionError.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    fun setOnProfileSelected(listener: (String) -> Unit) {
        profileSelected = listener
    }

    fun setOnRetry(listener: () -> Unit) {
        retry = listener
    }

    fun setOnLogout(listener: () -> Unit) {
        logout = listener
    }

    fun setOnAcknowledgeRecovery(listener: (String) -> Unit) {
        acknowledgeRecovery = listener
    }

    fun setOnReauthenticateRecovery(listener: () -> Unit) {
        reauthenticateRecovery = listener
    }

    fun render(state: ProfileSelectionUiState) {
        if (profiles.map { it.name } != state.profiles.map { it.name }) page = 0
        profiles = state.profiles
        selectedProfileName = state.selectedProfileName
        recovery = state.recovery
        actionsEnabled = !state.refreshing
        page = page.coerceAtMost(lastPage())
        binding.profileSelectionLoading.isVisible = state.refreshing
        binding.profileSelectionError.isVisible = !state.refreshing && state.error != null
        binding.profileSelectionRetry.isVisible = !state.refreshing && state.retryRequired
        binding.profileSelectionError.text = state.error?.resolve(context).orEmpty()
        val recoveryMessage = when (val item = state.recovery) {
            RecoveryScreenState.Hidden -> null
            is RecoveryScreenState.AuthenticationRequired ->
                context.getString(R.string.recovery_sign_in_again_detail)
            is RecoveryScreenState.RetrySchedulingFailed ->
                context.getString(R.string.recovery_retry_unscheduled)
            is RecoveryScreenState.ManualRecovery -> item.message
            is RecoveryScreenState.Terminal -> when (val result = item.result) {
                is com.rotiropi.pos_erpnext.recovery.RecoveryTerminalResult.Completed ->
                    context.getString(
                        R.string.profile_recovery_completed,
                        result.operation,
                        result.reference,
                        result.status,
                    )
                // Code and message are server-owned and pass through verbatim.
                is com.rotiropi.pos_erpnext.recovery.RecoveryTerminalResult.Rejected ->
                    "${result.code}: ${result.message}"
            }
        }
        binding.profileSelectionRecovery.isVisible = state.logoutBlockedMessage != null || recoveryMessage != null
        binding.profileSelectionRecovery.text =
            state.logoutBlockedMessage?.resolve(context) ?: recoveryMessage.orEmpty()
        binding.profileSelectionAcknowledgeRecovery.isVisible = state.recovery is RecoveryScreenState.Terminal
        binding.profileSelectionReauthenticateRecovery.isVisible = state.recovery is RecoveryScreenState.AuthenticationRequired
        val showProfiles = !state.refreshing && state.error == null && !state.retryRequired
        binding.profileSelectionRows.isVisible = showProfiles
        binding.profileSelectionPaging.isVisible = showProfiles && profiles.size > PAGE_SIZE
        binding.profileSelectionPage.isVisible = binding.profileSelectionPaging.isVisible
        renderPage()
    }

    private fun renderPage() {
        val rows = binding.profileSelectionRows
        val pageProfiles = profiles.drop(page * PAGE_SIZE).take(PAGE_SIZE)
        while (rows.childCount > pageProfiles.size) rows.removeViewAt(rows.childCount - 1)
        pageProfiles.forEachIndexed { index, profile ->
            val row = (rows.getChildAt(index) as? Button) ?: Button(context).also {
                it.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                it.minHeight = dp(48)
                rows.addView(it)
            }
            row.text = profile.name
            row.contentDescription = profile.name
            row.isSelected = profile.name == selectedProfileName
            row.isEnabled = actionsEnabled
            row.setOnClickListener { profileSelected(profile.name) }
        }
        val pages = lastPage() + 1
        binding.profileSelectionPage.text = resources.getString(
            R.string.profile_selection_page,
            page + 1,
            pages,
        )
        binding.profileSelectionPrevious.isEnabled = actionsEnabled && page > 0
        binding.profileSelectionNext.isEnabled = actionsEnabled && page < lastPage()
    }

    private fun lastPage(): Int = ((profiles.size - 1).coerceAtLeast(0)) / PAGE_SIZE

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PAGE_SIZE = 5
    }
}
