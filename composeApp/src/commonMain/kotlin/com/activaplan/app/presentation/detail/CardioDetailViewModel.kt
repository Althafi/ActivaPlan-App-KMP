package com.activaplan.app.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.AddToRoutineResult
import com.activaplan.app.domain.model.CardioDetail
import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.usecase.AddExerciseToRoutineUseCase
import com.activaplan.app.domain.usecase.GetCardioDetailUseCase
import com.activaplan.app.domain.usecase.ObserveSettingsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CardioDetailUiState {
    data object Loading : CardioDetailUiState

    /** Katalog kardio gagal dimuat; layar menawarkan Muat ulang. */
    data object Error : CardioDetailUiState

    /** ID tidak ada di katalog (tautan usang). */
    data object NotFound : CardioDetailUiState

    data class Ready(val detail: CardioDetail) : CardioDetailUiState
}

class CardioDetailViewModel(
    private val id: String,
    private val language: Language,
    private val getDetail: GetCardioDetailUseCase,
    private val addToRoutine: AddExerciseToRoutineUseCase,
    observeSettings: ObserveSettingsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CardioDetailUiState>(CardioDetailUiState.Loading)
    val uiState: StateFlow<CardioDetailUiState> get() = _uiState

    val routines: StateFlow<List<Routine>> = addToRoutine.routines
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** Usia dari Setelan untuk perkiraan zona detak jantung; null bila belum diisi. */
    val age: StateFlow<Int?> = observeSettings()
        .map { it.age }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _outcomes = Channel<AddOutcome>(Channel.BUFFERED)
    val outcomes: Flow<AddOutcome> = _outcomes.receiveAsFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = CardioDetailUiState.Loading
            _uiState.value = try {
                getDetail(id, language)?.let { CardioDetailUiState.Ready(it) } ?: CardioDetailUiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                CardioDetailUiState.Error
            }
        }
    }

    fun addToExisting(routineId: String) = add { addToRoutine.toExisting(routineId, it) }

    fun addToNew(name: String) = add { addToRoutine.toNew(name, it) }

    private fun add(action: suspend (CardioWorkout) -> AddToRoutineResult) {
        val workout = (_uiState.value as? CardioDetailUiState.Ready)?.detail?.workout ?: return
        viewModelScope.launch {
            val outcome = try {
                AddOutcome.Done(action(workout))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AddOutcome.Failed(retry = { add(action) })
            }
            _outcomes.send(outcome)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
