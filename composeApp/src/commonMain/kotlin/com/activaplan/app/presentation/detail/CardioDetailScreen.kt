package com.activaplan.app.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.activaplan.app.designsystem.ActivaDimens
import com.activaplan.app.designsystem.ActivaPlanTheme
import com.activaplan.app.designsystem.ActivaShapes
import com.activaplan.app.designsystem.components.ActivaBottomSheet
import com.activaplan.app.designsystem.components.ActivaButton
import com.activaplan.app.designsystem.components.ActivaCard
import com.activaplan.app.designsystem.components.ActivaIcon
import com.activaplan.app.designsystem.components.ActivaIconKind
import com.activaplan.app.designsystem.components.ActivaSnackbarHost
import com.activaplan.app.designsystem.components.ButtonStyle
import com.activaplan.app.designsystem.components.CardioSegmentChart
import com.activaplan.app.designsystem.components.LevelBadge
import com.activaplan.app.designsystem.components.TextBadge
import com.activaplan.app.designsystem.components.segmentColor
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.model.CardioBlock
import com.activaplan.app.domain.model.CardioDetail
import com.activaplan.app.domain.model.CardioSegment
import com.activaplan.app.domain.model.CardioType
import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.Motion
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.SegmentKind
import com.activaplan.app.domain.usecase.AddExerciseToRoutineUseCase
import com.activaplan.app.domain.usecase.GetCardioDetailUseCase
import com.activaplan.app.domain.usecase.ObserveSettingsUseCase
import com.activaplan.app.domain.cardio.HeartRateZones
import com.activaplan.app.domain.cardio.PaceGuide
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

/** Aksi layar Detail kardio, dipisah dari tampilan agar pratinjau tidak perlu ViewModel. */
class CardioDetailActions(
    val onBack: () -> Unit,
    val onOpenCardio: (String) -> Unit,
    val onReload: () -> Unit,
    val onAddToExisting: (String) -> Unit,
    val onAddToNew: (String) -> Unit,
)

/**
 * Detail kardio. [onOpenCardio] membuka kardio lain (Turun/Naik level) dan menggantikan layar ini,
 * jadi Kembali selalu menuju Jelajah.
 */
@Composable
fun CardioDetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenCardio: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val getDetail = koinInject<GetCardioDetailUseCase>()
    val addToRoutine = koinInject<AddExerciseToRoutineUseCase>()
    val observeSettings = koinInject<ObserveSettingsUseCase>()
    val strings = ActivaPlanTheme.strings
    val viewModel = viewModel(key = "cardio-detail-$id-${strings.language.code}") {
        CardioDetailViewModel(id, strings.language, getDetail, addToRoutine, observeSettings)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val age by viewModel.age.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel, strings) {
        viewModel.outcomes.collect { snackbarHostState.show(it, strings) }
    }
    val actions = CardioDetailActions(onBack, onOpenCardio, viewModel::reload, viewModel::addToExisting, viewModel::addToNew)
    CardioDetailContent(state, routines, actions, snackbarHostState, modifier, age)
}

@Composable
fun CardioDetailContent(
    state: CardioDetailUiState,
    routines: List<Routine>,
    actions: CardioDetailActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    age: Int? = null,
) {
    val strings = ActivaPlanTheme.strings
    Box(modifier.fillMaxSize().background(ActivaPlanTheme.colors.bg)) {
        when (state) {
            CardioDetailUiState.Loading -> BackOnly(actions.onBack)
            CardioDetailUiState.Error -> Message(strings[StringKey.ALERT_LOAD_FAILED], actions.onBack, strings[StringKey.ACTION_RELOAD], actions.onReload)
            CardioDetailUiState.NotFound -> Message(strings[StringKey.DETAIL_NOT_FOUND], actions.onBack)
            is CardioDetailUiState.Ready -> CardioDetailReady(state.detail, routines, actions, age)
        }
        ActivaSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioDetailReady(detail: CardioDetail, routines: List<Routine>, actions: CardioDetailActions, age: Int?) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val workout = detail.workout
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val effort = workout.effortRange
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        CardioHero(workout, actions.onBack)
        Column(
            Modifier.padding(horizontal = ActivaDimens.screenPadding).padding(top = 20.dp, bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(workout.name, style = ActivaPlanTheme.type.largeTitle, color = c.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LevelBadge(workout.difficulty)
                TextBadge(strings.label(workout.type))
            }
            Text(workout.summary, style = ActivaPlanTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(workout.totalMinutes.toString(), strings[StringKey.CARDIO_UNIT_MINUTES], Modifier.weight(1f))
                StatTile(
                    if (effort.first == effort.last) "${effort.first}" else "${effort.first}-${effort.last}",
                    strings[StringKey.CARDIO_UNIT_EFFORT],
                    Modifier.weight(1f),
                )
                StatTile(workout.expand().size.toString(), strings[StringKey.CARDIO_UNIT_SEGMENTS], Modifier.weight(1f))
            }
            Structure(workout)
            PaceCard(workout)
            HeartRateCard(workout, age)
            EffortScaleCard()
            if (workout.tips.isNotEmpty()) Tips(workout.tips)
            if (workout.commonMistakes.isNotEmpty()) Mistakes(workout.commonMistakes)
            SafetyNote()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val down = detail.regressionId
                val up = detail.progressionId
                ActivaButton(
                    strings[StringKey.DETAIL_LEVEL_DOWN], { down?.let(actions.onOpenCardio) }, Modifier.weight(1f),
                    ButtonStyle.SECONDARY, enabled = down != null, icon = ActivaIconKind.ARROW_DOWN, compact = true,
                )
                ActivaButton(
                    strings[StringKey.DETAIL_LEVEL_UP], { up?.let(actions.onOpenCardio) }, Modifier.weight(1f),
                    ButtonStyle.SECONDARY, enabled = up != null, icon = ActivaIconKind.ARROW_UP, compact = true,
                )
            }
            ActivaButton(
                strings[StringKey.DETAIL_ADD_TO_ROUTINE], { showSheet = true }, Modifier.fillMaxWidth(),
                icon = ActivaIconKind.PLUS,
            )
        }
    }
    if (showSheet) {
        ActivaBottomSheet(onDismiss = { showSheet = false }) {
            AddToRoutineContent(
                exerciseName = workout.name,
                routines = routines,
                onPick = { showSheet = false; actions.onAddToExisting(it) },
                onCreate = { showSheet = false; actions.onAddToNew(it) },
            )
        }
    }
}

@Composable
private fun CardioHero(workout: CardioWorkout, onBack: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Column(
        Modifier.fillMaxWidth().background(if (c.isDark) c.surface else c.surfaceVar).statusBarsPadding()
            .padding(horizontal = ActivaDimens.screenPadding).padding(top = 8.dp, bottom = 16.dp),
    ) {
        BackButton(onBack)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            ActivaIcon(ActivaIconKind.PULSE, c.primary, size = 48.dp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            strings[StringKey.EXPLORE_KIND_CARDIO] + " · " + strings.label(workout.type),
            style = ActivaPlanTheme.type.label.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = c.text,
        )
    }
}

/** Grafik batang plus daftar segmen per blok; blok berulang diberi judul "N× ulangi". */
@Composable
private fun Structure(workout: CardioWorkout) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val description = strings[StringKey.CARDIO_STRUCTURE] + ": " + workout.blocks.joinToString(", ") { block ->
        val prefix = if (block.repeat > 1) "${block.repeat}× " else ""
        prefix + block.segments.joinToString(" / ") { strings.segmentName(it) + " " + strings.duration(it.durationSeconds) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(strings[StringKey.CARDIO_STRUCTURE])
        CardioSegmentChart(workout.expand(), description)
        workout.blocks.forEach { block ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (block.repeat > 1) {
                    Text(
                        strings.format(StringKey.CARDIO_REPEAT, block.repeat),
                        style = ActivaPlanTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textMuted,
                    )
                }
                block.segments.forEach { SegmentLine(it) }
            }
        }
    }
}

@Composable
private fun SegmentLine(segment: CardioSegment) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(segmentColor(segment.kind), CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            strings.format(StringKey.CARDIO_SEGMENT_LINE, strings.segmentName(segment), strings.duration(segment.durationSeconds), segment.rpe),
            style = ActivaPlanTheme.type.body,
            color = c.text,
        )
    }
}

/** Rentang pace acuan per gerakan yang dipakai kardio ini, urut dari yang paling lambat. */
@Composable
private fun PaceCard(workout: CardioWorkout) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val motions = workout.expand().map { it.motion }.distinct().sortedBy { it.ordinal }
    ActivaCard(Modifier.fillMaxWidth()) {
        Text(strings[StringKey.CARDIO_PACE_TITLE], style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 14.sp), color = c.text)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            motions.forEach { motion ->
                val range = PaceGuide.range(motion)
                Text(
                    strings.format(StringKey.CARDIO_PACE_LINE, strings.label(motion), PaceGuide.format(range.first), PaceGuide.format(range.last)),
                    style = ActivaPlanTheme.type.body,
                    color = c.text,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            strings[StringKey.CARDIO_PACE_NOTE],
            style = ActivaPlanTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = c.textMuted,
        )
    }
}

/** Zona detak jantung per tingkat usaha yang muncul di sesi, ditambah aturan penjagaan. Tanpa usia: tampil dalam persen. */
@Composable
private fun HeartRateCard(workout: CardioWorkout, age: Int?) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val efforts = workout.expand().map { it.rpe }.distinct().sorted()
    ActivaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivaIcon(ActivaIconKind.PULSE, c.primary, size = 20.dp)
            Text(strings[StringKey.CARDIO_HR_TITLE], style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 14.sp), color = c.text)
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            efforts.forEach { Text(strings.heartRateLine(it, age), style = ActivaPlanTheme.type.body, color = c.text) }
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val small = ActivaPlanTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp)
            Text(strings[StringKey.CARDIO_HR_GUARD], style = small, color = c.textMuted)
            if (age != null) {
                Text(
                    strings.format(StringKey.CARDIO_HR_CAUTION, HeartRateZones.CAUTION_PERCENT, HeartRateZones.cautionBpm(age)),
                    style = small,
                    color = c.textMuted,
                )
            } else {
                Text(strings[StringKey.CARDIO_HR_NEED_AGE], style = small, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun EffortScaleCard() {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Surface(color = c.primarySoft, shape = ActivaShapes.button, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivaIcon(ActivaIconKind.INFO, c.text, size = 20.dp)
                Text(
                    strings[StringKey.CARDIO_RPE_TITLE],
                    style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 14.sp),
                    color = c.text,
                )
            }
            Text(strings[StringKey.CARDIO_RPE_HELP], style = ActivaPlanTheme.type.body, color = c.text)
        }
    }
}

@Composable
private fun SafetyNote() {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    ActivaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivaIcon(ActivaIconKind.INFO, c.primary, size = 20.dp)
            Text(strings[StringKey.DETAIL_SAFETY_TITLE], style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 14.sp), color = c.text)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            strings[StringKey.CARDIO_SAFETY],
            style = ActivaPlanTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = c.textMuted,
        )
    }
}

private val previewWorkout = CardioWorkout(
    id = "cd_fartlek", name = "Fartlek", type = CardioType.FARTLEK, difficulty = Difficulty.MEDIUM,
    summary = "Alternate 1 minute fast with 2 minutes easy, 6 times. Builds speed without strict pacing.",
    blocks = listOf(
        CardioBlock(segments = listOf(CardioSegment(SegmentKind.WARMUP, Motion.JOG, 600, 3))),
        CardioBlock(repeat = 6, segments = listOf(CardioSegment(SegmentKind.WORK, Motion.RUN, 60, 7), CardioSegment(SegmentKind.RECOVERY, Motion.JOG, 120, 3))),
        CardioBlock(segments = listOf(CardioSegment(SegmentKind.COOLDOWN, Motion.WALK, 300, 2))),
    ),
    tips = listOf("The fast parts feel strong but controlled, not a sprint."),
    commonMistakes = listOf("Going too hard on early repeats and fading at the end.", "Skipping the warm-up."),
    progressionId = "cd_tempo_run", regressionId = "cd_easy_run",
)

private val previewActions = CardioDetailActions({}, {}, {}, {}, {})

@Composable
private fun CardioPreview(darkTheme: Boolean) = ActivaPlanTheme(darkTheme = darkTheme) {
    CardioDetailContent(
        CardioDetailUiState.Ready(CardioDetail(previewWorkout, "cd_easy_run", "cd_tempo_run")),
        emptyList(), previewActions, remember { SnackbarHostState() },
    )
}

@Preview(widthDp = 390, heightDp = 1100, showBackground = true)
@Composable
private fun CardioDetailLightPreview() = CardioPreview(false)

@Preview(widthDp = 390, heightDp = 1100, showBackground = true)
@Composable
private fun CardioDetailDarkPreview() = CardioPreview(true)
