package com.rotiropi.pos_erpnext.ui.history

import com.rotiropi.pos_erpnext.data.SaleHistoryPage
import com.rotiropi.pos_erpnext.data.SaleReadResult
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import com.rotiropi.pos_erpnext.data.api.SaleSummaryDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val HISTORY_PAGE_SIZE = 20
const val MAX_HISTORY_ROWS = 100

data class HistoryIdentity(val cashier: String, val posProfile: String)

sealed interface HistoryUiState {
    data object Unavailable : HistoryUiState
    data class Content(
        val sales: List<SaleSummaryDto>,
        val query: String,
        val loading: Boolean,
        val hasMore: Boolean,
        val error: String? = null,
    ) : HistoryUiState
}

class HistoryViewModel(
    dispatcher: CoroutineDispatcher,
    private val listSales: (HistoryIdentity, String, Int, ApiCallCancellation) -> SaleReadResult<SaleHistoryPage>,
    private val cancellationFactory: () -> ApiCallCancellation = ::ApiCallCancellation,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<HistoryUiState>(HistoryUiState.Unavailable)
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()
    private var identity: HistoryIdentity? = null
    private var query = ""
    private var rows = emptyList<SaleSummaryDto>()
    private var nextStart: Int? = null
    private var request: ApiCallCancellation? = null
    private var job: Job? = null
    private var generation = 0L

    fun bind(value: HistoryIdentity) {
        if (identity == value) return
        identity = value
        refresh()
    }

    fun onQueryChanged(value: String) {
        query = value.trim()
        refresh()
    }

    fun refresh() {
        rows = emptyList()
        nextStart = 0
        load(0)
    }

    fun loadMore() = nextStart?.let(::load)

    fun retry() = load(nextStart ?: 0)

    fun clear() {
        generation++
        request?.cancel()
        job?.cancel()
        identity = null
        rows = emptyList()
        nextStart = null
        _state.value = HistoryUiState.Unavailable
    }

    private fun load(start: Int) {
        val active = identity ?: return
        if (start >= MAX_HISTORY_ROWS) return
        generation++
        request?.cancel()
        val requestGeneration = generation
        val cancellation = cancellationFactory().also { request = it }
        _state.value = HistoryUiState.Content(rows, query, loading = true, hasMore = false)
        job = scope.launch {
            if (start == 0 && query.isNotEmpty()) kotlinx.coroutines.delay(300)
            when (val result = listSales(active, query, start, cancellation)) {
                is SaleReadResult.Success -> if (identity == active && generation == requestGeneration && !cancellation.isCancelled) {
                    val page = result.data
                    val merged = (if (start == 0) emptyList() else rows)
                        .associateBy { it.name }.toMutableMap().apply { page.sales.forEach { put(it.name, it) } }
                        .values.take(MAX_HISTORY_ROWS)
                    rows = merged
                    nextStart = (start + page.sales.size).takeIf { page.page.has_more && it < MAX_HISTORY_ROWS }
                    _state.value = HistoryUiState.Content(rows, query, loading = false, hasMore = nextStart != null)
                }
                is SaleReadResult.Failure -> if (identity == active && generation == requestGeneration && !cancellation.isCancelled) {
                    _state.value = HistoryUiState.Content(rows, query, loading = false, hasMore = nextStart != null, error = result.code)
                }
            }
        }
    }
}
