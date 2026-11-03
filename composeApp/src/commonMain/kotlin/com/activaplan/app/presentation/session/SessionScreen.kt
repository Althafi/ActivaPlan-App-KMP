package com.activaplan.app.presentation.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.activaplan.app.designsystem.ActivaDimens
import com.activaplan.app.designsystem.ActivaPlanTheme
import com.activaplan.app.designsystem.ActivaShapes
import com.activaplan.app.designsystem.components.ActivaBackButton
import com.activaplan.app.designsystem.components.ActivaBottomSheet
import com.activaplan.app.designsystem.components.ActivaButton
import com.activaplan.app.designsystem.components.ActivaCard
import com.activaplan.app.designsystem.components.ActivaDialog
import com.activaplan.app.designsystem.components.ActivaIcon
import com.activaplan.app.designsystem.components.ActivaIconKind
import com.activaplan.app.designsystem.components.ActivaSnackbarHost
import com.activaplan.app.designsystem.components.AlertType
import com.activaplan.app.designsystem.components.ButtonStyle
import com.activaplan.app.designsystem.components.TextBadge
import com.activaplan.app.designsystem.components.ZoneChip
import com.activaplan.app.designsystem.components.showActiva
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.cardio.PaceGuide
import com.activaplan.app.domain.model.CardioSegment
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.SessionExercise
import com.activaplan.app.domain.model.SessionOverlay
import com.activaplan.app.domain.model.SessionPhase
import com.activaplan.app.domain.session.SessionState
import com.activaplan.app.domain.tracking.GpsStatus
import com.activaplan.app.domain.tracking.TrackingUi
import com.activaplan.app.domain.usecase.FinishSessionUseCase
import com.activaplan.app.domain.usecase.LoadSessionPlanUseCase
import com.activaplan.app.domain.usecase.ObserveSettingsUseCase
import com.activaplan.app.domain.cue.CuePlayer
import com.activaplan.app.domain.tracking.LocationTracker
import kotlinx.coroutines.flow.first
import com.activaplan.app.presentation.KeepScreenOn
import com.activaplan.app.presentation.PlatformBackHandler
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Aksi layar Sesi, dipisah dari tampilan agar pratinjau tidak perlu ViewModel. */
class SessionActions(
    val onClose: () -> Unit,
    val onTogglePause: () -> Unit,
    val onCountRep: () -> Unit,
    val onCompleteSet: () -> Unit,
    val onSkip: () -> Unit,
    val onAddRest: () -> Unit,
    val onSkipRest: () -> Unit,
    val onConfirmSafety: () -> Unit,
    val onRetry: () -> Unit,
    val onRegress: () -> Unit,
    val onSkipExercise: () -> Unit,
    val onDismissStop: () -> Unit,
    val onConfirmStop: () -> Unit,
    val onDone: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onReload: () -> Unit,
)

/**
 * Sesi latihan untuk rutinitas [routineId]: cek keselamatan (bila ada latihan tali), latihan, istirahat, lalu ringkasan.
 * [onExit] menutup layar; [onOpenHistory] dari layar Selesai.
 */
@Composable
fun SessionScreen(
    routineId: String,
    onExit: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadPlan = koinInject<LoadSessionPlanUseCase>()
    val finishSession = koinInject<FinishSessionUseCase>()
    val tracker = koinInject<LocationTracker>()
    val cues = koinInject<CuePlayer>()
    val observeSettings = koinInject<ObserveSettingsUseCase>()
    val strings = ActivaPlanTheme.strings
    // Tanpa bahasa di kunci: mengganti bahasa tidak boleh mengulang sesi yang sedang berjalan.
    val language = strings.language
    val viewModel = viewModel(key = "session-$routineId") {
        SessionViewModel(
            routineId, language, loadPlan, finishSession,
            tracker = tracker,
            cues = cues,
            loadSettings = { observeSettings().first() },
            trackingTitle = strings[StringKey.SESSION_GPS_NOTIFICATION],
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(viewModel, strings) {
        viewModel.events.collect { event ->
            // Snackbar jalan di coroutine sendiri agar Exit dan set berikutnya tidak menunggu snackbar sebelumnya.
            when (event) {
                is SessionEvent.SetCompleted -> {
                    // Tick paling ringan yang tersedia di semua platform.
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (event.restSeconds > 0) {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        launch {
                            snackbarHostState.showActiva(
                                strings.format(StringKey.ALERT_SET_DONE, event.setNumber, event.totalSets, event.restSeconds),
                            )
                        }
                    }
                }
                SessionEvent.ExercisesSkipped -> launch {
                    snackbarHostState.showActiva(strings[StringKey.ALERT_EXERCISE_MISSING], AlertType.WARNING)
                }
                is SessionEvent.SaveFailed -> launch {
                    val retry = snackbarHostState.showActiva(
                        strings[StringKey.ALERT_SAVE_FAILED], AlertType.ERROR, strings[StringKey.ACTION_RETRY],
                    )
                    if (retry) event.retry()
                }
                SessionEvent.Exit -> onExit()
            }
        }
    }

    val ready = state as? SessionUiState.Ready
    // Layar tidak boleh padam selama sesi berjalan; setelah selesai kembali normal.
    KeepScreenOn(enabled = ready != null && ready.state.phase !is SessionPhase.Finished)
    PlatformBackHandler(enabled = ready != null, onBack = viewModel::close)

    val actions = SessionActions(
        onClose = if (ready != null) viewModel::close else onExit,
        onTogglePause = viewModel::togglePause,
        onCountRep = viewModel::countRep,
        onCompleteSet = viewModel::completeSet,
        onSkip = viewModel::skip,
        onAddRest = viewModel::addRest,
        onSkipRest = viewModel::skipRest,
        onConfirmSafety = viewModel::confirmSafety,
        onRetry = viewModel::retry,
        onRegress = viewModel::regress,
        onSkipExercise = viewModel::skipExercise,
        onDismissStop = viewModel::dismissStop,
        onConfirmStop = viewModel::confirmStop,
        onDone = onExit,
        onOpenHistory = onOpenHistory,
        onReload = viewModel::reload,
    )
    SessionContent(state, actions, snackbarHostState, modifier)
}

@Composable
fun SessionContent(
    state: SessionUiState,
    actions: SessionActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val strings = ActivaPlanTheme.strings
    Box(modifier.fillMaxSize().background(ActivaPlanTheme.colors.bg)) {
        when (state) {
            SessionUiState.Loading -> BackOnly(actions.onClose)
            SessionUiState.Error ->
                Message(strings[StringKey.ALERT_LOAD_FAILED], actions.onClose, strings[StringKey.ACTION_RELOAD], actions.onReload)
            SessionUiState.NotFound -> Message(strings[StringKey.SESSION_NOT_FOUND], actions.onClose)
            is SessionUiState.Ready -> ReadyContent(state, actions)
        }
        ActivaSnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 160.dp),
        )
    }
}

@Composable
private fun ReadyContent(ready: SessionUiState.Ready, actions: SessionActions) {
    val state = ready.state
    // Detail gerakan dari kartu di layar latihan atau istirahat; sheet menjeda timer selama terbuka.
    var detail by remember { mutableStateOf<SessionExercise?>(null) }
    when (val phase = state.phase) {
        SessionPhase.SafetyCheck -> SafetyCheckContent(state, actions)
        is SessionPhase.Working -> WorkingContent(state, phase, actions, ready.tracking, ready.age, onShowDetail = { detail = it })
        is SessionPhase.Resting -> RestContent(state, phase, actions, onShowDetail = { detail = it })
        is SessionPhase.Finished -> if (phase.showsSummary) {
            CompleteContent(
                phase.summary.copy(distanceMeters = ready.tracking.stats.roundedMeters),
                ready.streak,
                actions,
                averagePaceSecPerKm = ready.tracking.stats.averagePaceSecPerKm,
            )
        }
    }
    detail?.let { ExerciseDetailSheet(it, state.paused, actions.onTogglePause) { detail = null } }
    when (state.overlay) {
        SessionOverlay.NONE -> Unit
        // Menutup sheet tanpa memilih sama dengan Coba lagi: pengguna tetap di set yang sama.
        SessionOverlay.FAILED -> ActivaBottomSheet(onDismiss = actions.onRetry) {
            FailedSheetContent(state.regression, actions)
        }
        SessionOverlay.CONFIRM_STOP -> {
            val strings = ActivaPlanTheme.strings
            ActivaDialog(
                title = strings[StringKey.ALERT_STOP_TITLE],
                message = strings[StringKey.ALERT_STOP_BODY],
                confirmLabel = strings[StringKey.ACTION_STOP],
                onConfirm = actions.onConfirmStop,
                dismissLabel = strings[StringKey.ACTION_CONTINUE],
                onDismiss = actions.onDismissStop,
            )
        }
    }
}

// region Latihan

@Composable
private fun WorkingContent(
    state: SessionState,
    phase: SessionPhase.Working,
    actions: SessionActions,
    tracking: TrackingUi = TrackingUi(),
    age: Int? = null,
    onShowDetail: (SessionExercise) -> Unit = {},
) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val item = state.current
    val exercise = item.exercise
    val step = item.cardio
    SessionLayout(
        top = { TopBar(state, actions) },
        bottom = {
            if (step != null) {
                // Segmen kardio berpindah sendiri; tombol ini hanya mempercepat. Tidak ada "Lewati" terpisah.
                ActivaButton(
                    strings[StringKey.SESSION_NEXT_SEGMENT], actions.onCompleteSet, Modifier.fillMaxWidth(),
                    enabled = !state.paused, icon = ActivaIconKind.SKIP_NEXT,
                )
                PauseButton(state.paused, actions.onTogglePause, Modifier.fillMaxWidth())
            } else {
                ActivaButton(
                    strings[StringKey.SESSION_FINISH_SET], actions.onCompleteSet, Modifier.fillMaxWidth(),
                    enabled = !state.paused, icon = ActivaIconKind.CHECK,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActivaButton(
                        strings[StringKey.SESSION_SKIP], actions.onSkip, Modifier.weight(1f), ButtonStyle.SECONDARY,
                        enabled = !state.paused, icon = ActivaIconKind.SKIP_NEXT,
                    )
                    PauseButton(state.paused, actions.onTogglePause, Modifier.weight(1f))
                }
            }
        },
    ) {
        if (step != null) {
            TextBadge(strings.label(step.segment.kind))
            Text(
                strings.segmentName(step.segment),
                style = ActivaPlanTheme.type.largeTitle.copy(fontSize = 26.sp), color = c.text, textAlign = TextAlign.Center,
            )
            Text(
                strings.format(StringKey.SESSION_CARDIO_SUBTITLE, step.workoutName, step.segment.rpe),
                style = ActivaPlanTheme.type.body,
                color = c.textMuted,
            )
        } else {
            ZoneOrCategory(exercise)
            Text(exercise.name, style = ActivaPlanTheme.type.largeTitle.copy(fontSize = 26.sp), color = c.text, textAlign = TextAlign.Center)
            Text(
                strings.format(StringKey.SESSION_SET_PROGRESS, state.setNumber, item.sets),
                style = ActivaPlanTheme.type.body,
                color = c.textMuted,
            )
        }
        if (item.isTimed) {
            val totalMillis = (item.durationSeconds ?: 0) * 1000L
            val remaining = phase.remainingMillis ?: 0L
            ProgressRing(
                progress = if (totalMillis > 0) remaining.toFloat() / totalMillis else 0f,
                dimmed = state.paused,
                modifier = Modifier.fillMaxWidth(RING_WIDTH_FRACTION).widthIn(max = RING_MAX_SIZE),
            ) {
                RingNumber(secondsCeil(remaining), strings[StringKey.SESSION_SECONDS_LEFT])
            }
        } else {
            val target = item.reps ?: 0
            val description = "${phase.repsDone} ${strings.format(StringKey.SESSION_REPS_OF, target)}"
            ProgressRing(
                progress = if (target > 0) phase.repsDone.toFloat() / target else 0f,
                dimmed = state.paused,
                modifier = Modifier.fillMaxWidth(RING_WIDTH_FRACTION).widthIn(max = RING_MAX_SIZE)
                    .clip(CircleShape)
                    .semantics { contentDescription = description }
                    .clickable(
                        enabled = !state.paused,
                        role = Role.Button,
                        onClickLabel = strings[StringKey.SESSION_COUNT_REP],
                        onClick = actions.onCountRep,
                    ),
            ) {
                RingNumber(phase.repsDone, strings.format(StringKey.SESSION_REPS_OF, target))
            }
        }
        if (state.paused) {
            Text(strings[StringKey.SESSION_PAUSED], style = ActivaPlanTheme.type.label, color = c.textMuted)
        }
        if (step != null) {
            CardioGuide(step.segment, tracking, age)
            state.exercises.getOrNull(state.index + 1)?.let { UpNextCard(it, onShowDetail) }
        } else {
            cue(exercise, state.setNumber)?.let { CueCard(it) { onShowDetail(item) } }
        }
    }
}

/**
 * Panduan segmen kardio: zona detak jantung target (bpm bila usia diketahui) dan, bila GPS menyala, target pace
 * gerakan itu serta jarak dan pace saat ini. Pace hanya acuan umum; usaha (RPE) yang utama.
 */
@Composable
private fun CardioGuide(segment: CardioSegment, tracking: TrackingUi, age: Int?) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val lines = buildList {
        add(strings.heartRateLine(segment.rpe, age))
        when (tracking.status) {
            GpsStatus.OFF -> Unit
            GpsStatus.SEARCHING -> add(strings[StringKey.SESSION_GPS_SEARCHING])
            GpsStatus.ACTIVE -> {
                val target = PaceGuide.range(segment.motion)
                add(strings.format(StringKey.SESSION_TARGET_PACE, PaceGuide.format(target.first), PaceGuide.format(target.last)))
                val distance = strings.distance(tracking.stats.roundedMeters)
                val pace = tracking.stats.currentPaceSecPerKm
                add(
                    if (pace != null) strings.format(StringKey.SESSION_GPS_METRICS, distance, strings.pace(pace))
                    else strings.format(StringKey.SESSION_GPS_NO_PACE, distance),
                )
            }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { Text(it, style = ActivaPlanTheme.type.body, color = c.textMuted, textAlign = TextAlign.Center) }
    }
}

/** Segmen atau latihan yang menyusul, supaya pelari tahu perubahan sebelum bunyi/getar datang. */
@Composable
private fun UpNextCard(next: SessionExercise, onShowDetail: (SessionExercise) -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    // Segmen kardio tidak punya langkah atau tips, jadi hanya latihan biasa yang bisa dibuka detailnya.
    ActivaCard(Modifier.fillMaxWidth(), onClick = if (next.cardio == null) ({ onShowDetail(next) }) else null) {
        Text(
            strings[StringKey.SESSION_REST_UP_NEXT],
            style = ActivaPlanTheme.type.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = c.textMuted,
        )
        Spacer(Modifier.height(4.dp))
        val step = next.cardio
        Text(
            if (step != null) strings.segmentName(step.segment) else next.exercise.name,
            style = ActivaPlanTheme.type.cardTitle,
            color = c.text,
        )
        Text(
            if (step != null) strings.duration(step.segment.durationSeconds) else strings.summary(next.exercise),
            style = ActivaPlanTheme.type.body,
            color = c.textMuted,
        )
        if (step == null) DetailHint()
    }
}

/** Satu petunjuk dari tips lalu langkah; berganti tiap set agar tidak membosankan. Null bila latihan tak punya keduanya. */
internal fun cue(exercise: Exercise, setNumber: Int): String? {
    val cues = exercise.tips + exercise.steps
    return if (cues.isEmpty()) null else cues[(setNumber - 1).coerceAtLeast(0) % cues.size]
}

/** Petunjuk singkat; menyentuh kartu membuka detail gerakan (langkah, tips, kesalahan umum). */
@Composable
private fun CueCard(text: String, onClick: () -> Unit) {
    val c = ActivaPlanTheme.colors
    ActivaCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ActivaIcon(ActivaIconKind.INFO, c.primary, size = 20.dp)
            Text(text, style = ActivaPlanTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = c.text, modifier = Modifier.weight(1f))
        }
        DetailHint()
    }
}

/** Ajakan kecil di kartu yang bisa dibuka menjadi detail gerakan. */
@Composable
private fun DetailHint() {
    Spacer(Modifier.height(8.dp))
    Text(
        ActivaPlanTheme.strings[StringKey.SESSION_EXERCISE_DETAIL],
        style = ActivaPlanTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
        color = ActivaPlanTheme.colors.primary,
    )
}

// endregion

// region Istirahat

@Composable
private fun RestContent(
    state: SessionState,
    phase: SessionPhase.Resting,
    actions: SessionActions,
    onShowDetail: (SessionExercise) -> Unit = {},
) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    SessionLayout(
        top = { TopBar(state, actions) },
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivaButton(
                    strings.format(StringKey.SESSION_REST_ADD, SessionState.REST_EXTENSION_SECONDS), actions.onAddRest,
                    Modifier.weight(1f), ButtonStyle.SECONDARY, enabled = !state.paused, icon = ActivaIconKind.PLUS,
                )
                ActivaButton(
                    strings[StringKey.SESSION_REST_SKIP], actions.onSkipRest, Modifier.weight(1f),
                    enabled = !state.paused, icon = ActivaIconKind.SKIP_NEXT,
                )
            }
            PauseButton(state.paused, actions.onTogglePause, Modifier.fillMaxWidth())
        },
    ) {
        TextBadge(strings[StringKey.SESSION_REST_TITLE])
        Text(strings[StringKey.SESSION_REST_TITLE], style = ActivaPlanTheme.type.largeTitle.copy(fontSize = 26.sp), color = c.text)
        ProgressRing(
            progress = if (phase.totalMillis > 0) phase.remainingMillis.toFloat() / phase.totalMillis else 0f,
            dimmed = state.paused,
            modifier = Modifier.fillMaxWidth(RING_WIDTH_FRACTION).widthIn(max = RING_MAX_SIZE),
        ) {
            RingNumber(secondsCeil(phase.remainingMillis), strings[StringKey.DETAIL_UNIT_SECONDS])
        }
        if (state.paused) {
            Text(strings[StringKey.SESSION_PAUSED], style = ActivaPlanTheme.type.label, color = c.textMuted)
        }
        state.nextUp?.let { next ->
            val openable = next.exercise.cardio == null
            ActivaCard(Modifier.fillMaxWidth(), onClick = if (openable) ({ onShowDetail(next.exercise) }) else null) {
                Text(
                    strings[StringKey.SESSION_REST_UP_NEXT],
                    style = ActivaPlanTheme.type.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textMuted,
                )
                Spacer(Modifier.height(4.dp))
                Text(next.exercise.exercise.name, style = ActivaPlanTheme.type.cardTitle, color = c.text)
                Text(
                    strings.format(StringKey.SESSION_SET_PROGRESS, next.setNumber, next.exercise.sets),
                    style = ActivaPlanTheme.type.body,
                    color = c.textMuted,
                )
                if (openable) DetailHint()
            }
        }
    }
}

// endregion

// region Cek keselamatan

@Composable
private fun SafetyCheckContent(state: SessionState, actions: SessionActions) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.padding(horizontal = ActivaDimens.screenPadding).padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivaBackButton(actions.onClose)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ActivaDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(strings[StringKey.SESSION_SAFETY_TITLE], style = ActivaPlanTheme.type.largeTitle, color = c.text)
            Surface(color = c.primarySoft, shape = ActivaShapes.card, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActivaIcon(ActivaIconKind.INFO, c.primary, size = 22.dp)
                        Text(
                            strings[StringKey.ALERT_ROPE_CHECK],
                            style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 15.sp, lineHeight = 22.sp),
                            color = c.text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    state.plan.safetyNotes.forEach {
                        Text("• $it", style = ActivaPlanTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = c.text)
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = ActivaDimens.screenPadding, vertical = 14.dp)) {
            ActivaButton(
                strings[StringKey.SESSION_SAFETY_CONFIRM], actions.onConfirmSafety, Modifier.fillMaxWidth(),
                icon = ActivaIconKind.CHECK,
            )
        }
    }
}

// endregion

// region Sheet Gagal

/** Isi bottom sheet Gagal. [regression] null menyembunyikan "Turun ke ...". */
@Composable
fun FailedSheetContent(regression: Exercise?, actions: SessionActions) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(56.dp).background(c.errorSoft, CircleShape), contentAlignment = Alignment.Center) {
            ActivaIcon(ActivaIconKind.WARNING, c.error, size = 28.dp)
        }
        Text(
            strings[StringKey.SESSION_FAILED_TITLE],
            style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            color = c.text,
        )
        Text(
            strings[if (regression != null) StringKey.SESSION_FAILED_BODY else StringKey.SESSION_FAILED_BODY_NO_EASIER],
            style = ActivaPlanTheme.type.body,
            color = c.textMuted,
        )
        Spacer(Modifier.height(4.dp))
        ActivaButton(strings[StringKey.ACTION_RETRY], actions.onRetry, Modifier.fillMaxWidth(), icon = ActivaIconKind.REFRESH)
        if (regression != null) {
            ActivaButton(
                strings.format(StringKey.SESSION_FAILED_REGRESS, regression.name), actions.onRegress,
                Modifier.fillMaxWidth(), ButtonStyle.SECONDARY, icon = ActivaIconKind.ARROW_DOWN,
            )
        }
        NeutralButton(strings[StringKey.SESSION_FAILED_SKIP], actions.onSkipExercise, Modifier.fillMaxWidth())
    }
}

/** Tombol bisu berlatar surfaceVar, sesuai "Lewati latihan ini" di Figma. */
@Composable
private fun NeutralButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ActivaPlanTheme.colors
    Surface(
        color = c.surfaceVar,
        contentColor = c.text,
        shape = ActivaShapes.button,
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text, style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 15.sp), textAlign = TextAlign.Center)
        }
    }
}

// endregion

// region Bagian bersama

/**
 * Kerangka layar latihan dan istirahat: [top] dan [bottom] tetap di tepi, tengahnya bisa digulir
 * agar tetap terjangkau di layar pendek atau saat dirotasi.
 */
@Composable
private fun SessionLayout(
    top: @Composable () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        top()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = ActivaDimens.screenPadding).padding(top = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        Column(
            Modifier.padding(horizontal = ActivaDimens.screenPadding).padding(top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = bottom,
        )
    }
}

@Composable
private fun TopBar(state: SessionState, actions: SessionActions) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Column(Modifier.padding(horizontal = ActivaDimens.screenPadding).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleButton(ActivaIconKind.CLOSE, strings[StringKey.SESSION_CLOSE], actions.onClose)
            val cardioStep = state.current.cardio
            Text(
                if (cardioStep != null) {
                    strings.format(StringKey.SESSION_CARDIO_PROGRESS, cardioStep.workoutName, cardioStep.position, cardioStep.total)
                } else {
                    strings.format(StringKey.SESSION_EXERCISE_PROGRESS, state.groupIndex + 1, state.groupCount)
                },
                style = ActivaPlanTheme.type.cardTitle,
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            CircleButton(
                if (state.paused) ActivaIconKind.PLAY else ActivaIconKind.PAUSE,
                strings[if (state.paused) StringKey.SESSION_RESUME else StringKey.SESSION_PAUSE],
                actions.onTogglePause,
            )
        }
        // Banyak langkah (segmen kardio) membuat batang sempit, jadi jarak antarbatang dirapatkan.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (state.exercises.size > 10) 2.dp else 6.dp)) {
            repeat(state.exercises.size) { i ->
                Box(
                    Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i <= state.index) c.primary else c.surfaceVar),
                )
            }
        }
    }
}

@Composable
private fun CircleButton(icon: ActivaIconKind, label: String, onClick: () -> Unit) {
    val c = ActivaPlanTheme.colors
    Box(
        Modifier.size(ActivaDimens.minTouchTarget)
            .background(c.surface, CircleShape)
            .border(1.dp, c.border, CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ActivaIcon(icon, c.text, size = 22.dp)
    }
}

@Composable
private fun PauseButton(paused: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val strings = ActivaPlanTheme.strings
    ActivaButton(
        strings[if (paused) StringKey.SESSION_RESUME else StringKey.SESSION_PAUSE], onClick, modifier, ButtonStyle.SECONDARY,
        icon = if (paused) ActivaIconKind.PLAY else ActivaIconKind.PAUSE,
    )
}

@Composable
private fun ZoneOrCategory(exercise: Exercise) {
    val zone = exercise.boardZone
    if (zone != null) ZoneChip(zone) else TextBadge(ActivaPlanTheme.strings.label(exercise.category))
}

@Composable
private fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ActivaPlanTheme.colors
    val track = c.surfaceVar
    val arc = if (dimmed) c.textMuted else c.primary
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = RING_STROKE.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(arc, -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun RingNumber(value: Int, caption: String) {
    val c = ActivaPlanTheme.colors
    Text(value.toString(), style = ActivaPlanTheme.type.timer, color = c.text)
    Text(caption, style = ActivaPlanTheme.type.body, color = c.textMuted)
}

/** Detik sisa dibulatkan ke atas agar timer menampilkan 1 sampai tepat sebelum habis, bukan 0 lebih dulu. */
private fun secondsCeil(millis: Long): Int = ((millis + 999) / 1000).toInt()

@Composable
private fun BackOnly(onBack: () -> Unit) {
    ActivaBackButton(onBack, Modifier.statusBarsPadding().padding(start = ActivaDimens.screenPadding, top = 8.dp))
}

@Composable
private fun Message(text: String, onBack: () -> Unit, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val c = ActivaPlanTheme.colors
    BackOnly(onBack)
    Column(
        Modifier.fillMaxSize().padding(ActivaDimens.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ActivaIcon(ActivaIconKind.WARNING, c.textMuted, size = 32.dp)
        Spacer(Modifier.height(12.dp))
        Text(text, style = ActivaPlanTheme.type.body, color = c.textMuted, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            ActivaButton(actionLabel, onAction, style = ButtonStyle.SOFT)
        }
    }
}

private const val RING_WIDTH_FRACTION = 0.64f
private val RING_MAX_SIZE = 240.dp
private val RING_STROKE = 20.dp

// endregion
