package com.rotiropi.pos_erpnext.ui.history

import com.rotiropi.pos_erpnext.data.SaleReadResult
import com.rotiropi.pos_erpnext.data.api.ApiCallCancellation
import com.rotiropi.pos_erpnext.data.api.SaleDetailDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SaleDetailUiState {
    data object Unavailable : SaleDetailUiState
    data object Loading : SaleDetailUiState
    data class Content(val sale: SaleDetailDto) : SaleDetailUiState
    data class Error(val code: String) : SaleDetailUiState
}

class SaleDetailViewModel(
    dispatcher: CoroutineDispatcher,
    private val getSale: (String, ApiCallCancellation) -> SaleReadResult<SaleDetailDto>,
    private val cancellationFactory: () -> ApiCallCancellation = ::ApiCallCancellation,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<SaleDetailUiState>(SaleDetailUiState.Unavailable)
    val state: StateFlow<SaleDetailUiState> = _state.asStateFlow()
    private var name: String? = null
    private var generation = 0L
    private var cancellation: ApiCallCancellation? = null

    fun load(value: String) {
        name = value
        generation++
        val current = generation
        cancellation?.cancel()
        val request = cancellationFactory().also { cancellation = it }
        _state.value = SaleDetailUiState.Loading
        scope.launch {
            _state.value = when (val result = getSale(value, request)) {
                is SaleReadResult.Success -> if (name == value && generation == current && !request.isCancelled) SaleDetailUiState.Content(result.data) else return@launch
                is SaleReadResult.Failure -> if (name == value && generation == current && !request.isCancelled) SaleDetailUiState.Error(result.code) else return@launch
            }
        }
    }

    fun clear() { generation++; cancellation?.cancel(); name = null; _state.value = SaleDetailUiState.Unavailable }
}
