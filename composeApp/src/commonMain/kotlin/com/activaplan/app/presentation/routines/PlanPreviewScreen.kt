package com.activaplan.app.presentation.routines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.activaplan.app.designsystem.ActivaDimens
import com.activaplan.app.designsystem.ActivaPlanTheme
import com.activaplan.app.designsystem.components.ActivaBackButton
import com.activaplan.app.designsystem.components.ActivaButton
import com.activaplan.app.designsystem.components.ActivaCard
import com.activaplan.app.designsystem.components.ActivaDialogCard
import com.activaplan.app.designsystem.components.ActivaIcon
import com.activaplan.app.designsystem.components.ActivaIconKind
import com.activaplan.app.designsystem.components.ActivaSnackbarHost
import com.activaplan.app.designsystem.components.AlertType
import com.activaplan.app.designsystem.components.ButtonStyle
import com.activaplan.app.designsystem.components.LevelBadge
import com.activaplan.app.designsystem.components.TextBadge
import com.activaplan.app.designsystem.components.showActiva
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.model.ConflictPolicy
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.PlanDayDetail
import com.activaplan.app.domain.model.PlanDayType
import com.activaplan.app.domain.model.WeeklyPlanDetail
import com.activaplan.app.domain.time.LocalClock
import com.activaplan.app.domain.time.formatClock
import com.activaplan.app.domain.usecase.ApplyWeeklyPlanUseCase
import com.activaplan.app.domain.usecase.GetWeeklyPlanDetailUseCase
import com.activaplan.app.domain.usecase.ObserveSettingsUseCase
import com.activaplan.app.presentation.detail.Tips
import com.activaplan.app.presentation.schedule.TimePickerDialog
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlinx.datetime.isoDayNumber
import org.koin.compose.koinInject

/** Warna penanda jenis hari: kekuatan = primary, kardio = success, istirahat = redup. */
@Composable
internal fun planDayColor(type: PlanDayType): Color {
    val c = ActivaPlanTheme.colors
    return when (type) {
        PlanDayType.STRENGTH -> c.primary
        PlanDayType.CARDIO -> c.success
        PlanDayType.REST -> c.border
    }
}

/**
 * Pratinjau rencana mingguan dari daftar Rutinitas. "Pakai Rencana Ini" membuat rutinitas per hari latihan dan
 * mengisi Jadwal pada jam yang dipilih; [onApplied] dipanggil setelah berhasil.
 */
@Composable
fun PlanPreviewScreen(
    id: String,
    onBack: () -> Unit,
    onApplied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val getDetail = koinInject<GetWeeklyPlanDetailUseCase>()
    val applyPlan = koinInject<ApplyWeeklyPlanUseCase>()
    val observeSettings = koinInject<ObserveSettingsUseCase>()
    val clock = koinInject<LocalClock>()
    val strings = ActivaPlanTheme.strings
    val viewModel = viewModel(key = "plan-$id-${strings.language.code}") {
        PlanPreviewViewModel(id, strings.language, getDetail, applyPlan, observeSettings, clock)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var conflict by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel, strings) {
        viewModel.events.collect { event ->
            when (event) {
                PlanApplyEvent.Applied -> onApplied()
                PlanApplyEvent.Conflict -> conflict = true
                PlanApplyEvent.Unavailable -> snackbarHostState.showActiva(strings[StringKey.PLAN_NOT_FOUND], AlertType.WARNING)
                is PlanApplyEvent.Failed -> {
                    val retry = snackbarHostState.showActiva(
                        strings[StringKey.ALERT_SAVE_FAILED], AlertType.ERROR, strings[StringKey.ACTION_RETRY],
                    )
                    if (retry) event.retry()
                }
            }
        }
    }
    PlanPreviewContent(
        state, onBack, { viewModel.use() }, { pickingTime = true }, viewModel::reload, snackbarHostState, modifier,
    )

    (state as? PlanPreviewUiState.Ready)?.let { ready ->
        if (pickingTime) {
            TimePickerDialog(
                initialMinutes = ready.timeMinutes,
                onConfirm = { viewModel.onTime(it); pickingTime = false },
                onDismiss = { pickingTime = false },
            )
        }
    }
    if (conflict) {
        // Menyentuh luar dialog membatalkan; Ganti dan Tambah sama-sama memakai rencana.
        Dialog(onDismissRequest = { conflict = false }) {
            ActivaDialogCard(
                title = strings[StringKey.SCHEDULE_CONFLICT_TITLE],
                message = strings[StringKey.PLAN_CONFLICT_BODY],
                confirmLabel = strings[StringKey.SCHEDULE_CONFLICT_REPLACE],
                onConfirm = { conflict = false; viewModel.use(ConflictPolicy.REPLACE) },
                dismissLabel = strings[StringKey.SCHEDULE_CONFLICT_ADD],
                onDismiss = { conflict = false; viewModel.use(ConflictPolicy.KEEP_BOTH) },
            )
        }
    }
}

@Composable
fun PlanPreviewContent(
    state: PlanPreviewUiState,
    onBack: () -> Unit,
    onUse: () -> Unit,
    onPickTime: () -> Unit,
    onReload: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val strings = ActivaPlanTheme.strings
    Box(modifier.fillMaxSize().background(ActivaPlanTheme.colors.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(horizontal = ActivaDimens.screenPadding).padding(top = 8.dp, bottom = 12.dp)) {
                ActivaBackButton(onBack)
            }
            when (state) {
                PlanPreviewUiState.Loading -> Unit
                PlanPreviewUiState.Error -> Message(strings[StringKey.ALERT_LOAD_FAILED], strings[StringKey.ACTION_RELOAD], onReload)
                PlanPreviewUiState.NotFound -> Message(strings[StringKey.PLAN_NOT_FOUND])
                is PlanPreviewUiState.Ready -> ReadyContent(state, onUse, onPickTime)
            }
        }
        ActivaSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 160.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ReadyContent(state: PlanPreviewUiState.Ready, onUse: () -> Unit, onPickTime: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val detail = state.detail
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ActivaDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(detail.name, style = ActivaPlanTheme.type.largeTitle, color = c.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LevelBadge(detail.difficulty)
            if (detail.modes.isNotEmpty()) TextBadge(strings.format(StringKey.PLAN_NEEDS, detail.modes.joinToString(", ") { strings.label(it) }))
        }
        Text(detail.summary, style = ActivaPlanTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = c.text)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(detail.trainingDays.toString(), strings[StringKey.PLAN_STAT_TRAINING], Modifier.weight(1f))
            StatTile(detail.weeklyMinutes.toString(), strings[StringKey.PLAN_STAT_MINUTES], Modifier.weight(1f))
            StatTile(detail.restDays.toString(), strings[StringKey.PLAN_STAT_REST], Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text(strings[StringKey.PLAN_WEEK_TITLE], style = ActivaPlanTheme.type.cardTitle, color = c.text)
        detail.days.forEach { DayCard(it) }
        if (detail.tips.isNotEmpty()) Tips(detail.tips)
        Spacer(Modifier.height(12.dp))
    }
    Surface(color = c.surface, border = BorderStroke(1.dp, c.border), shape = RectangleShape) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = ActivaDimens.screenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings[StringKey.PLAN_TIME], style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 15.sp), color = c.text, modifier = Modifier.weight(1f))
                Text(formatClock(state.timeMinutes), style = ActivaPlanTheme.type.cardTitle, color = c.text)
                Spacer(Modifier.width(12.dp))
                ActivaButton(strings[StringKey.PLAN_TIME_CHANGE], onPickTime, style = ButtonStyle.SECONDARY, compact = true)
            }
            state.firstSession?.let { first ->
                Text(
                    strings.format(
                        StringKey.PLAN_FIRST_SESSION,
                        strings.dayName(first.dayOfWeek.isoDayNumber),
                        first.date.day,
                        strings.monthName(first.date.month.ordinal + 1),
                        formatClock(first.hour * 60 + first.minute),
                    ),
                    style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 14.sp),
                    color = c.text,
                )
            }
            Text(
                strings[StringKey.PLAN_REPEAT_NOTE],
                style = ActivaPlanTheme.type.body.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = c.textMuted,
            )
            ActivaButton(strings[StringKey.PLAN_USE], onUse, Modifier.fillMaxWidth(), icon = ActivaIconKind.PLUS)
            Text(
                strings.format(StringKey.PLAN_USE_NOTE, detail.trainingDays),
                style = ActivaPlanTheme.type.body.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = c.textMuted,
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val c = ActivaPlanTheme.colors
    Surface(color = c.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, c.border), modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = ActivaPlanTheme.type.largeTitle.copy(fontSize = 24.sp, lineHeight = 30.sp), color = c.text)
            Text(label, style = ActivaPlanTheme.type.labelSmall.copy(fontSize = 12.sp), color = c.textMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DayCard(day: PlanDayDetail) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    ActivaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(planDayColor(day.type), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                strings.dayName(day.dayOfWeek),
                style = ActivaPlanTheme.type.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = c.textMuted,
                modifier = Modifier.weight(1f),
            )
            TextBadge(strings.label(day.type))
        }
        Spacer(Modifier.height(6.dp))
        Text(day.name, style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 15.sp), color = c.text)
        val detailText = if (day.type == PlanDayType.REST) day.note.orEmpty() else day.itemNames.joinToString(" · ")
        if (detailText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(detailText, style = ActivaPlanTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = c.textMuted)
        }
        if (day.minutes > 0) {
            Spacer(Modifier.height(4.dp))
            Text(strings.duration(day.minutes * 60), style = ActivaPlanTheme.type.labelSmall, color = c.textMuted)
        }
    }
}

@Composable
private fun Message(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val c = ActivaPlanTheme.colors
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

private val previewDetail = WeeklyPlanDetail(
    id = "plan_easy_mix",
    name = "Mudah · Campur 5 Hari",
    difficulty = Difficulty.EASY,
    summary = "Tiga hari latihan kekuatan tanpa alat selang-seling dengan dua hari kardio ringan, lalu Sabtu dan Minggu istirahat.",
    tips = listOf("Fokus pada bentuk gerakan, bukan jumlah ulangan."),
    days = listOf(
        PlanDayDetail(1, PlanDayType.STRENGTH, "Mudah Campur · Tubuh Penuh A", null, listOf("Jalan di Tempat", "Push-up Lutut", "Squat"), 22),
        PlanDayDetail(2, PlanDayType.CARDIO, "Mudah Campur · Jalan Cepat", null, listOf("Jalan Cepat"), 30),
        PlanDayDetail(3, PlanDayType.STRENGTH, "Mudah Campur · Tubuh Penuh B", null, listOf("Putaran Lengan", "Push-up Dinding"), 24),
        PlanDayDetail(4, PlanDayType.CARDIO, "Mudah Campur · Jalan-Lari", null, listOf("Jalan-Lari Pemula"), 34),
        PlanDayDetail(5, PlanDayType.STRENGTH, "Mudah Campur · Tubuh Penuh C", null, listOf("Squat", "Superman"), 24),
        PlanDayDetail(6, PlanDayType.REST, "Istirahat aktif", "Jalan santai 15-20 menit dan peregangan ringan.", emptyList(), 0),
        PlanDayDetail(7, PlanDayType.REST, "Istirahat penuh", "Tidur cukup, makan teratur, dan minum air.", emptyList(), 0),
    ),
    modes = emptyList<Mode>(),
)

@Composable
private fun PlanPreview(darkTheme: Boolean) = ActivaPlanTheme(darkTheme = darkTheme) {
    PlanPreviewContent(PlanPreviewUiState.Ready(previewDetail, 16 * 60 + 30), {}, {}, {}, {}, remember { SnackbarHostState() })
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PlanPreviewLightPreview() = PlanPreview(false)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PlanPreviewDarkPreview() = PlanPreview(true)
