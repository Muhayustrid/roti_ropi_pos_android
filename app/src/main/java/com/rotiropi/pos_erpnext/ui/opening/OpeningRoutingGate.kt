package com.rotiropi.pos_erpnext.ui.opening

import com.rotiropi.pos_erpnext.data.BootstrapRefreshTrigger
import com.rotiropi.pos_erpnext.data.CurrentSessionResult
import com.rotiropi.pos_erpnext.data.OpeningStatus
import com.rotiropi.pos_erpnext.data.RepositoryResult

data class OpeningRoutingAuthority(
    val cashier: String,
    val posProfile: String,
    val authenticationGeneration: Long,
    val repositoryGeneration: Long,
)

enum class OpeningRoutingDestination { OPENING, CASHIER }

data class OpeningRoutingResult(
    val destination: OpeningRoutingDestination,
    val failure: com.rotiropi.pos_erpnext.data.BootstrapFailure? = null,
)

class OpeningRoutingGate(
    private val currentSession: (String) -> CurrentSessionResult,
    private val refreshCapabilities: (BootstrapRefreshTrigger) -> RepositoryResult,
) {
    fun afterAuthentication(authority: OpeningRoutingAuthority): OpeningRoutingResult =
        reconcile(authority, refresh = false)

    fun afterOpeningSucceeded(authority: OpeningRoutingAuthority): OpeningRoutingResult =
        reconcile(authority, refresh = true)

    private fun reconcile(
        authority: OpeningRoutingAuthority,
        refresh: Boolean,
    ): OpeningRoutingResult = when (val result = currentSession(authority.posProfile)) {
        is CurrentSessionResult.Success -> {
            val opening = result.opening
            if (!isMatchingActive(opening, authority)) {
                OpeningRoutingResult(OpeningRoutingDestination.OPENING)
            } else if (!refresh) {
                OpeningRoutingResult(OpeningRoutingDestination.CASHIER)
            } else {
                when (val capabilityResult = refreshCapabilities(BootstrapRefreshTrigger.OPENING_COMPLETED)) {
                    is RepositoryResult.Success -> if (
                        isMatchingActive(capabilityResult.state.opening, authority) &&
                        capabilityResult.state.capabilities.submitSale
                    ) {
                        OpeningRoutingResult(OpeningRoutingDestination.CASHIER)
                    } else {
                        OpeningRoutingResult(OpeningRoutingDestination.OPENING)
                    }
                    is RepositoryResult.Failure -> OpeningRoutingResult(
                        OpeningRoutingDestination.OPENING,
                        capabilityResult.reason,
                    )
                    RepositoryResult.Discarded -> OpeningRoutingResult(OpeningRoutingDestination.OPENING)
                }
            }
        }
        is CurrentSessionResult.Failure -> OpeningRoutingResult(OpeningRoutingDestination.OPENING, result.reason)
        CurrentSessionResult.Discarded -> OpeningRoutingResult(OpeningRoutingDestination.OPENING)
    }

    private fun isMatchingActive(opening: com.rotiropi.pos_erpnext.data.OpeningSession?, authority: OpeningRoutingAuthority): Boolean =
        opening?.posProfile == authority.posProfile &&
            opening.user == authority.cashier &&
            opening.status == OpeningStatus.OPEN
}
