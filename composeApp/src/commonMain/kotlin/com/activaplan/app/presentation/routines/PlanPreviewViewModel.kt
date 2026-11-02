package com.activaplan.app.presentation.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.ApplyPlanResult
import com.activaplan.app.domain.model.ConflictPolicy
import com.activaplan.app.domain.model.PlanDayType
import com.activaplan.app.domain.model.WeeklyPlanDetail
import com.activaplan.app.domain.reminder.nextOccurrence
import com.activaplan.app.domain.time.LocalClock
import com.activaplan.app.domain.usecase.ApplyWeeklyPlanUseCase
import com.activaplan.app.domain.usecase.GetWeeklyPlanDetailUseCase
import com.activaplan.app.domain.usecase.ObserveSettingsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime

sealed interface PlanPreviewUiState {
    data object Loading : PlanPreviewUiState

    /** Katalog gagal dimuat; layar menawarkan Muat ulang. */
    data object Error : PlanPreviewUiState

    /** ID rencana tidak ada atau tak punya hari latihan yang dikenal. */
    data object NotFound : PlanPreviewUiState

    /**
     * [timeMinutes] = jam latihan yang akan dijadwalkan (awalnya waktu default Setelan). [firstSession] = kemunculan
     * pertama sesi rencana ini setelah sekarang (bisa pekan depan bila hari-harinya sudah lewat), null bila tak dihitung.
     */
    data class Ready(
        val detail: WeeklyPlanDetail,
        val timeMinutes: Int,
        val firstSession: LocalDateTime? = null,
    ) : PlanPreviewUiState
}

/** Hasil menekan "Pakai Rencana Ini". [Failed.retry] mengulang dengan kebijakan yang sama. */
sealed interface PlanApplyEvent {
    data object Applied : PlanApplyEvent
    data object Conflict : PlanApplyEvent
    data object Unavailable : PlanApplyEvent
    data class Failed(val retry: () -> Unit) : PlanApplyEvent
}

class PlanPreviewViewModel(
    private val id: String,
    private val language: Language,
    private val getDetail: GetWeeklyPlanDetailUseCase,
    private val applyPlan: ApplyWeeklyPlanUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    private val clock: LocalClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlanPreviewUiState>(PlanPreviewUiState.Loading)
    val uiState: StateFlow<PlanPreviewUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlanApplyEvent>(Channel.BUFFERED)
    val events: Flow<PlanApplyEvent> = _events.receiveAsFlow()

    private var reminder = true

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = PlanPreviewUiState.Loading
            _uiState.value = try {
                val settings = observeSettings().first()
                reminder = settings.remindersEnabled
                getDetail(id, language)?.let { ready(it, settings.defaultTimeMinutes) } ?: PlanPreviewUiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                PlanPreviewUiState.Error
            }
        }
    }

    fun onTime(timeMinutes: Int) {
        _uiState.update { if (it is PlanPreviewUiState.Ready) ready(it.detail, timeMinutes) else it }
    }

    private fun ready(detail: WeeklyPlanDetail, timeMinutes: Int): PlanPreviewUiState.Ready {
        val now = clock.now()
        val first = detail.days.filter { it.type != PlanDayType.REST }.minOfOrNull { nextOccurrence(it.dayOfWeek, timeMinutes, now) }
        return PlanPreviewUiState.Ready(detail, timeMinutes, first)
    }

    fun use(policy: ConflictPolicy = ConflictPolicy.ASK) {
        val ready = _uiState.value as? PlanPreviewUiState.Ready ?: return
        viewModelScope.launch {
            val event = try {
                when (applyPlan(id, language, ready.timeMinutes, reminder, policy)) {
                    is ApplyPlanResult.Applied -> PlanApplyEvent.Applied
                    is ApplyPlanResult.Conflict -> PlanApplyEvent.Conflict
                    ApplyPlanResult.NotFound -> PlanApplyEvent.Unavailable
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                PlanApplyEvent.Failed(retry = { use(policy) })
            }
            _events.send(event)
        }
    }
}
