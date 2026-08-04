package com.rotiropi.pos_erpnext.ui.customer

import com.rotiropi.pos_erpnext.data.CustomerSearchFailure
import com.rotiropi.pos_erpnext.data.CustomerSearchPage as RepositoryPage
import com.rotiropi.pos_erpnext.data.CustomerSearchResult
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
) {
    private val scope = CoroutineScope(dispatcher)
    private var identity: CustomerSearchIdentity? = null
    private var requestJob: Job? = null
    private var cancellation: ApiCallCancellation? = null
    private var activeAuthority: CustomerSearchAuthority? = null
    private var nextStart: Int? = null
    private var successfulInitialQuery: String? = null
    private var nextRequestId = 0L
    var activeGeneration: Long = 0
        private set
    private val _state = MutableStateFlow(CustomerSearchUiState())
    val state: StateFlow<CustomerSearchUiState> = _state.asStateFlow()

    /** Must be called on the main thread. */
    fun bind(value: CustomerSearchIdentity) {
        if (identity == value) return
        invalidateAndCancel()
        identity = value
        _state.value = CustomerSearchUiState(selection = CustomerSelection.WalkIn(value.walkInCustomerId, ""))
    }

    fun onQueryChanged(value: String) {
        val query = value.trim()
        if (query == successfulInitialQuery ||
            (query == _state.value.query && (_state.value.customers.isNotEmpty() || _state.value.error != null || _state.value.pageError != null))
        ) return
        invalidateAndCancel()
        val current = identity ?: return
        _state.value = _state.value.copy(query = query, customers = emptyList(), loading = true, hasMore = false, error = null, pageError = null)
        requestJob = scope.launch {
            delay(300)
            begin(CustomerSearchAuthority(current, activeGeneration, query, 0))
        }
    }

    fun loadMore() {
        val start = nextStart ?: return
        val current = identity ?: return
        if (_state.value.loading || !_state.value.hasMore || _state.value.customers.size >= MAX_CUSTOMERS) return
        begin(CustomerSearchAuthority(current, activeGeneration, _state.value.query, start))
    }

    fun retry() {
        val authority = activeAuthority ?: return
        if (_state.value.loading) return
        begin(authority)
    }

    fun onWalkInDisplayNameChanged(value: String) {
        val selection = _state.value.selection as? CustomerSelection.WalkIn ?: return
        _state.value = _state.value.copy(selection = selection.copy(displayName = value))
    }

    fun selectRegistered(customer: CustomerRecord) {
        _state.value = _state.value.copy(selection = CustomerSelection.Registered(customer.id, customer.displayLabel, customer.mobile))
    }

    fun selectWalkIn() {
        val current = identity ?: return
        _state.value = _state.value.copy(selection = CustomerSelection.WalkIn(current.walkInCustomerId, ""))
    }

    fun clear() {
        invalidateAndCancel()
        identity = null
        _state.value = CustomerSearchUiState()
    }

    /** Testable completion boundary; production results arrive through [begin]. */
    internal fun publishForAuthority(authority: CustomerSearchAuthority, result: CustomerSearchResult) {
        when (result) {
            is CustomerSearchResult.Success -> publishSuccess(authority, result.page.toUi())
            is CustomerSearchResult.Failure -> publishFailure(authority, result.reason.toUi())
        }
    }

    private fun begin(authority: CustomerSearchAuthority) {
        if (!isCurrent(authority)) return
        cancellation?.cancel()
        requestJob?.cancel()
        val requestAuthority = authority.copy(requestId = ++nextRequestId)
        val request = CustomerSearchRequest(requestAuthority.query, requestAuthority.identity.posProfile, requestAuthority.start, CUSTOMER_PAGE_SIZE)
        val callCancellation = cancellationFactory()
        activeAuthority = requestAuthority
        cancellation = callCancellation
        _state.value = _state.value.copy(loading = true, error = null, pageError = null)
        requestJob = scope.launch {
            when (val result = search(request, callCancellation)) {
                is CustomerSearchResult.Success -> publishSuccess(requestAuthority, result.page.toUi())
                is CustomerSearchResult.Failure -> publishFailure(requestAuthority, result.reason.toUi())
            }
        }
    }

    private fun publishSuccess(authority: CustomerSearchAuthority, page: CustomerSearchPage) {
        if (!isCurrent(authority) || activeAuthority != authority || page.limit <= 0) return
        if (authority.start > 0 && page.start < authority.start) {
            nextStart = null
            _state.value = _state.value.copy(loading = false, hasMore = false, pageError = null)
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

    private fun publishFailure(authority: CustomerSearchAuthority, failure: CustomerSearchError) {
        if (!isCurrent(authority) || activeAuthority != authority) return
        _state.value = if (authority.start == 0) {
            successfulInitialQuery = null
            _state.value.copy(customers = emptyList(), loading = false, error = failure, pageError = null)
        } else {
            _state.value.copy(loading = false, pageError = failure)
        }
    }

    private fun isCurrent(authority: CustomerSearchAuthority): Boolean =
        authority.generation == activeGeneration && authority.identity == identity && authority.query == _state.value.query

    private fun invalidateAndCancel() {
        activeGeneration++
        cancellation?.cancel()
        cancellation = null
        requestJob?.cancel()
        requestJob = null
        activeAuthority = null
        nextStart = null
        successfulInitialQuery = null
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
