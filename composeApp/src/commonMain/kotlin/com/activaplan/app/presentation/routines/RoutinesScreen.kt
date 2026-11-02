package com.activaplan.app.presentation.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.activaplan.app.designsystem.ActivaDimens
import com.activaplan.app.designsystem.ActivaPlanTheme
import com.activaplan.app.designsystem.components.ActivaButton
import com.activaplan.app.designsystem.components.ActivaCard
import com.activaplan.app.designsystem.components.ActivaDialog
import com.activaplan.app.designsystem.components.ActivaIcon
import com.activaplan.app.designsystem.components.ActivaIconKind
import com.activaplan.app.designsystem.components.ActivaSegmentedControl
import com.activaplan.app.designsystem.components.ActivaSnackbarHost
import com.activaplan.app.designsystem.components.AlertType
import com.activaplan.app.designsystem.components.ButtonStyle
import com.activaplan.app.designsystem.components.LevelBadge
import com.activaplan.app.designsystem.components.TextBadge
import com.activaplan.app.designsystem.components.showActiva
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.i18n.Strings
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.PlanDayType
import com.activaplan.app.domain.model.PlanSummary
import com.activaplan.app.domain.model.ProgramSummary
import com.activaplan.app.domain.model.RoutineSummary
import com.activaplan.app.domain.model.RoutinesData
import com.activaplan.app.domain.model.ScheduleSlot
import com.activaplan.app.domain.model.TemplateSummary
import com.activaplan.app.domain.time.formatClock
import com.activaplan.app.domain.usecase.CreateRoutineFromTemplateUseCase
import com.activaplan.app.domain.usecase.DeleteRoutineUseCase
import com.activaplan.app.domain.usecase.ObserveRoutinesUseCase
import com.activaplan.app.presentation.ScreenListSkeleton
import com.activaplan.app.presentation.rememberShowSkeleton
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

/** Aksi layar Rutinitas, dipisah dari tampilan agar pratinjau tidak perlu ViewModel. */
class RoutinesActions(
    val onCreate: () -> Unit,
    val onEdit: (String) -> Unit,
    val onStart: (String) -> Unit,
    val onRequestDelete: (RoutineSummary) -> Unit,
    val onUseTemplate: (String) -> Unit,
    val onOpenTemplate: (String) -> Unit,
    val onReload: () -> Unit,
    val onOpenPlan: (String) -> Unit = {},
    val onOpenProgram: (String) -> Unit = {},
)

private const val TAB_MINE = 0
private const val TAB_TEMPLATES = 1

/**
 * Daftar Rutinitas: rutinitas milik pengguna dan template siap pakai.
 * [onUseTemplate] dipanggil dengan ID rutinitas hasil salinan template; [onSavedNoticeShown] setelah snackbar "tersimpan" tampil.
 */
@Composable
fun RoutinesScreen(
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onStart: (String) -> Unit,
    onUseTemplate: (String) -> Unit,
    onOpenTemplate: (String) -> Unit,
    savedNotice: Boolean,
    onSavedNoticeShown: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPlan: (String) -> Unit = {},
    onOpenProgram: (String) -> Unit = {},
) {
    val observe = koinInject<ObserveRoutinesUseCase>()
    val delete = koinInject<DeleteRoutineUseCase>()
    val createFromTemplate = koinInject<CreateRoutineFromTemplateUseCase>()
    val strings = ActivaPlanTheme.strings
    val viewModel = viewModel(key = "routines-${strings.language.code}") {
        RoutinesViewModel(strings.language, observe, delete, createFromTemplate)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(TAB_MINE) }
    var deleteTarget by remember { mutableStateOf<RoutineSummary?>(null) }

    // Bendera dibersihkan sebelum snackbar tampil; efek tidak boleh dikunci pada bendera itu agar tidak dibatalkan.
    val savedNoticeState = rememberUpdatedState(savedNotice)
    val noticeShown = rememberUpdatedState(onSavedNoticeShown)
    LaunchedEffect(strings) {
        snapshotFlow { savedNoticeState.value }.filter { it }.collect {
            noticeShown.value()
            snackbarHostState.showActiva(strings[StringKey.ALERT_ROUTINE_SAVED])
        }
    }
    LaunchedEffect(viewModel, strings) {
        viewModel.events.collect { event ->
            when (event) {
                RoutinesEvent.Deleted -> snackbarHostState.showActiva(strings[StringKey.ALERT_ROUTINE_DELETED])
                is RoutinesEvent.TemplateCopied -> onUseTemplate(event.routineId)
                RoutinesEvent.TemplateUnavailable ->
                    snackbarHostState.showActiva(strings[StringKey.TEMPLATE_NOT_FOUND], AlertType.WARNING)
                is RoutinesEvent.Failed -> {
                    val retry = snackbarHostState.showActiva(
                        strings[StringKey.ALERT_SAVE_FAILED], AlertType.ERROR, strings[StringKey.ACTION_RETRY],
                    )
                    if (retry) event.retry()
                }
            }
        }
    }

    val actions = RoutinesActions(
        onCreate, onEdit, onStart, { deleteTarget = it }, viewModel::useTemplate, onOpenTemplate, viewModel::reload, onOpenPlan, onOpenProgram,
    )
    RoutinesContent(state, tab, { tab = it }, actions, snackbarHostState, modifier)

    deleteTarget?.let { target ->
        ActivaDialog(
            title = strings[StringKey.ALERT_DELETE_ROUTINE_TITLE],
            message = strings[StringKey.ALERT_DELETE_ROUTINE_BODY],
            confirmLabel = strings[StringKey.ACTION_DELETE],
            onConfirm = { deleteTarget = null; viewModel.delete(target.id) },
            dismissLabel = strings[StringKey.ACTION_CANCEL],
            onDismiss = { deleteTarget = null },
            confirmIcon = ActivaIconKind.TRASH,
        )
    }
}

@Composable
fun RoutinesContent(
    state: RoutinesUiState,
    tab: Int,
    onTab: (Int) -> Unit,
    actions: RoutinesActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(ActivaPlanTheme.colors.bg).statusBarsPadding()) {
        if (rememberShowSkeleton(state == RoutinesUiState.Loading, state is RoutinesUiState.Ready)) ScreenListSkeleton() else when (state) {
            RoutinesUiState.Loading -> Unit
            RoutinesUiState.Error -> LoadError(actions.onReload)
            is RoutinesUiState.Ready -> ReadyContent(state.data, tab, onTab, actions)
        }
        ActivaSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
    }
}

@Composable
private fun ReadyContent(data: RoutinesData, tab: Int, onTab: (Int) -> Unit, actions: RoutinesActions) {
    val strings = ActivaPlanTheme.strings
    Column(Modifier.fillMaxSize().padding(horizontal = ActivaDimens.screenPadding)) {
        Spacer(Modifier.height(16.dp))
        Header(actions.onCreate)
        Spacer(Modifier.height(16.dp))
        ActivaSegmentedControl(
            options = listOf(
                strings.format(StringKey.ROUTINES_TAB_MINE, data.routines.size),
                strings.format(StringKey.ROUTINES_TAB_TEMPLATES, data.templates.size + data.plans.size),
            ),
            selectedIndex = tab,
            onSelect = onTab,
        )
        Spacer(Modifier.height(16.dp))
        if (tab == TAB_MINE) {
            if (data.routines.isEmpty()) {
                EmptyState({ onTab(TAB_TEMPLATES) }, actions.onCreate)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(data.routines, key = { it.id }) { RoutineCard(it, actions) }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (data.programs.isNotEmpty()) {
                    item(key = "programs-title") {
                        Text(
                            strings[StringKey.PROGRAM_SECTION_TITLE],
                            style = ActivaPlanTheme.type.cardTitle,
                            color = ActivaPlanTheme.colors.text,
                        )
                    }
                    items(data.programs, key = { it.id }) { ProgramCard(it, actions) }
                }
                if (data.plans.isNotEmpty()) {
                    item(key = "plans-title") {
                        Text(
                            strings[StringKey.PLAN_SECTION_TITLE],
                            style = ActivaPlanTheme.type.cardTitle,
                            color = ActivaPlanTheme.colors.text,
                        )
                    }
                    items(data.plans, key = { it.id }) { PlanCard(it, actions) }
                }
                item(key = "templates-title") {
                    Text(
                        strings[StringKey.ROUTINES_TEMPLATES_TITLE],
                        style = ActivaPlanTheme.type.cardTitle,
                        color = ActivaPlanTheme.colors.text,
                    )
                }
                if (data.templates.isEmpty()) {
                    item {
                        Text(strings[StringKey.ROUTINES_TEMPLATES_EMPTY], style = ActivaPlanTheme.type.body, color = ActivaPlanTheme.colors.textMuted)
                    }
                }
                items(data.templates, key = { it.id }) { TemplateCard(it, actions) }
            }
        }
    }
}

@Composable
private fun Header(onCreate: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val addLabel = strings[StringKey.ROUTINE_NEW]
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            strings[StringKey.NAV_ROUTINES],
            style = ActivaPlanTheme.type.screenTitle,
            color = c.text,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.size(ActivaDimens.minTouchTarget)
                .background(c.primary, CircleShape)
                .semantics { contentDescription = addLabel }
                .clickable(role = Role.Button, onClick = onCreate),
            contentAlignment = Alignment.Center,
        ) {
            ActivaIcon(ActivaIconKind.PLUS, c.onPrimary, size = 22.dp)
        }
    }
}

@Composable
private fun RoutineCard(routine: RoutineSummary, actions: RoutinesActions) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val deleteLabel = strings[StringKey.ROUTINES_DELETE]
    ActivaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(routine.name, style = ActivaPlanTheme.type.cardTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                routine.level?.let { LevelBadge(it) }
            }
            Box(
                Modifier.size(ActivaDimens.minTouchTarget)
                    .semantics { contentDescription = "$deleteLabel: ${routine.name}" }
                    .clickable(role = Role.Button) { actions.onRequestDelete(routine) },
                contentAlignment = Alignment.Center,
            ) {
                ActivaIcon(ActivaIconKind.TRASH, c.textMuted, size = 20.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(strings.routineMeta(routine.exerciseCount, routine.estimatedMinutes), style = metaStyle(), color = c.textMuted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivaIcon(ActivaIconKind.CALENDAR, c.textMuted, size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(strings.scheduleText(routine.schedule), style = metaStyle(), color = c.textMuted)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActivaButton(
                strings[StringKey.ROUTINES_START], { actions.onStart(routine.id) }, Modifier.weight(1f),
                icon = ActivaIconKind.PLAY, compact = true,
            )
            ActivaButton(
                strings[StringKey.ROUTINES_EDIT], { actions.onEdit(routine.id) }, Modifier.weight(1f),
                style = ButtonStyle.SECONDARY, compact = true,
            )
        }
    }
}

/** Kartu program bertahap: nama, level, alat yang dibutuhkan, dan jumlah pekan dengan rentang menit per pekan. */
@Composable
private fun ProgramCard(program: ProgramSummary, actions: RoutinesActions) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val minutes = program.weeks.map { it.weeklyMinutes }
    ActivaCard(Modifier.fillMaxWidth(), onClick = { actions.onOpenProgram(program.id) }) {
        Text(program.name, style = ActivaPlanTheme.type.cardTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(program.difficulty)
            if (program.modes.isNotEmpty()) TextBadge(strings.format(StringKey.PLAN_NEEDS, program.modes.joinToString(", ") { strings.label(it) }))
        }
        Spacer(Modifier.height(8.dp))
        Text(program.summary, style = metaStyle(), color = c.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Text(
            strings.format(StringKey.PROGRAM_CARD_META, program.weeks.size, minutes.minOrNull() ?: 0, minutes.maxOrNull() ?: 0),
            style = metaStyle(),
            color = c.textMuted,
        )
    }
}

/** Kartu rencana mingguan: tujuh titik hari berwarna menurut jenisnya, ringkasan, dan alat yang dibutuhkan. */
@Composable
private fun PlanCard(plan: PlanSummary, actions: RoutinesActions) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val weekDescription = (1..7).zip(plan.days).joinToString(", ") { (day, type) -> "${strings.dayName(day)}: ${strings.label(type)}" }
    ActivaCard(Modifier.fillMaxWidth(), onClick = { actions.onOpenPlan(plan.id) }) {
        Text(plan.name, style = ActivaPlanTheme.type.cardTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.semantics { contentDescription = weekDescription }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            plan.days.forEachIndexed { index, type ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(12.dp).background(planDayColor(type), CircleShape))
                    Text(strings.dayShort(index + 1), style = ActivaPlanTheme.type.labelSmall.copy(fontSize = 10.sp), color = c.textMuted)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(plan.difficulty)
            if (plan.modes.isNotEmpty()) TextBadge(strings.format(StringKey.PLAN_NEEDS, plan.modes.joinToString(", ") { strings.label(it) }))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            strings.format(StringKey.PLAN_CARD_META, plan.trainingDays, plan.strengthDays, plan.cardioDays),
            style = metaStyle(),
            color = c.textMuted,
        )
        Text(strings.format(StringKey.PLAN_CARD_MINUTES, plan.weeklyMinutes), style = metaStyle(), color = c.textMuted)
    }
}

@Composable
private fun TemplateCard(template: TemplateSummary, actions: RoutinesActions) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    ActivaCard(Modifier.fillMaxWidth(), onClick = { actions.onOpenTemplate(template.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(template.name, style = ActivaPlanTheme.type.cardTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                LevelBadge(template.difficulty)
                Text(strings.routineMeta(template.exerciseCount, template.estimatedMinutes), style = metaStyle(), color = c.textMuted)
            }
            Spacer(Modifier.width(12.dp))
            ActivaButton(strings[StringKey.ROUTINES_USE], { actions.onUseTemplate(template.id) }, compact = true)
        }
    }
}

@Composable
private fun EmptyState(onUseTemplates: () -> Unit, onCreate: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ActivaIcon(ActivaIconKind.LIST, c.textMuted, size = 32.dp)
        Spacer(Modifier.height(12.dp))
        Text(strings[StringKey.ROUTINES_EMPTY_TITLE], style = ActivaPlanTheme.type.cardTitle, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        ActivaButton(strings[StringKey.ROUTINES_EMPTY_CREATE], onCreate, icon = ActivaIconKind.PLUS)
        Spacer(Modifier.height(10.dp))
        ActivaButton(strings[StringKey.ROUTINES_EMPTY_TEMPLATE], onUseTemplates, style = ButtonStyle.SOFT)
    }
}

@Composable
private fun LoadError(onReload: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Column(
        Modifier.fillMaxSize().padding(ActivaDimens.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ActivaIcon(ActivaIconKind.WARNING, c.textMuted, size = 32.dp)
        Spacer(Modifier.height(12.dp))
        Text(strings[StringKey.ALERT_LOAD_FAILED], style = ActivaPlanTheme.type.body, color = c.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        ActivaButton(strings[StringKey.ACTION_RELOAD], onReload, style = ButtonStyle.SOFT)
    }
}

@Composable
private fun metaStyle() = ActivaPlanTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp)

/** "5 latihan · ±42 mnt". */
internal fun Strings.routineMeta(exerciseCount: Int, minutes: Int): String =
    format(if (exerciseCount == 1) StringKey.ROUTINE_ITEM_ONE else StringKey.ROUTINE_ITEMS, exerciseCount) +
        " · " + format(StringKey.ROUTINES_MINUTES, minutes)

/** Jadwal paling awal dalam pekan ("Min 16.30"), plus "+n lagi" bila ada lebih dari satu. */
internal fun Strings.scheduleText(schedule: List<ScheduleSlot>): String {
    val first = schedule.firstOrNull() ?: return get(StringKey.ROUTINES_NOT_SCHEDULED)
    val base = "${dayShort(first.dayOfWeek)} ${formatClock(first.timeMinutes)}"
    return if (schedule.size > 1) base + " " + format(StringKey.ROUTINES_SCHEDULE_MORE, schedule.size - 1) else base
}

private val previewData = RoutinesData(
    routines = listOf(
        RoutineSummary("r1", "Board Pemula Full Body", Difficulty.EASY, 5, 42, listOf(ScheduleSlot(7, 990))),
        RoutineSummary("r2", "Dada & Triceps Sedang", Difficulty.MEDIUM, 4, 38, listOf(ScheduleSlot(1, 390), ScheduleSlot(3, 390))),
        RoutineSummary("r3", "Latihan Santai", null, 1, 5, emptyList()),
    ),
    templates = listOf(
        TemplateSummary("t1", "Board Pemula Full Body", Difficulty.EASY, 5, 42),
        TemplateSummary("t2", "Dada & Triceps Sedang", Difficulty.MEDIUM, 4, 38),
        TemplateSummary("t3", "Board Sulit Full Body", Difficulty.HARD, 6, 65),
    ),
    plans = listOf(
        PlanSummary(
            "plan_easy_mix", "Mudah · Campur 5 Hari", Difficulty.EASY,
            listOf(
                PlanDayType.STRENGTH, PlanDayType.CARDIO, PlanDayType.STRENGTH, PlanDayType.CARDIO,
                PlanDayType.STRENGTH, PlanDayType.REST, PlanDayType.REST,
            ),
            120, emptyList(),
        ),
    ),
)

private val previewActions = RoutinesActions({}, {}, {}, {}, {}, {}, {})

@Composable
private fun RoutinesPreview(darkTheme: Boolean, data: RoutinesData, tab: Int) = ActivaPlanTheme(darkTheme = darkTheme) {
    RoutinesContent(RoutinesUiState.Ready(data), tab, {}, previewActions, remember { SnackbarHostState() })
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun RoutinesLightPreview() = RoutinesPreview(false, previewData, TAB_MINE)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun RoutinesDarkPreview() = RoutinesPreview(true, previewData, TAB_MINE)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun RoutinesTemplatesLightPreview() = RoutinesPreview(false, previewData, TAB_TEMPLATES)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun RoutinesTemplatesDarkPreview() = RoutinesPreview(true, previewData, TAB_TEMPLATES)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun RoutinesEmptyLightPreview() = RoutinesPreview(false, previewData.copy(routines = emptyList()), TAB_MINE)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun RoutinesEmptyDarkPreview() = RoutinesPreview(true, previewData.copy(routines = emptyList()), TAB_MINE)
