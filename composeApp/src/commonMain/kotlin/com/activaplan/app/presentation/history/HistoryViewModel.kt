package com.activaplan.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.activaplan.app.domain.model.HistoryData
import com.activaplan.app.domain.usecase.ObserveHistoryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    /** Riwayat gagal dibaca; layar menawarkan Muat ulang. */
    data object Error : HistoryUiState

    data class Ready(val data: HistoryData) : HistoryUiState
}

class HistoryViewModel(observeHistory: ObserveHistoryUseCase) : ViewModel() {

    private val reloads = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = reloads.flatMapLatest {
        observeHistory()
            .map<HistoryData, HistoryUiState> { HistoryUiState.Ready(it) }
            .onStart { emit(HistoryUiState.Loading) }
            .catch { emit(HistoryUiState.Error) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HistoryUiState.Loading)

    fun reload() {
        reloads.value += 1
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
