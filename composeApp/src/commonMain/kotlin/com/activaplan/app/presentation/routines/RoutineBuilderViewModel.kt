package com.activaplan.app.presentation.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.DraftLoad
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineDraft
import com.activaplan.app.domain.model.SaveRoutineResult
import com.activaplan.app.domain.usecase.LoadRoutineDraftUseCase
import com.activaplan.app.domain.usecase.SaveRoutineUseCase
import com.activaplan.app.domain.usecase.toRoutineItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface BuilderUiState {
    data object Loading : BuilderUiState

    /** Katalog latihan gagal dimuat; layar menawarkan Muat ulang. */
    data object Error : BuilderUiState

    /** ID rutinitas tidak ada (tautan usang). */
    data object NotFound : BuilderUiState

    /**
     * [catalog] berisi latihan (sudah diterjemahkan) menurut ID untuk menampilkan nama dan zona.
     * [isDirty] true bila ada perubahan yang belum disimpan; [nameError] true setelah simpan ditolak karena nama.
     */
    data class Ready(
        val draft: RoutineDraft,
        val catalog: Map<String, Exercise>,
        val isNew: Boolean,
        val isDirty: Boolean,
        val nameError: Boolean = false,
        /** Kardio menurut ID; item rutinitas yang ID-nya ada di sini tampil sebagai kardio, bukan latihan set/rep. */
        val cardioCatalog: Map<String, CardioWorkout> = emptyMap(),
    ) : BuilderUiState
}

sealed interface BuilderEvent {
    data object Saved : BuilderEvent
    data object NeedItem : BuilderEvent
    data object Full : BuilderEvent
    data object SkippedItems : BuilderEvent

    /** Simpan gagal; [retry] mengulang. */
    data class SaveFailed(val retry: () -> Unit) : BuilderEvent
}

class BuilderViewModel(
    private val routineId: String?,
    private val language: Language,
    private val exercises: ExerciseRepository,
    private val cardio: CardioRepository,
    private val loadDraft: LoadRoutineDraftUseCase,
    private val saveRoutine: SaveRoutineUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BuilderUiState>(BuilderUiState.Loading)
    val uiState: StateFlow<BuilderUiState> = _uiState.asStateFlow()

    private val _events = Channel<BuilderEvent>(Channel.BUFFERED)
    val events: Flow<BuilderEvent> = _events.receiveAsFlow()

    private var baseline: Routine = Routine("", "", emptyList())

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = BuilderUiState.Loading
            _uiState.value = try {
                val catalog = exercises.exercises(language = language).associateBy { it.id }
                val cardioCatalog = cardio.workouts(language = language).associateBy { it.id }
                when (val load = loadDraft(routineId)) {
                    DraftLoad.NotFound -> BuilderUiState.NotFound
                    is DraftLoad.Loaded -> {
                        baseline = load.baseline
                        if (load.skippedItems > 0) _events.send(BuilderEvent.SkippedItems)
                        BuilderUiState.Ready(load.draft, catalog, isNew = routineId == null, isDirty = false, cardioCatalog = cardioCatalog)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BuilderUiState.Error
            }
        }
    }

    fun onName(name: String) = edit(clearNameError = true) { it.rename(name) }

    fun onSets(key: Int, delta: Int) = edit { it.adjustSets(key, delta) }

    fun onWork(key: Int, delta: Int) = edit { it.adjustWork(key, delta) }

    fun onRemove(key: Int) = edit { it.remove(key) }

    fun onMove(from: Int, to: Int) = edit { it.move(from, to) }

    /** Menambah latihan atau kardio hasil mode pilih; ID yang tidak dikenal diabaikan. */
    fun onExercisePicked(exerciseId: String) {
        val ready = _uiState.value as? BuilderUiState.Ready ?: return
        val item = ready.catalog[exerciseId]?.toRoutineItem() ?: ready.cardioCatalog[exerciseId]?.toRoutineItem() ?: return
        val added = ready.draft.add(item)
        if (added == null) _events.trySend(BuilderEvent.Full) else edit { added }
    }

    fun save() {
        val ready = _uiState.value as? BuilderUiState.Ready ?: return
        viewModelScope.launch {
            val event = try {
                when (saveRoutine(ready.draft)) {
                    SaveRoutineResult.Saved -> {
                        baseline = ready.draft.toRoutine()
                        (_uiState.value as? BuilderUiState.Ready)?.let {
                            _uiState.value = it.copy(isDirty = it.draft.toRoutine() != baseline)
                        }
                        BuilderEvent.Saved
                    }
                    SaveRoutineResult.InvalidName -> {
                        _uiState.value = ready.copy(nameError = true)
                        null
                    }
                    SaveRoutineResult.NoItems -> BuilderEvent.NeedItem
                    SaveRoutineResult.TooManyItems -> BuilderEvent.Full
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BuilderEvent.SaveFailed(retry = ::save)
            }
            if (event != null) _events.send(event)
        }
    }

    private fun edit(clearNameError: Boolean = false, transform: (RoutineDraft) -> RoutineDraft) {
        val ready = _uiState.value as? BuilderUiState.Ready ?: return
        val draft = transform(ready.draft)
        _uiState.value = ready.copy(
            draft = draft,
            isDirty = draft.toRoutine() != baseline,
            nameError = ready.nameError && !clearNameError,
        )
    }
}
