package com.activaplan.app.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import com.activaplan.app.designsystem.components.ActivaTextField
import com.activaplan.app.designsystem.components.AlertType
import com.activaplan.app.designsystem.components.BoardDiagram
import com.activaplan.app.designsystem.components.ButtonStyle
import com.activaplan.app.designsystem.components.ExerciseImageView
import com.activaplan.app.designsystem.components.LevelBadge
import com.activaplan.app.designsystem.components.ZoneChip
import com.activaplan.app.designsystem.components.showActiva
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.i18n.Strings
import com.activaplan.app.domain.model.AddToRoutineResult
import com.activaplan.app.domain.model.BoardZone
import com.activaplan.app.domain.model.Category
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.ExerciseDetail
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineItem
import com.activaplan.app.domain.model.RoutineRules
import com.activaplan.app.domain.usecase.AddExerciseToRoutineUseCase
import com.activaplan.app.domain.usecase.GetExerciseDetailUseCase
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

/** Aksi layar Detail, dipisah dari tampilan agar pratinjau tidak perlu ViewModel. */
class ExerciseDetailActions(
    val onBack: () -> Unit,
    val onOpenExercise: (String) -> Unit,
    val onReload: () -> Unit,
    val onAddToExisting: (String) -> Unit,
    val onAddToNew: (String) -> Unit,
)

/**
 * Detail latihan. [onOpenExercise] membuka latihan lain (Turun/Naik level) dan menggantikan layar ini,
 * jadi Kembali selalu menuju Jelajah.
 */
@Composable
fun ExerciseDetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val getDetail = koinInject<GetExerciseDetailUseCase>()
    val addToRoutine = koinInject<AddExerciseToRoutineUseCase>()
    val strings = ActivaPlanTheme.strings
    val viewModel = viewModel(key = "detail-$id-${strings.language.code}") {
        ExerciseDetailViewModel(id, strings.language, getDetail, addToRoutine)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel, strings) {
        viewModel.outcomes.collect { snackbarHostState.show(it, strings) }
    }
    val actions = ExerciseDetailActions(
        onBack, onOpenExercise, viewModel::reload, viewModel::addToExisting, viewModel::addToNew,
    )
    ExerciseDetailContent(state, routines, actions, snackbarHostState, modifier)
}

internal suspend fun SnackbarHostState.show(outcome: AddOutcome, strings: Strings) {
    when (outcome) {
        is AddOutcome.Done -> when (val result = outcome.result) {
            is AddToRoutineResult.Added -> showActiva(strings.format(StringKey.ALERT_ADDED_TO_ROUTINE, result.routineName))
            AddToRoutineResult.RoutineFull ->
                showActiva(strings.format(StringKey.ALERT_ROUTINE_FULL, RoutineRules.MAX_ITEMS), AlertType.WARNING)
            AddToRoutineResult.RoutineNotFound -> showActiva(strings[StringKey.ALERT_SAVE_FAILED], AlertType.ERROR)
            AddToRoutineResult.InvalidName ->
                showActiva(strings.format(StringKey.ROUTINE_NAME_ERROR, RoutineRules.MAX_NAME_LENGTH), AlertType.WARNING)
        }
        is AddOutcome.Failed -> {
            val retry = showActiva(strings[StringKey.ALERT_SAVE_FAILED], AlertType.ERROR, strings[StringKey.ACTION_RETRY])
            if (retry) outcome.retry()
        }
    }
}

@Composable
fun ExerciseDetailContent(
    state: ExerciseDetailUiState,
    routines: List<Routine>,
    actions: ExerciseDetailActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val strings = ActivaPlanTheme.strings
    Box(modifier.fillMaxSize().background(ActivaPlanTheme.colors.bg)) {
        when (state) {
            ExerciseDetailUiState.Loading -> BackOnly(actions.onBack)
            ExerciseDetailUiState.Error -> Message(strings[StringKey.ALERT_LOAD_FAILED], actions.onBack, strings[StringKey.ACTION_RELOAD], actions.onReload)
            ExerciseDetailUiState.NotFound -> Message(strings[StringKey.DETAIL_NOT_FOUND], actions.onBack)
            is ExerciseDetailUiState.Ready -> DetailReady(state.detail, routines, actions)
        }
        ActivaSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
    }
}

@Composable
private fun DetailReady(detail: ExerciseDetail, routines: List<Routine>, actions: ExerciseDetailActions) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val exercise = detail.exercise
    var showSheet by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Hero(exercise, actions.onBack)
        Column(
            Modifier.padding(horizontal = ActivaDimens.screenPadding).padding(top = 20.dp, bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(exercise.name, style = ActivaPlanTheme.type.largeTitle, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelBadge(exercise.difficulty)
                exercise.boardZone?.let { ZoneChip(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(exercise.sets.toString(), strings[StringKey.DETAIL_UNIT_SET], Modifier.weight(1f))
                StatTile((exercise.reps ?: exercise.durationSeconds ?: 0).toString(), strings.workUnit(exercise), Modifier.weight(1f))
                StatTile(exercise.restSeconds.toString(), strings[StringKey.DETAIL_UNIT_REST], Modifier.weight(1f))
            }
            if (detail.safetyNotes.isNotEmpty()) SafetyCard(detail.safetyNotes)
            Steps(exercise.steps)
            if (exercise.tips.isNotEmpty()) Tips(exercise.tips)
            if (exercise.commonMistakes.isNotEmpty()) Mistakes(exercise.commonMistakes)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val down = detail.regressionId
                val up = detail.progressionId
                ActivaButton(
                    strings[StringKey.DETAIL_LEVEL_DOWN], { down?.let(actions.onOpenExercise) }, Modifier.weight(1f),
                    ButtonStyle.SECONDARY, enabled = down != null, icon = ActivaIconKind.ARROW_DOWN, compact = true,
                )
                ActivaButton(
                    strings[StringKey.DETAIL_LEVEL_UP], { up?.let(actions.onOpenExercise) }, Modifier.weight(1f),
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
                exerciseName = exercise.name,
                routines = routines,
                onPick = { showSheet = false; actions.onAddToExisting(it) },
                onCreate = { showSheet = false; actions.onAddToNew(it) },
            )
        }
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = ActivaPlanTheme.colors
    val label = ActivaPlanTheme.strings[StringKey.DETAIL_BACK]
    Box(
        modifier.size(ActivaDimens.minTouchTarget)
            .background(c.surface, CircleShape)
            .border(1.dp, c.border, CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        ActivaIcon(ActivaIconKind.CHEVRON_LEFT, c.text, size = 22.dp)
    }
}

/** Hero: diagram board dengan lubang zona disorot, atau ikon untuk latihan tanpa alat. */
@Composable
private fun Hero(exercise: Exercise, onBack: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Column(
        Modifier.fillMaxWidth().background(if (c.isDark) c.surface else c.surfaceVar).statusBarsPadding()
            .padding(horizontal = ActivaDimens.screenPadding).padding(top = 8.dp, bottom = 16.dp),
    ) {
        BackButton(onBack)
        Spacer(Modifier.height(12.dp))
        ExerciseImageView(exercise.images.firstOrNull(), Modifier.clip(RoundedCornerShape(16.dp))) {
            if (exercise.mode == Mode.NO_EQUIPMENT) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    ActivaIcon(ActivaIconKind.DUMBBELL, c.textMuted, size = 48.dp)
                }
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BoardDiagram(exercise.boardZone, Modifier.fillMaxWidth(0.886f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val zone = exercise.boardZone
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (zone != null) {
                Box(Modifier.size(8.dp).background(c.zone(zone), CircleShape))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (zone != null) strings.format(StringKey.DETAIL_ZONE, strings.zoneChip(zone)) else strings.label(exercise.mode),
                style = ActivaPlanTheme.type.label.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = c.text,
            )
        }
    }
}

@Composable
internal fun StatTile(value: String, unit: String, modifier: Modifier = Modifier) {
    val c = ActivaPlanTheme.colors
    Surface(
        color = c.surface, contentColor = c.text, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, c.border), modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = ActivaPlanTheme.type.largeTitle.copy(fontSize = 24.sp, lineHeight = 30.sp), color = c.text)
            Text(unit, style = ActivaPlanTheme.type.labelSmall.copy(fontSize = 12.sp), color = c.textMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun SectionTitle(text: String) =
    Text(text, style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 18.sp), color = ActivaPlanTheme.colors.text)

@Composable
internal fun Steps(steps: List<String>) {
    val c = ActivaPlanTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(ActivaPlanTheme.strings[StringKey.DETAIL_STEPS])
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(28.dp).background(c.primarySoft, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = ActivaPlanTheme.type.label.copy(fontWeight = FontWeight.Bold), color = c.primary)
                }
                Text(step, style = ActivaPlanTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = c.text, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun Tips(tips: List<String>) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Surface(color = c.primarySoft, shape = ActivaShapes.button, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tips.forEach { tip ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActivaIcon(ActivaIconKind.INFO, c.text, size = 20.dp)
                    Text(strings.format(StringKey.DETAIL_TIP, tip), style = ActivaPlanTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun Mistakes(mistakes: List<String>) {
    val c = ActivaPlanTheme.colors
    Surface(color = c.warningSoft, shape = ActivaShapes.button, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivaIcon(ActivaIconKind.WARNING, c.text, size = 20.dp)
                Text(
                    ActivaPlanTheme.strings[StringKey.DETAIL_MISTAKES],
                    style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 14.sp),
                    color = c.text,
                )
            }
            mistakes.forEach { Text("• $it", style = ActivaPlanTheme.type.body, color = c.text) }
        }
    }
}

/** Info keselamatan untuk latihan tali, dari `device.safetyNotes`. */
@Composable
private fun SafetyCard(notes: List<String>) {
    val c = ActivaPlanTheme.colors
    ActivaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivaIcon(ActivaIconKind.INFO, c.primary, size = 20.dp)
            Text(ActivaPlanTheme.strings[StringKey.DETAIL_SAFETY_TITLE], style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 14.sp), color = c.text)
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            notes.forEach { Text("• $it", style = ActivaPlanTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = c.textMuted) }
        }
    }
}

@Composable
internal fun BackOnly(onBack: () -> Unit) {
    BackButton(onBack, Modifier.statusBarsPadding().padding(start = ActivaDimens.screenPadding, top = 8.dp))
}

@Composable
internal fun Message(text: String, onBack: () -> Unit, actionLabel: String? = null, onAction: () -> Unit = {}) {
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

/** Isi sheet "Tambah ke Rutinitas": pilih rutinitas yang ada atau buat baru bernama 1-40 karakter. */
@Composable
fun AddToRoutineContent(
    exerciseName: String,
    routines: List<Routine>,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf(false) }
    val showCreate = creating || routines.isEmpty()

    fun submit() {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > RoutineRules.MAX_NAME_LENGTH) nameError = true else onCreate(trimmed)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(
                strings[StringKey.DETAIL_ADD_TO_ROUTINE],
                style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                color = c.text,
            )
            Text(exerciseName, style = ActivaPlanTheme.type.body, color = c.textMuted)
        }
        if (routines.isEmpty()) {
            Text(strings[StringKey.ROUTINE_PICK_EMPTY], style = ActivaPlanTheme.type.body, color = c.textMuted)
        } else {
            Column(
                Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                routines.forEach { routine ->
                    ActivaCard(Modifier.fillMaxWidth(), onClick = { onPick(routine.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(routine.name, style = ActivaPlanTheme.type.cardTitle, color = c.text, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                strings.format(
                                    if (routine.items.size == 1) StringKey.ROUTINE_ITEM_ONE else StringKey.ROUTINE_ITEMS,
                                    routine.items.size,
                                ),
                                style = ActivaPlanTheme.type.body.copy(fontSize = 13.sp),
                                color = c.textMuted,
                            )
                        }
                    }
                }
            }
        }
        if (showCreate) {
            ActivaTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                hint = strings[StringKey.ROUTINE_NAME_HINT],
                isError = nameError,
                imeAction = ImeAction.Done,
                onImeAction = ::submit,
            )
            if (nameError) {
                Text(
                    strings.format(StringKey.ROUTINE_NAME_ERROR, RoutineRules.MAX_NAME_LENGTH),
                    style = ActivaPlanTheme.type.labelSmall.copy(fontSize = 12.sp),
                    color = c.error,
                )
            }
            ActivaButton(strings[StringKey.ROUTINE_CREATE_ADD], ::submit, Modifier.fillMaxWidth(), icon = ActivaIconKind.PLUS)
        } else {
            ActivaButton(
                strings[StringKey.ROUTINE_NEW], { creating = true }, Modifier.fillMaxWidth(),
                style = ButtonStyle.SOFT, icon = ActivaIconKind.PLUS,
            )
        }
    }
}

private val previewExercise = Exercise(
    id = "pb_chest_pushup", name = "Push-up Dada Standar", mode = Mode.PUSHUP_BOARD, category = Category.CHEST,
    difficulty = Difficulty.MEDIUM, boardZone = BoardZone.BLUE, primaryMuscles = listOf("Dada"),
    sets = 3, reps = 10, restSeconds = 60,
    steps = listOf(
        "Tangan pada lingkaran biru, kaki lurus bertumpu pada ujung jari kaki.",
        "Badan membentuk garis lurus dari kepala sampai tumit.",
        "Turunkan dada sampai hampir menyentuh papan, lalu dorong naik.",
    ),
    tips = listOf("tarik napas saat turun, buang napas saat mendorong."),
    commonMistakes = listOf("Kepala menunduk atau mendongak berlebihan.", "Gerakan setengah (tidak turun penuh)."),
    progressionId = "pb_chest_decline", regressionId = "pb_chest_knee_pushup",
)

private val previewDetail = ExerciseDetail(previewExercise, "pb_chest_knee_pushup", "pb_chest_decline", emptyList())

private val previewRopeDetail = ExerciseDetail(
    exercise = previewExercise.copy(
        id = "rope_grip_hold", name = "Tahan Genggaman Tali", mode = Mode.BOARD_ROPE, category = Category.ARMS,
        difficulty = Difficulty.EASY, boardZone = null, reps = null, durationSeconds = 20,
        progressionId = null, regressionId = null,
    ),
    regressionId = null,
    progressionId = null,
    safetyNotes = listOf(
        "Letakkan papan di lantai datar dan tidak licin; pastikan tidak bergeser sebelum mulai.",
        "Periksa tali dari retak atau serat terkelupas sebelum dipakai.",
        "Jika nyeri tajam di sendi, hentikan latihan.",
    ),
)

private val previewActions = ExerciseDetailActions({}, {}, {}, {}, {})

@Composable
private fun DetailPreview(darkTheme: Boolean, detail: ExerciseDetail) = ActivaPlanTheme(darkTheme = darkTheme) {
    ExerciseDetailContent(ExerciseDetailUiState.Ready(detail), emptyList(), previewActions, remember { SnackbarHostState() })
}

@Preview(widthDp = 390, heightDp = 1010, showBackground = true)
@Composable
private fun DetailLightPreview() = DetailPreview(false, previewDetail)

@Preview(widthDp = 390, heightDp = 1010, showBackground = true)
@Composable
private fun DetailDarkPreview() = DetailPreview(true, previewDetail)

@Preview(widthDp = 390, heightDp = 1010, showBackground = true)
@Composable
private fun DetailRopeLightPreview() = DetailPreview(false, previewRopeDetail)

@Preview(widthDp = 390, heightDp = 1010, showBackground = true)
@Composable
private fun DetailNoEquipmentDarkPreview() = DetailPreview(
    true,
    previewRopeDetail.copy(exercise = previewRopeDetail.exercise.copy(mode = Mode.NO_EQUIPMENT), safetyNotes = emptyList()),
)

private val previewRoutines = listOf(
    Routine("r1", "Board Pemula Full Body", List(5) { RoutineItem("a", 3, 10, null, 60) }),
    Routine("r2", "Dada & Triceps", listOf(RoutineItem("a", 3, 10, null, 60))),
)

@Composable
private fun SheetPreview(darkTheme: Boolean, routines: List<Routine>) = ActivaPlanTheme(darkTheme = darkTheme) {
    Column(Modifier.background(ActivaPlanTheme.colors.surface).padding(ActivaDimens.screenPadding)) {
        AddToRoutineContent("Push-up Dada Standar", routines, {}, {})
    }
}

@Preview(widthDp = 390, showBackground = true)
@Composable
private fun SheetLightPreview() = SheetPreview(false, previewRoutines)

@Preview(widthDp = 390, showBackground = true)
@Composable
private fun SheetEmptyDarkPreview() = SheetPreview(true, emptyList())
