package com.activaplan.app.presentation.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.ProgramDetail
import com.activaplan.app.domain.usecase.GetProgramDetailUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProgramPreviewUiState {
    data object Loading : ProgramPreviewUiState

    /** Katalog gagal dimuat; layar menawarkan Muat ulang. */
    data object Error : ProgramPreviewUiState

    /** ID program tidak ada atau ada pekan yang tak punya latihan yang dikenal. */
    data object NotFound : ProgramPreviewUiState

    data class Ready(val detail: ProgramDetail) : ProgramPreviewUiState
}

class ProgramPreviewViewModel(
    private val id: String,
    private val language: Language,
    private val getDetail: GetProgramDetailUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProgramPreviewUiState>(ProgramPreviewUiState.Loading)
    val uiState: StateFlow<ProgramPreviewUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = ProgramPreviewUiState.Loading
            _uiState.value = try {
                getDetail(id, language)?.let { ProgramPreviewUiState.Ready(it) } ?: ProgramPreviewUiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ProgramPreviewUiState.Error
            }
        }
    }
}
