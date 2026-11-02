package com.activaplan.app.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.activaplan.app.domain.cue.CuePlayer
import com.activaplan.app.domain.cue.NoCuePlayer
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.SessionOverlay
import com.activaplan.app.domain.model.SessionPhase
import com.activaplan.app.domain.model.SessionSummary
import com.activaplan.app.domain.model.Settings
import com.activaplan.app.domain.reminder.PermissionState
import com.activaplan.app.domain.session.SessionState
import com.activaplan.app.domain.tracking.GpsStatus
import com.activaplan.app.domain.tracking.LocationTracker
import com.activaplan.app.domain.tracking.NoLocationTracker
import com.activaplan.app.domain.tracking.TrackAccumulator
import com.activaplan.app.domain.tracking.TrackPoint
import com.activaplan.app.domain.tracking.TrackingUi
import com.activaplan.app.domain.usecase.FinishSessionUseCase
import com.activaplan.app.domain.usecase.LoadSessionPlanUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

sealed interface SessionUiState {
    data object Loading : SessionUiState

    /** Katalog latihan gagal dimuat; layar menawarkan Muat ulang. */
    data object Error : SessionUiState

    /** Rutinitas tidak ada atau tidak punya latihan yang dikenal. */
    data object NotFound : SessionUiState

    /**
     * [streak] baru terisi setelah sesi selesai dan lognya tersimpan. [tracking] = keadaan GPS (OFF bila tidak dipakai);
     * [age] = usia dari Setelan untuk perkiraan zona detak jantung.
     */
    data class Ready(
        val state: SessionState,
        val streak: Int = 0,
        val tracking: TrackingUi = TrackingUi(),
        val age: Int? = null,
    ) : SessionUiState
}

/** Kejadian sekali pakai yang ditampilkan layar (snackbar, haptic, keluar). */
sealed interface SessionEvent {
    /** Satu set selesai. [restSeconds] 0 bila tidak ada istirahat sesudahnya (sesi berakhir atau istirahat 0 detik). */
    data class SetCompleted(val setNumber: Int, val totalSets: Int, val restSeconds: Int) : SessionEvent

    /** Ada latihan di rutinitas yang tidak ditemukan di katalog dan dilewati. */
    data object ExercisesSkipped : SessionEvent

    /** Log sesi gagal disimpan; [retry] mengulang penyimpanan yang sama. */
    data class SaveFailed(val retry: () -> Unit) : SessionEvent

    /** Layar sesi harus ditutup. */
    data object Exit : SessionEvent
}

private val timeOrigin = TimeSource.Monotonic.markNow()

private fun monotonicMillis(): Long = timeOrigin.elapsedNow().inWholeMilliseconds

/**
 * Menjalankan [SessionState]: mengalirkan waktu nyata ke timer dan menyimpan log di akhir sesi.
 *
 * Waktu dihitung dari jam monotonik, bukan dari jumlah ketukan, jadi rotasi layar dan kembali dari background
 * tidak menggeser timer. ViewModel bertahan selama layar ada di back stack, sehingga tidak ikut hilang saat rotasi.
 * Log disimpan sebelum layar ditutup; bila gagal, pengguna diberi pilihan mengulang dan sesi tidak ditutup.
 */
class SessionViewModel(
    private val routineId: String,
    private val language: Language,
    private val loadPlan: LoadSessionPlanUseCase,
    private val finishSession: FinishSessionUseCase,
    private val nowMillis: () -> Long = ::monotonicMillis,
    private val tracker: LocationTracker = NoLocationTracker,
    private val cues: CuePlayer = NoCuePlayer,
    private val loadSettings: suspend () -> Settings = { Settings.DEFAULT },
    /** Judul notifikasi layanan GPS di Android. */
    private val trackingTitle: String = "",
) : ViewModel() {

    private val _uiState = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val uiState: StateFlow<SessionUiState> get() = _uiState

    private val _events = Channel<SessionEvent>(Channel.BUFFERED)
    val events: Flow<SessionEvent> = _events.receiveAsFlow()

    private var ticker: Job? = null
    private var trackerJob: Job? = null
    private var accumulator = TrackAccumulator()
    private var saving = false
    private var logSaved = false

    init {
        load()
    }

    fun reload() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = SessionUiState.Loading
            val plan = try {
                loadPlan(routineId, language)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = SessionUiState.Error
                return@launch
            }
            if (plan == null) {
                _uiState.value = SessionUiState.NotFound
                return@launch
            }
            logSaved = false
            val settings = try {
                loadSettings()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Settings.DEFAULT
            }
            _uiState.value = SessionUiState.Ready(SessionState.start(plan), age = settings.age)
            if (plan.skippedItems > 0) _events.send(SessionEvent.ExercisesSkipped)
            if (settings.gpsTracking && plan.exercises.any { it.cardio != null }) startTracking(plan.routineName)
            startTicker()
        }
    }

    /** Menyalakan GPS hanya bila izin sudah diberikan; selain itu sesi berjalan tanpa jarak dan pace. */
    private suspend fun startTracking(routineName: String) {
        val granted = try {
            tracker.permission() == PermissionState.GRANTED
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!granted) return
        accumulator = TrackAccumulator()
        setTracking(TrackingUi(GpsStatus.SEARCHING))
        tracker.start(trackingTitle.ifBlank { routineName }, routineName)
        trackerJob?.cancel()
        trackerJob = viewModelScope.launch { tracker.points.collect(::onPoint) }
    }

    /** Titik hanya dihitung saat segmen kardio berjalan; di luar itu jangkar dilepas supaya jeda tidak menambah jarak. */
    private fun onPoint(point: TrackPoint) {
        val s = current
        val counting = s != null && s.isRunning && s.phase is SessionPhase.Working && s.current.cardio != null
        accumulator = if (counting) accumulator.add(point) else accumulator.detach()
        setTracking(TrackingUi(GpsStatus.ACTIVE, accumulator.stats))
    }

    private fun setTracking(tracking: TrackingUi) {
        _uiState.update { if (it is SessionUiState.Ready) it.copy(tracking = tracking) else it }
    }

    private fun stopTracking() {
        trackerJob?.cancel()
        trackerJob = null
        tracker.stop()
    }

    /** Ringkasan dari state machine ditambah jarak GPS. */
    private fun SessionSummary.withDistance() = copy(distanceMeters = accumulator.stats.roundedMeters)

    override fun onCleared() {
        stopTracking()
        super.onCleared()
    }

    fun confirmSafety() = act(SessionState::confirmSafety)

    fun countRep() = act(SessionState::countRep)

    fun completeSet() = act(SessionState::completeSet)

    fun skip() = act(SessionState::requestSkip)

    /** Coba lagi di sheet Gagal; juga dipakai saat sheet ditutup tanpa memilih. */
    fun retry() = act(SessionState::retry)

    fun regress() = act(SessionState::regress)

    fun skipExercise() = act(SessionState::skipExercise)

    fun addRest() = act(SessionState::addRest)

    fun skipRest() = act(SessionState::skipRest)

    fun togglePause() = act(SessionState::togglePause)

    fun dismissStop() = act(SessionState::dismissStop)

    /** Tombol tutup atau Kembali. Sebelum latihan dimulai (cek keselamatan) atau setelah selesai langsung keluar. */
    fun close() {
        val s = current ?: return
        if (s.phase == SessionPhase.SafetyCheck || s.phase is SessionPhase.Finished) {
            _events.trySend(SessionEvent.Exit)
        } else {
            act(SessionState::requestStop)
        }
    }

    /** Hentikan di dialog: log (PARTIAL bila ada set selesai, selain itu ABANDONED) disimpan dulu, lalu layar ditutup. */
    fun confirmStop() {
        val s = current ?: return
        if (s.overlay != SessionOverlay.CONFIRM_STOP) return
        persist(s.summarize().withDistance()) {
            stopTracking()
            setState(s.confirmStop())
            _events.send(SessionEvent.Exit)
        }
    }

    private val current: SessionState? get() = (_uiState.value as? SessionUiState.Ready)?.state

    private fun setState(state: SessionState) {
        _uiState.update { if (it is SessionUiState.Ready) it.copy(state = state) else it }
    }

    private fun act(transition: SessionState.() -> SessionState) {
        val before = current ?: return
        val after = before.transition()
        if (after != before) commit(before, after)
    }

    private fun commit(before: SessionState, after: SessionState) {
        setState(after)
        if (after.setsDone.sum() > before.setsDone.sum()) {
            if (before.current.cardio != null && after.phase !is SessionPhase.Finished) cues.segmentChange()
            val resting = after.phase is SessionPhase.Resting
            _events.trySend(
                SessionEvent.SetCompleted(before.setNumber, before.current.sets, if (resting) before.current.restSeconds else 0),
            )
        }
        val finished = after.phase as? SessionPhase.Finished
        if (finished != null && before.phase !is SessionPhase.Finished && !finished.stopped) {
            if (after.exercises.any { it.cardio != null }) cues.sessionComplete()
            val summary = finished.summary.withDistance()
            stopTracking()
            persist(summary) {
                if (finished.showsSummary) {
                    val streak = try {
                        finishSession.streak(routineId, summary)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        0
                    }
                    _uiState.update { if (it is SessionUiState.Ready) it.copy(streak = streak) else it }
                } else {
                    _events.send(SessionEvent.Exit)
                }
            }
        }
    }

    /** Menulis log sekali. [onSaved] jalan setelah log tersimpan; bila gagal, [SessionEvent.SaveFailed] menawarkan ulang. */
    private fun persist(summary: SessionSummary, onSaved: suspend () -> Unit) {
        if (saving || logSaved) return
        saving = true
        viewModelScope.launch {
            try {
                finishSession.record(routineId, summary)
                logSaved = true
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(SessionEvent.SaveFailed(retry = { persist(summary, onSaved) }))
            } finally {
                saving = false
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            var last = nowMillis()
            while (true) {
                delay(TICK_MILLIS)
                val now = nowMillis()
                val delta = now - last
                last = now
                act { advance(delta) }
            }
        }
    }

    private companion object {
        const val TICK_MILLIS = 200L
    }
}
