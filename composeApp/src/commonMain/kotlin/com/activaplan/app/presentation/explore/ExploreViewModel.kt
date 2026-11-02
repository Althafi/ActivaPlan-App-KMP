package com.activaplan.app.presentation.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.ActivityKind
import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.Category
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.ExploreQuery
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.usecase.FilterCardioUseCase
import com.activaplan.app.domain.usecase.FilterExercisesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ExploreUiState {
    data object Loading : ExploreUiState

    /** Katalog latihan gagal dimuat; layar menawarkan Muat ulang. */
    data object Error : ExploreUiState

    /** [results] terisi untuk jenis Kekuatan, [cardioResults] untuk jenis Kardio (sesuai `ExploreQuery.kind`). */
    data class Ready(
        val results: List<Exercise>,
        val cardioResults: List<CardioWorkout> = emptyList(),
    ) : ExploreUiState {
        val isEmpty: Boolean get() = results.isEmpty() && cardioResults.isEmpty()
    }
}

/**
 * Isi katalog dimuat sekali per [language]; filter dihitung di memori sehingga hasil langsung berubah.
 * Mode awal diambil dari [initialMode] (alat yang dipilih di Onboarding); layar menunggu sampai terisi agar tidak berkedip.
 */
class ExploreViewModel(
    private val exercises: ExerciseRepository,
    private val cardio: CardioRepository,
    private val filter: FilterExercisesUseCase,
    private val filterCardio: FilterCardioUseCase,
    private val language: Language,
    private val initialMode: suspend () -> Mode,
) : ViewModel() {

    private val _query = MutableStateFlow(ExploreQuery(mode = Mode.NO_EQUIPMENT))
    private val modeResolved = MutableStateFlow(false)
    private var modeChosenByUser = false

    /** Dipisah dari [uiState] agar teks di kolom cari selalu sinkron saat mengetik. */
    val query: StateFlow<ExploreQuery> = _query.asStateFlow()

    private sealed interface Load {
        data object Loading : Load
        data object Failed : Load
        data class Done(val all: List<Exercise>, val cardio: List<CardioWorkout>) : Load
    }

    private val load = MutableStateFlow<Load>(Load.Loading)

    val uiState: StateFlow<ExploreUiState> = combine(load, _query, modeResolved) { loaded, query, resolved ->
        when {
            !resolved -> ExploreUiState.Loading
            loaded is Load.Loading -> ExploreUiState.Loading
            loaded is Load.Failed -> ExploreUiState.Error
            loaded is Load.Done && query.kind == ActivityKind.CARDIO ->
                ExploreUiState.Ready(emptyList(), filterCardio(loaded.cardio, query))
            loaded is Load.Done -> ExploreUiState.Ready(filter(loaded.all, query))
            else -> ExploreUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ExploreUiState.Loading)

    init {
        viewModelScope.launch {
            val mode = try {
                initialMode()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Mode.NO_EQUIPMENT
            }
            // Bila pengguna sempat mengetuk mode lain sebelum setelan terbaca, pilihannya yang dipakai.
            if (!modeChosenByUser) _query.update { it.copy(mode = mode) }
            modeResolved.value = true
        }
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            load.value = Load.Loading
            load.value = try {
                Load.Done(exercises.exercises(language = language), cardio.workouts(language = language))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Load.Failed
            }
        }
    }

    fun onMode(mode: Mode) {
        modeChosenByUser = true
        _query.update { it.copy(mode = mode) }
    }

    fun onKind(kind: ActivityKind) = _query.update { it.copy(kind = kind) }

    fun onText(text: String) = _query.update { it.copy(text = text) }

    fun onCategory(category: Category?) = _query.update { it.copy(category = category) }

    fun onDifficulty(difficulty: Difficulty?) = _query.update { it.copy(difficulty = difficulty) }

    /** Mengosongkan teks, kategori, dan level; mode dan jenis tetap. */
    fun resetFilters() = _query.update { it.copy(text = "", category = null, difficulty = null) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
