package com.activaplan.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.activaplan.app.designsystem.ActivaDimens
import com.activaplan.app.designsystem.ActivaPlanTheme
import com.activaplan.app.designsystem.ActivaShapes
import com.activaplan.app.designsystem.components.ActivaButton
import com.activaplan.app.designsystem.components.ActivaCard
import com.activaplan.app.designsystem.components.ActivaIcon
import com.activaplan.app.designsystem.components.ActivaIconKind
import com.activaplan.app.designsystem.components.ActivaLogoTile
import com.activaplan.app.designsystem.components.ActivaSnackbarHost
import com.activaplan.app.designsystem.components.ButtonStyle
import com.activaplan.app.designsystem.components.LevelBadge
import com.activaplan.app.designsystem.components.showActiva
import com.activaplan.app.domain.i18n.Strings
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.GreetingPeriod
import com.activaplan.app.domain.model.HomeData
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.ProgramProgress
import com.activaplan.app.domain.model.SessionStatus
import com.activaplan.app.domain.model.StreakDay
import com.activaplan.app.domain.model.TemplateRecommendation
import com.activaplan.app.domain.model.TodayWorkout
import com.activaplan.app.domain.model.WeekStreak
import com.activaplan.app.domain.time.formatClock
import com.activaplan.app.domain.usecase.ObserveHomeUseCase
import com.activaplan.app.presentation.ScreenListSkeleton
import com.activaplan.app.presentation.rememberShowSkeleton
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

/**
 * Beranda: latihan terjadwal hari ini, streak pekan ini, dan rekomendasi template.
 * [onStartWorkout] dipanggil dengan jadwal yang tampil; `requiresSafetyCheck` menentukan apakah sesi diawali cek keselamatan.
 */
@Composable
fun HomeScreen(
    onOpenSchedule: () -> Unit,
    onStartWorkout: (TodayWorkout) -> Unit,
    onOpenTemplate: (String) -> Unit,
    modifier: Modifier = Modifier,
    dataClearedNotice: Boolean = false,
    onDataClearedNoticeShown: () -> Unit = {},
    onOpenPlan: (String) -> Unit = {},
    onOpenProgram: (String) -> Unit = {},
    onOpenHistory: () -> Unit = {},
) {
    val observeHome = koinInject<ObserveHomeUseCase>()
    val viewModel = viewModel { HomeViewModel(observeHome) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = ActivaPlanTheme.strings
    val snackbarHostState = remember { SnackbarHostState() }

    // Bendera dibersihkan sebelum snackbar tampil; efek tidak boleh dikunci pada bendera itu agar tidak dibatalkan.
    val noticeState = rememberUpdatedState(dataClearedNotice)
    val noticeShown = rememberUpdatedState(onDataClearedNoticeShown)
    LaunchedEffect(strings) {
        snapshotFlow { noticeState.value }.filter { it }.collect {
            noticeShown.value()
            snackbarHostState.showActiva(strings[StringKey.ALERT_ALL_DATA_DELETED])
        }
    }

    Box(modifier) {
        HomeContent(state, onOpenSchedule, onStartWorkout, onOpenTemplate, viewModel::reload,
            onOpenPlan = onOpenPlan, onOpenProgram = onOpenProgram, onOpenHistory = onOpenHistory,
        )
        ActivaSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
    }
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenSchedule: () -> Unit,
    onStartWorkout: (TodayWorkout) -> Unit,
    onOpenTemplate: (String) -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPlan: (String) -> Unit = {},
    onOpenProgram: (String) -> Unit = {},
    onOpenHistory: () -> Unit = {},
) {
    Box(modifier.fillMaxSize().background(ActivaPlanTheme.colors.bg).statusBarsPadding()) {
        if (rememberShowSkeleton(state == HomeUiState.Loading, state is HomeUiState.Ready)) ScreenListSkeleton(cardCount = 4) else when (state) {
            HomeUiState.Loading -> Unit
            HomeUiState.Error -> LoadError(onReload)
            is HomeUiState.Ready -> HomeReady(state.data, onOpenSchedule, onStartWorkout, onOpenTemplate, onOpenPlan, onOpenProgram, onOpenHistory)
        }
    }
}

@Composable
private fun HomeReady(
    data: HomeData,
    onOpenSchedule: () -> Unit,
    onStartWorkout: (TodayWorkout) -> Unit,
    onOpenTemplate: (String) -> Unit,
    onOpenPlan: (String) -> Unit,
    onOpenProgram: (String) -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = ActivaDimens.screenPadding).padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header(data.greeting, onOpenSchedule)
        val today = data.today
        if (today != null) TodayCard(today, onStartWorkout, onOpenHistory) else EmptyTodayCard(onOpenSchedule)
        data.program?.let { ProgramSection(it, onOpenPlan, onOpenProgram) }
        StreakSection(data.week)
        if (data.recommendations.isNotEmpty()) Recommendations(data.recommendations, onOpenTemplate)
    }
}

@Composable
private fun Header(greeting: GreetingPeriod, onOpenSchedule: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActivaLogoTile(size = 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "ActivaPlan",
                    style = ActivaPlanTheme.type.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
                    color = c.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(strings.greeting(greeting), style = ActivaPlanTheme.type.body, color = c.textMuted)
            Text(strings[StringKey.HOME_HEADLINE], style = bigTitle(), color = c.text)
        }
        Spacer(Modifier.width(12.dp))
        val bellLabel = strings[StringKey.NAV_SCHEDULE]
        Box(
            Modifier.size(ActivaDimens.minTouchTarget)
                .background(c.surface, CircleShape)
                .border(1.dp, c.border, CircleShape)
                .semantics { contentDescription = bellLabel }
                .clickable(role = Role.Button, onClick = onOpenSchedule),
            contentAlignment = Alignment.Center,
        ) {
            ActivaIcon(ActivaIconKind.BELL, c.text, size = 22.dp)
        }
    }
}

@Composable
private fun TodayCard(today: TodayWorkout, onStartWorkout: (TodayWorkout) -> Unit, onOpenHistory: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val eyebrow = buildString {
        if (today.locked) {
            append(strings[StringKey.HOME_NEXT]).append(" · ").append(strings.dayName(today.dayOfWeek))
        } else {
            append(strings[StringKey.HOME_TODAY])
        }
        append(" · ").append(formatClock(today.timeMinutes))
        if (today.passed) append(" · ").append(strings[StringKey.HOME_TODAY_PASSED])
    }.uppercase()
    Surface(color = c.primary, contentColor = c.onPrimary, shape = ActivaShapes.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                eyebrow,
                style = ActivaPlanTheme.type.label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                color = c.onPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(today.routineName, style = bigTitle(), color = c.onPrimary)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActivaIcon(ActivaIconKind.DUMBBELL, c.onPrimary, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(strings.exerciseCount(today.exerciseCount), style = ActivaPlanTheme.type.body, color = c.onPrimary)
                Spacer(Modifier.width(16.dp))
                ActivaIcon(ActivaIconKind.CLOCK, c.onPrimary, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    strings.format(StringKey.HOME_MINUTES, today.estimatedMinutes),
                    style = ActivaPlanTheme.type.body,
                    color = c.onPrimary,
                )
            }
            if (today.locked) {
                Spacer(Modifier.height(12.dp))
                Text(strings[StringKey.HOME_DONE_TODAY], style = ActivaPlanTheme.type.body, color = c.onPrimary)
            }
            Spacer(Modifier.height(20.dp))
            // Jadwal hari lain belum boleh dimulai; jam hanya pengingat, jadi jadwal hari ini selalu bisa dimulai.
            ActivaButton(
                text = if (today.locked) strings.format(StringKey.HOME_START_LOCKED, strings.dayName(today.dayOfWeek)) else strings[StringKey.HOME_START],
                onClick = { onStartWorkout(today) },
                modifier = Modifier.fillMaxWidth(),
                style = ButtonStyle.SECONDARY,
                enabled = !today.locked,
                icon = if (today.locked) ActivaIconKind.CLOCK else ActivaIconKind.PLAY,
            )
            // Riwayat hanya ditawarkan setelah ada sesi yang selesai hari ini.
            if (today.locked) {
                Spacer(Modifier.height(10.dp))
                ActivaButton(
                    text = strings[StringKey.HOME_VIEW_HISTORY],
                    onClick = onOpenHistory,
                    modifier = Modifier.fillMaxWidth(),
                    style = ButtonStyle.SECONDARY,
                )
            }
        }
    }
}

/** Program aktif: nama, pekan ke berapa, penanda kemajuan, dan ajakan lanjut ke pekan berikutnya setelah tujuh hari. */
@Composable
private fun ProgramSection(program: ProgramProgress, onOpenPlan: (String) -> Unit, onOpenProgram: (String) -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val progress = strings.format(StringKey.HOME_PROGRAM_WEEK, program.week, program.totalWeeks)
    ActivaCard(Modifier.fillMaxWidth()) {
        Text(
            strings[StringKey.HOME_PROGRAM_EYEBROW].uppercase(),
            style = ActivaPlanTheme.type.label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
            color = c.textMuted,
        )
        Spacer(Modifier.height(6.dp))
        Text(program.programName, style = ActivaPlanTheme.type.cardTitle, color = c.text)
        Text(program.weekTitle, style = ActivaPlanTheme.type.body, color = c.textMuted)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = progress },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(program.totalWeeks) { index ->
                Box(
                    Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (index < program.week) c.primary else c.surfaceVar),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(progress, style = ActivaPlanTheme.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text)
        Spacer(Modifier.height(12.dp))
        val nextPlanId = program.nextPlanId
        when {
            program.finished -> {
                Text(strings[StringKey.HOME_PROGRAM_DONE], style = ActivaPlanTheme.type.body, color = c.textMuted)
                Spacer(Modifier.height(12.dp))
                ActivaButton(strings[StringKey.HOME_PROGRAM_VIEW], { onOpenProgram(program.programId) }, Modifier.fillMaxWidth(), style = ButtonStyle.SOFT, compact = true)
            }
            nextPlanId != null -> {
                Text(
                    if (program.weekCompleted) strings[StringKey.HOME_PROGRAM_READY] else strings.format(StringKey.HOME_PROGRAM_DAY, program.dayInWeek),
                    style = ActivaPlanTheme.type.body,
                    color = c.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                ActivaButton(
                    strings.format(StringKey.HOME_PROGRAM_NEXT, program.nextWeek ?: program.week),
                    { onOpenPlan(nextPlanId) },
                    Modifier.fillMaxWidth(),
                    style = if (program.weekCompleted) ButtonStyle.PRIMARY else ButtonStyle.SOFT,
                    compact = true,
                )
            }
            else -> Text(strings.format(StringKey.HOME_PROGRAM_DAY, program.dayInWeek), style = ActivaPlanTheme.type.body, color = c.textMuted)
        }
    }
}

@Composable
private fun EmptyTodayCard(onOpenSchedule: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    ActivaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ActivaIcon(ActivaIconKind.CALENDAR, c.textMuted, size = 32.dp)
            Spacer(Modifier.height(12.dp))
            Text(strings[StringKey.HOME_EMPTY_TITLE], style = ActivaPlanTheme.type.cardTitle, color = c.text)
            Spacer(Modifier.height(16.dp))
            ActivaButton(
                text = strings[StringKey.HOME_EMPTY_ACTION],
                onClick = onOpenSchedule,
                style = ButtonStyle.SOFT,
                icon = ActivaIconKind.PLUS,
            )
        }
    }
}

@Composable
private fun StreakSection(week: WeekStreak) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(strings[StringKey.HOME_STREAK_TITLE], style = ActivaPlanTheme.type.cardTitle, color = c.text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActivaIcon(ActivaIconKind.FLAME, c.primary, size = 18.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    strings.streakDays(week.streak),
                    style = ActivaPlanTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ActivaCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.days.forEach { DayDot(it) }
            }
        }
    }
}

/** Lingkaran satu hari: centang hijau bila selesai, abu bila tidak, garis oranye bila hari ini. */
@Composable
private fun DayDot(day: StreakDay) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val name = strings.dayName(day.dayOfWeek)
    val description = if (day.completed) "$name, ${strings.label(SessionStatus.COMPLETED)}" else name
    Column(
        Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name.first().uppercase(), style = ActivaPlanTheme.type.label, color = c.textMuted)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.size(36.dp)
                .background(if (day.completed) c.success else c.surfaceVar, CircleShape)
                .then(if (day.isToday) Modifier.border(2.dp, c.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (day.completed) ActivaIcon(ActivaIconKind.CHECK, c.onSuccess, size = 18.dp)
        }
    }
}

@Composable
private fun Recommendations(items: List<TemplateRecommendation>, onOpenTemplate: (String) -> Unit) {
    val strings = ActivaPlanTheme.strings
    Column {
        Text(strings[StringKey.HOME_RECOMMENDED], style = ActivaPlanTheme.type.cardTitle, color = ActivaPlanTheme.colors.text)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { item ->
                RecommendationCard(item, { onOpenTemplate(item.id) }, Modifier.weight(1f).fillMaxHeight())
            }
            // Satu kartu tetap setengah lebar seperti di Figma.
            repeat(2 - items.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun RecommendationCard(item: TemplateRecommendation, onClick: () -> Unit, modifier: Modifier) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val meta = strings.exerciseCount(item.exerciseCount) + (item.primaryMode?.let { " · ${strings.shortMode(it)}" } ?: "")
    ActivaCard(modifier, onClick) {
        LevelBadge(item.difficulty)
        Spacer(Modifier.height(10.dp))
        Text(item.name, style = ActivaPlanTheme.type.cardTitle, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(meta, style = ActivaPlanTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = c.textMuted)
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
private fun bigTitle() = ActivaPlanTheme.type.largeTitle.copy(fontSize = 26.sp, lineHeight = 32.sp)

private fun Strings.greeting(period: GreetingPeriod): String = get(
    when (period) {
        GreetingPeriod.MORNING -> StringKey.HOME_GREETING_MORNING
        GreetingPeriod.NOON -> StringKey.HOME_GREETING_NOON
        GreetingPeriod.AFTERNOON -> StringKey.HOME_GREETING_AFTERNOON
        GreetingPeriod.EVENING -> StringKey.HOME_GREETING_EVENING
    },
)

private fun Strings.exerciseCount(count: Int): String =
    format(if (count == 1) StringKey.HOME_EXERCISE_ONE else StringKey.HOME_EXERCISE_COUNT, count)

private fun Strings.streakDays(days: Int): String =
    format(if (days == 1) StringKey.HOME_STREAK_DAY_ONE else StringKey.HOME_STREAK_DAYS, days)

private fun Strings.shortMode(mode: Mode): String = get(
    when (mode) {
        Mode.NO_EQUIPMENT -> StringKey.HOME_MODE_NO_EQUIPMENT
        Mode.PUSHUP_BOARD -> StringKey.HOME_MODE_BOARD
        Mode.BOARD_ROPE -> StringKey.HOME_MODE_ROPE
    },
)

private val previewWeek = WeekStreak(
    days = listOf(true, true, false, true, true, false, false).mapIndexed { i, done -> StreakDay(i + 1, done, isToday = i == 6) },
    streak = 4,
)

private val previewData = HomeData(
    greeting = GreetingPeriod.AFTERNOON,
    today = TodayWorkout("r1", "Board Pemula Full Body", 990, 5, 42, passed = false, requiresSafetyCheck = true),
    week = previewWeek,
    recommendations = listOf(
        TemplateRecommendation("t1", "Dada & Triceps Sedang", Difficulty.MEDIUM, 4, Mode.PUSHUP_BOARD),
        TemplateRecommendation("t2", "Tali Tubuh Atas", Difficulty.MEDIUM, 4, Mode.BOARD_ROPE),
    ),
)

@Composable
private fun HomePreview(darkTheme: Boolean, data: HomeData) = ActivaPlanTheme(darkTheme = darkTheme) {
    HomeContent(HomeUiState.Ready(data), {}, {}, {}, {})
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HomeLightPreview() = HomePreview(darkTheme = false, data = previewData)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HomeDarkPreview() = HomePreview(darkTheme = true, data = previewData)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HomeEmptyLightPreview() = HomePreview(darkTheme = false, data = previewData.copy(today = null, recommendations = emptyList()))

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HomeDoneLightPreview() = HomePreview(
    darkTheme = false,
    data = previewData.copy(today = previewData.today?.copy(dayOfWeek = 2, daysAhead = 1)),
)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun HomePassedDarkPreview() = HomePreview(
    darkTheme = true,
    data = previewData.copy(today = previewData.today?.copy(passed = true)),
)
