package com.rotiropi.pos_erpnext.ui.customer

import com.rotiropi.pos_erpnext.data.CustomerSearchFailure
import com.rotiropi.pos_erpnext.data.CustomerSearchPage as RepositoryPage
import com.rotiropi.pos_erpnext.data.CustomerSearchResult
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val CUSTOMER_PAGE_SIZE = 20
const val MAX_CUSTOMERS = 100

data class CustomerSearchIdentity(val cashier: String, val posProfile: String, val walkInCustomerId: String)
data class CustomerSearchRequest(val query: String, val posProfile: String, val start: Int, val limit: Int)
data class CustomerSearchAuthority(
    val identity: CustomerSearchIdentity,
    val generation: Long,
    val query: String,
    val start: Int,
    val requestId: Long = 0,
)
data class CustomerRecord(val id: String, val displayLabel: String, val mobile: String?, val isDefaultWalkIn: Boolean)
data class CustomerSearchPage(val customers: List<CustomerRecord>, val start: Int, val limit: Int, val hasMore: Boolean)

sealed interface CustomerSelection {
    data class WalkIn(val customerId: String, val displayName: String) : CustomerSelection
    data class Registered(val customerId: String, val displayLabel: String, val mobile: String?) : CustomerSelection
}

data class CustomerSearchUiState(
    val query: String = "",
    val customers: List<CustomerRecord> = emptyList(),
    val selection: CustomerSelection? = null,
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val error: CustomerSearchError? = null,
    val pageError: CustomerSearchError? = null,
)

sealed interface CustomerSearchError {
    data object AuthenticationRequired : CustomerSearchError
    data object AuthorizationDenied : CustomerSearchError
    data object Unavailable : CustomerSearchError
    data class Stable(val code: String) : CustomerSearchError
    data object Protocol : CustomerSearchError
}

class CustomerSearchViewModel(
    dispatcher: CoroutineDispatcher,
    private val search: (CustomerSearchRequest, ApiCallCancellation) -> CustomerSearchResult,
    private val cancellationFactory: () -> ApiCallCancellation = ::ApiCallCancellation,
    private val debounceMillis: Long = 300,
    private val completionCheckpoint: (CustomerSearchAuthority) -> Unit = {},
    private val requestAdmissionCheckpoint: (CustomerSearchAuthority) -> Unit = {},
) {
    private val scope = CoroutineScope(dispatcher)
    private val mutationLock = Any()
    private var identity: CustomerSearchIdentity? = null
    private var scheduledJob: Job? = null
    private var requestJob: Job? = null
    private var cancellation: ApiCallCancellation? = null
    private var activeAuthority: CustomerSearchAuthority? = null
    private var scheduledQuery: String? = null
    private var inFlightQuery: String? = null
    private var nextStart: Int? = null
    private var successfulInitialQuery: String? = null
    private var nextRequestId = 0L
    private var generation = 0L
    val activeGeneration: Long
        get() = synchronized(mutationLock) { generation }
    private val _state = MutableStateFlow(CustomerSearchUiState())
    val state: StateFlow<CustomerSearchUiState> = _state.asStateFlow()

    fun bind(value: CustomerSearchIdentity) {
        synchronized(mutationLock) {
            if (identity == value) return
            invalidateAndCancelLocked()
            identity = value
            _state.value = CustomerSearchUiState(selection = CustomerSelection.WalkIn(value.walkInCustomerId, ""))
        }
    }

    fun onQueryChanged(value: String) {
        synchronized(mutationLock) { onQueryChangedLocked(value.trim()) }
    }

    fun onSelectorOpened() {
        synchronized(mutationLock) { onQueryChangedLocked(_state.value.query) }
    }

    private fun onQueryChangedLocked(query: String) {
        if (query == scheduledQuery || query == inFlightQuery || query == successfulInitialQuery ||
            (query == _state.value.query && (_state.value.customers.isNotEmpty() || _state.value.error != null || _state.value.pageError != null))
        ) return
        invalidateAndCancelLocked()
        val current = identity ?: return
        _state.value = _state.value.copy(query = query, customers = emptyList(), loading = true, hasMore = false, error = null, pageError = null)
        val authority = CustomerSearchAuthority(current, generation, query, 0)
        scheduledQuery = query
        scheduledJob = scope.launch {
            delay(debounceMillis)
            begin(authority)
        }
    }

    fun loadMore() {
        val authority = synchronized(mutationLock) {
            val start = nextStart ?: return
            val current = identity ?: return
            if (_state.value.loading || !_state.value.hasMore || _state.value.customers.size >= MAX_CUSTOMERS) return
            CustomerSearchAuthority(current, generation, _state.value.query, start)
        }
        requestAdmissionCheckpoint(authority)
        begin(authority)
    }

    fun retry() {
        val authority = synchronized(mutationLock) {
            val current = activeAuthority ?: return
            if (_state.value.loading) return
            current
        }
        requestAdmissionCheckpoint(authority)
        begin(authority)
    }

    fun onWalkInDisplayNameChanged(value: String) {
        synchronized(mutationLock) {
            val selection = _state.value.selection as? CustomerSelection.WalkIn ?: return
            _state.value = _state.value.copy(selection = selection.copy(displayName = value))
        }
    }

    fun selectCustomer(customer: CustomerRecord) {
        synchronized(mutationLock) {
            val current = identity ?: return
            _state.value = _state.value.copy(
                selection = if (customer.id == current.walkInCustomerId && customer.isDefaultWalkIn) {
                    CustomerSelection.WalkIn(
                        customer.id,
                        (_state.value.selection as? CustomerSelection.WalkIn)?.displayName.orEmpty(),
                    )
                } else {
                    CustomerSelection.Registered(customer.id, customer.displayLabel, customer.mobile)
                },
            )
        }
    }

    fun selectWalkIn() {
        synchronized(mutationLock) {
            val current = identity ?: return
            _state.value = _state.value.copy(selection = CustomerSelection.WalkIn(current.walkInCustomerId, ""))
        }
    }

    fun clear() {
        synchronized(mutationLock) {
            invalidateAndCancelLocked()
            clearUiLocked()
        }
    }

    fun invalidateAuthority() {
        synchronized(mutationLock) { invalidateAuthorityLocked() }
    }

    fun cancelActiveRequest() {
        synchronized(mutationLock) { cancelActiveRequestLocked() }
    }

    fun clearUi() {
        synchronized(mutationLock) { clearUiLocked() }
    }

    /** Testable completion boundary; production results arrive through [begin]. */
    internal fun publishForAuthority(authority: CustomerSearchAuthority, result: CustomerSearchResult) {
        synchronized(mutationLock) {
            when (result) {
                is CustomerSearchResult.Success -> publishSuccessLocked(authority, result.page.toUi())
                is CustomerSearchResult.Failure -> publishFailureLocked(authority, result.reason.toUi())
            }
        }
    }

    private fun begin(authority: CustomerSearchAuthority) {
        val job = synchronized(mutationLock) {
            if (!isCurrentLocked(authority)) return
            if (scheduledJob == null && _state.value.loading) return
            cancellation?.cancel()
            requestJob?.cancel()
            scheduledJob = null
            scheduledQuery = null
            val requestAuthority = authority.copy(requestId = ++nextRequestId)
            val request = CustomerSearchRequest(requestAuthority.query, requestAuthority.identity.posProfile, requestAuthority.start, CUSTOMER_PAGE_SIZE)
            val callCancellation = cancellationFactory()
            activeAuthority = requestAuthority
            inFlightQuery = requestAuthority.query
            cancellation = callCancellation
            _state.value = _state.value.copy(loading = true, error = null, pageError = null)
            scope.launch(start = CoroutineStart.LAZY) {
                val result = search(request, callCancellation)
                if (!isCompletionCandidate(requestAuthority)) return@launch
                completionCheckpoint(requestAuthority)
                synchronized(mutationLock) {
                    when (result) {
                        is CustomerSearchResult.Success -> publishSuccessLocked(requestAuthority, result.page.toUi())
                        is CustomerSearchResult.Failure -> publishFailureLocked(requestAuthority, result.reason.toUi())
                    }
                }
            }.also { requestJob = it }
        }
        job.start()
    }

    private fun isCompletionCandidate(authority: CustomerSearchAuthority): Boolean =
        synchronized(mutationLock) {
            isCurrentLocked(authority) && activeAuthority == authority
        }

    private fun publishSuccessLocked(authority: CustomerSearchAuthority, page: CustomerSearchPage) {
        if (!isCurrentLocked(authority) || activeAuthority != authority) return
        inFlightQuery = null
        if (!hasValidMetadata(authority, page)) {
            nextStart = null
            if (authority.start == 0) successfulInitialQuery = null
            _state.value = if (authority.start == 0) {
                _state.value.copy(
                    customers = emptyList(),
                    loading = false,
                    hasMore = false,
                    error = CustomerSearchError.Protocol,
                    pageError = null,
                )
            } else {
                _state.value.copy(
                    loading = false,
                    hasMore = false,
                    pageError = CustomerSearchError.Protocol,
                )
            }
            return
        }
        val current = _state.value.customers
        val customers = (if (page.start == 0) page.customers else current + page.customers).distinctBy(CustomerRecord::id).take(MAX_CUSTOMERS)
        if (authority.start == 0) successfulInitialQuery = authority.query
        val candidate = page.start + page.limit
        nextStart = candidate.takeIf {
            page.hasMore && page.start >= authority.start && it > authority.start && customers.size < MAX_CUSTOMERS
        }
        _state.value = _state.value.copy(customers = customers, loading = false, hasMore = nextStart != null, error = null, pageError = null)
    }

    private fun hasValidMetadata(authority: CustomerSearchAuthority, page: CustomerSearchPage): Boolean {
        if (page.start < 0 || page.limit <= 0) return false
        if (authority.start > 0 && page.start < authority.start) return false
        val candidate = page.start.toLong() + page.limit
        if (candidate > Int.MAX_VALUE) return false
        return authority.start == 0 || candidate > authority.start
    }

    private fun publishFailureLocked(authority: CustomerSearchAuthority, failure: CustomerSearchError) {
        if (!isCurrentLocked(authority) || activeAuthority != authority) return
        inFlightQuery = null
        _state.value = if (authority.start == 0) {
            successfulInitialQuery = null
            _state.value.copy(customers = emptyList(), loading = false, error = failure, pageError = null)
        } else {
            _state.value.copy(loading = false, pageError = failure)
        }
    }

    private fun isCurrentLocked(authority: CustomerSearchAuthority): Boolean =
        authority.generation == generation && authority.identity == identity && authority.query == _state.value.query

    private fun invalidateAuthorityLocked() {
        generation++
        activeAuthority = null
        scheduledQuery = null
        inFlightQuery = null
        nextStart = null
        successfulInitialQuery = null
    }

    private fun cancelActiveRequestLocked() {
        cancellation?.cancel()
        cancellation = null
        scheduledJob?.cancel()
        scheduledJob = null
        requestJob?.cancel()
        requestJob = null
    }

    private fun invalidateAndCancelLocked() {
        invalidateAuthorityLocked()
        cancelActiveRequestLocked()
    }

    private fun clearUiLocked() {
        identity = null
        _state.value = CustomerSearchUiState()
    }

    private fun RepositoryPage.toUi() = CustomerSearchPage(customers.map { CustomerRecord(it.id, it.displayLabel, it.mobile, it.isDefaultWalkIn) }, start, limit, hasMore)
    private fun CustomerSearchFailure.toUi(): CustomerSearchError = when (this) {
        CustomerSearchFailure.AuthenticationRequired -> CustomerSearchError.AuthenticationRequired
        CustomerSearchFailure.AuthorizationDenied -> CustomerSearchError.AuthorizationDenied
        CustomerSearchFailure.Unavailable -> CustomerSearchError.Unavailable
        is CustomerSearchFailure.Stable -> CustomerSearchError.Stable(code)
        is CustomerSearchFailure.Protocol -> CustomerSearchError.Protocol
    }
}

/** Returns a user-facing error message for display in the customer search UI. */
fun CustomerSearchError.toUiMessage(): String = when (this) {
    CustomerSearchError.AuthenticationRequired -> "Session expired. Please sign in again."
    CustomerSearchError.AuthorizationDenied -> "You do not have permission to search customers."
    CustomerSearchError.Unavailable -> "Customer search is unavailable. Check your connection."
    is CustomerSearchError.Stable -> "Search failed. Please try again."
    CustomerSearchError.Protocol -> "Search failed. Please try again."
}
