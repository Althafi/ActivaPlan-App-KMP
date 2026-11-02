package com.activaplan.app.presentation.routines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.designsystem.ActivaDimens
import com.activaplan.app.designsystem.ActivaPlanTheme
import com.activaplan.app.designsystem.ActivaShapes
import com.activaplan.app.designsystem.components.ActivaBackButton
import com.activaplan.app.designsystem.components.ActivaButton
import com.activaplan.app.designsystem.components.ActivaDialog
import com.activaplan.app.designsystem.components.ActivaIcon
import com.activaplan.app.designsystem.components.ActivaIconKind
import com.activaplan.app.designsystem.components.ActivaSnackbarHost
import com.activaplan.app.designsystem.components.ActivaStepper
import com.activaplan.app.designsystem.components.ActivaTextField
import com.activaplan.app.designsystem.components.AlertType
import com.activaplan.app.designsystem.components.ButtonStyle
import com.activaplan.app.designsystem.components.showActiva
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.model.BoardZone
import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.Category
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.DraftItem
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.RoutineDraft
import com.activaplan.app.domain.model.RoutineItem
import com.activaplan.app.domain.model.RoutineRules
import com.activaplan.app.domain.usecase.EstimateRoutineMinutesUseCase
import com.activaplan.app.domain.usecase.LoadRoutineDraftUseCase
import com.activaplan.app.domain.usecase.SaveRoutineUseCase
import com.activaplan.app.presentation.PlatformBackHandler
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

/** Aksi Builder, dipisah dari tampilan agar pratinjau tidak perlu ViewModel. */
class BuilderActions(
    val onBack: () -> Unit,
    val onName: (String) -> Unit,
    val onSets: (key: Int, delta: Int) -> Unit,
    val onWork: (key: Int, delta: Int) -> Unit,
    val onRemove: (key: Int) -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    val onAdd: () -> Unit,
    val onSave: () -> Unit,
    val onReload: () -> Unit,
)

/**
 * Builder rutinitas. [routineId] null membuat rutinitas baru. [pickedExerciseId] adalah hasil mode pilih dan
 * dikonsumsi sekali lewat [onPickedConsumed]. [onAddExercise] membuka Jelajah mode pilih; [onSaved] dipanggil setelah tersimpan.
 */
@Composable
fun RoutineBuilderScreen(
    routineId: String?,
    pickedExerciseId: String?,
    onPickedConsumed: () -> Unit,
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = koinInject<ExerciseRepository>()
    val cardioRepository = koinInject<CardioRepository>()
    val loadDraft = koinInject<LoadRoutineDraftUseCase>()
    val saveRoutine = koinInject<SaveRoutineUseCase>()
    val strings = ActivaPlanTheme.strings
    val viewModel = viewModel(key = "builder-${routineId ?: "new"}-${strings.language.code}") {
        BuilderViewModel(routineId, strings.language, repository, cardioRepository, loadDraft, saveRoutine)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val isDirty = (state as? BuilderUiState.Ready)?.isDirty == true

    LaunchedEffect(pickedExerciseId) {
        if (pickedExerciseId != null) {
            viewModel.onExercisePicked(pickedExerciseId)
            onPickedConsumed()
        }
    }
    LaunchedEffect(viewModel, strings) {
        viewModel.events.collect { event ->
            when (event) {
                BuilderEvent.Saved -> onSaved()
                BuilderEvent.NeedItem -> snackbarHostState.showActiva(strings[StringKey.BUILDER_NEED_ITEM], AlertType.WARNING)
                BuilderEvent.Full -> snackbarHostState.showActiva(
                    strings.format(StringKey.ALERT_ROUTINE_FULL, RoutineRules.MAX_ITEMS), AlertType.WARNING,
                )
                BuilderEvent.SkippedItems ->
                    snackbarHostState.showActiva(strings[StringKey.ALERT_EXERCISE_MISSING], AlertType.WARNING)
                is BuilderEvent.SaveFailed -> {
                    val retry = snackbarHostState.showActiva(
                        strings[StringKey.ALERT_SAVE_FAILED], AlertType.ERROR, strings[StringKey.ACTION_RETRY],
                    )
                    if (retry) event.retry()
                }
            }
        }
    }

    fun requestBack() {
        if (isDirty) showDiscard = true else onBack()
    }

    PlatformBackHandler(enabled = isDirty) { showDiscard = true }

    val actions = BuilderActions(
        onBack = ::requestBack,
        onName = viewModel::onName,
        onSets = viewModel::onSets,
        onWork = viewModel::onWork,
        onRemove = viewModel::onRemove,
        onMove = viewModel::onMove,
        onAdd = {
            val ready = state as? BuilderUiState.Ready
            if (ready?.draft?.isFull == true) {
                scope.launch {
                    snackbarHostState.showActiva(
                        strings.format(StringKey.ALERT_ROUTINE_FULL, RoutineRules.MAX_ITEMS), AlertType.WARNING,
                    )
                }
            } else {
                onAddExercise()
            }
        },
        onSave = viewModel::save,
        onReload = viewModel::reload,
    )
    BuilderContent(state, actions, snackbarHostState, modifier)

    if (showDiscard) {
        ActivaDialog(
            title = strings[StringKey.BUILDER_DISCARD_TITLE],
            message = strings[StringKey.BUILDER_DISCARD_BODY],
            confirmLabel = strings[StringKey.BUILDER_DISCARD],
            onConfirm = { showDiscard = false; onBack() },
            dismissLabel = strings[StringKey.BUILDER_KEEP_EDITING],
            onDismiss = { showDiscard = false },
        )
    }
}

@Composable
fun BuilderContent(
    state: BuilderUiState,
    actions: BuilderActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val strings = ActivaPlanTheme.strings
    val isNew = (state as? BuilderUiState.Ready)?.isNew ?: true
    Box(modifier.fillMaxSize().background(ActivaPlanTheme.colors.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            TopBar(strings[if (isNew) StringKey.BUILDER_TITLE_NEW else StringKey.BUILDER_TITLE_EDIT], actions.onBack)
            when (state) {
                BuilderUiState.Loading -> Unit
                BuilderUiState.Error -> Message(strings[StringKey.ALERT_LOAD_FAILED], strings[StringKey.ACTION_RELOAD], actions.onReload)
                BuilderUiState.NotFound -> Message(strings[StringKey.BUILDER_NOT_FOUND])
                is BuilderUiState.Ready -> ReadyContent(state, actions)
            }
        }
        ActivaSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ActivaDimens.screenPadding).padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivaBackButton(onBack)
        Text(
            title,
            style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 17.sp),
            color = ActivaPlanTheme.colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(ActivaDimens.minTouchTarget))
    }
}

@Composable
private fun ColumnScope.ReadyContent(state: BuilderUiState.Ready, actions: BuilderActions) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val draft = state.draft
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ActivaDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ActivaTextField(
                value = draft.name,
                onValueChange = actions.onName,
                hint = strings[StringKey.BUILDER_NAME_LABEL],
                isError = state.nameError,
                imeAction = ImeAction.Done,
            )
            if (state.nameError) {
                Text(
                    strings.format(StringKey.ROUTINE_NAME_ERROR, RoutineRules.MAX_NAME_LENGTH),
                    style = ActivaPlanTheme.type.labelSmall.copy(fontSize = 12.sp),
                    color = c.error,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                strings.format(StringKey.BUILDER_EXERCISES, draft.items.size),
                style = ActivaPlanTheme.type.cardTitle,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            if (draft.items.size > 1) {
                Text(strings[StringKey.BUILDER_REORDER_HINT], style = ActivaPlanTheme.type.labelSmall.copy(fontSize = 12.sp), color = c.textMuted)
            }
        }
        ItemList(draft, state.catalog, state.cardioCatalog, actions)
        ActivaButton(
            strings[StringKey.BUILDER_ADD], actions.onAdd, Modifier.fillMaxWidth(),
            style = ButtonStyle.SECONDARY, icon = ActivaIconKind.PLUS,
        )
        Spacer(Modifier.height(12.dp))
    }
    Footer(draft, actions.onSave)
}

@Composable
private fun Footer(draft: RoutineDraft, onSave: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val minutes = remember(draft.items) { EstimateRoutineMinutesUseCase()(draft.items.map { it.item }) }
    Surface(color = c.surface, border = BorderStroke(1.dp, c.border), shape = RectangleShape) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = ActivaDimens.screenPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(strings[StringKey.BUILDER_TOTAL], style = ActivaPlanTheme.type.labelSmall.copy(fontSize = 12.sp), color = c.textMuted)
                Text(
                    strings.routineMeta(draft.items.size, minutes),
                    style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 15.sp),
                    color = c.text,
                )
            }
            ActivaButton(strings[StringKey.BUILDER_SAVE], onSave, icon = ActivaIconKind.CHECK)
        }
    }
}

/**
 * Daftar item yang bisa diurutkan: tahan pegangan lalu geser. Selama menyeret, [dragOffset] adalah selisih piksel
 * dari posisi tata letak item; tiap kali melewati separuh tetangga, item ditukar dan selisih dikurangi tinggi tetangga
 * sehingga item tetap berada di bawah jari.
 */
@Composable
private fun ItemList(
    draft: RoutineDraft,
    catalog: Map<String, Exercise>,
    cardioCatalog: Map<String, CardioWorkout>,
    actions: BuilderActions,
) {
    val items by rememberUpdatedState(draft.items)
    val onMove by rememberUpdatedState(actions.onMove)
    var dragKey by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Int, Int>() }
    val spacingPx = with(LocalDensity.current) { ITEM_SPACING.toPx() }

    fun handleDrag(key: Int, delta: Float) {
        dragOffset += delta
        val index = items.indexOfFirst { it.key == key }
        val neighbor = when {
            dragOffset > 0 -> items.getOrNull(index + 1)
            dragOffset < 0 -> items.getOrNull(index - 1)
            else -> null
        } ?: return
        val step = (heights[neighbor.key] ?: return) + spacingPx
        if (abs(dragOffset) > step / 2) {
            val direction = if (dragOffset > 0) 1 else -1
            onMove(index, index + direction)
            dragOffset -= direction * step
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(ITEM_SPACING)) {
        draft.items.forEachIndexed { index, item ->
            key(item.key) {
                val dragging = dragKey == item.key
                ItemRow(
                    item = item,
                    index = index,
                    count = draft.items.size,
                    exercise = catalog[item.item.exerciseId],
                    cardio = cardioCatalog[item.item.exerciseId],
                    actions = actions,
                    onDragStart = { dragKey = item.key; dragOffset = 0f },
                    onDrag = { handleDrag(item.key, it) },
                    onDragEnd = { dragKey = null; dragOffset = 0f },
                    modifier = Modifier
                        .onSizeChanged { heights[item.key] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                )
            }
        }
    }
}

private val ITEM_SPACING = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemRow(
    item: DraftItem,
    index: Int,
    count: Int,
    exercise: Exercise?,
    cardio: CardioWorkout?,
    actions: BuilderActions,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) actions.onRemove(item.key)
    }
    val moveUp = strings[StringKey.BUILDER_MOVE_UP]
    val moveDown = strings[StringKey.BUILDER_MOVE_DOWN]
    val remove = strings[StringKey.BUILDER_REMOVE]
    val handleLabel = strings[StringKey.BUILDER_DRAG_HANDLE]
    val name = exercise?.name ?: cardio?.name ?: item.item.exerciseId

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    Modifier.fillMaxSize().background(c.errorSoft, ActivaShapes.card).padding(end = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    ActivaIcon(ActivaIconKind.TRASH, c.error, size = 22.dp)
                }
            }
        },
    ) {
        Surface(
            color = c.surface,
            contentColor = c.text,
            shape = ActivaShapes.card,
            border = BorderStroke(1.dp, c.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(ActivaDimens.minTouchTarget)
                        .semantics {
                            contentDescription = "$handleLabel: $name"
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveUp) { actions.onMove(index, index - 1); true })
                                if (index < count - 1) add(CustomAccessibilityAction(moveDown) { actions.onMove(index, index + 1); true })
                                add(CustomAccessibilityAction(remove) { actions.onRemove(item.key); true })
                            }
                        }
                        .pointerInput(item.key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                                onDrag = { change, amount ->
                                    change.consume()
                                    onDrag(amount.y)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    ActivaIcon(ActivaIconKind.DRAG_HANDLE, c.textMuted, size = 22.dp)
                }
                Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(if (cardio != null) c.primary else exercise?.boardZone?.let { c.zone(it) } ?: c.textMuted, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            name,
                            style = ActivaPlanTheme.type.cardTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            color = c.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (cardio != null) {
                        Text(
                            strings.cardioMeta(cardio),
                            style = ActivaPlanTheme.type.body.copy(fontSize = 13.sp),
                            color = c.textMuted,
                            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ActivaStepper(
                                label = strings[StringKey.BUILDER_UNIT_SET],
                                value = item.item.sets,
                                onDecrease = { actions.onSets(item.key, -1) },
                                onIncrease = { actions.onSets(item.key, 1) },
                                canDecrease = item.canDecreaseSets,
                                canIncrease = item.canIncreaseSets,
                            )
                            ActivaStepper(
                                label = strings[if (item.isTimed) StringKey.BUILDER_UNIT_SECONDS else StringKey.BUILDER_UNIT_REP],
                                value = item.workValue,
                                onDecrease = { actions.onWork(item.key, -1) },
                                onIncrease = { actions.onWork(item.key, 1) },
                                canDecrease = item.canDecreaseWork,
                                canIncrease = item.canIncreaseWork,
                            )
                        }
                    }
                }
            }
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

private fun previewExercise(id: String, name: String, zone: BoardZone?, reps: Int?, seconds: Int? = null) = Exercise(
    id = id, name = name, mode = if (zone != null) Mode.PUSHUP_BOARD else Mode.BOARD_ROPE, category = Category.CHEST,
    difficulty = Difficulty.MEDIUM, boardZone = zone, primaryMuscles = emptyList(), sets = 3, reps = reps,
    durationSeconds = seconds, restSeconds = 60, steps = emptyList(), tips = emptyList(), commonMistakes = emptyList(),
)

private val previewCatalog = listOf(
    previewExercise("a", "Push-up Dada Standar", BoardZone.BLUE, 10),
    previewExercise("b", "Push-up Triceps", BoardZone.GREEN, 10),
    previewExercise("c", "Push-up Zona Kuning dengan Jeda", BoardZone.YELLOW, 8),
    previewExercise("d", "Tahan Genggaman Tali", null, null, 20),
).associateBy { it.id }

private val previewDraft = RoutineDraft(
    id = "r1",
    name = "Dada & Triceps Sedang",
    items = previewCatalog.values.mapIndexed { index, e -> DraftItem(index, RoutineItem(e.id, 3, e.reps, e.durationSeconds, 60)) },
)

private val previewActions = BuilderActions({}, {}, { _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, {})

@Composable
private fun BuilderPreview(darkTheme: Boolean, state: BuilderUiState) = ActivaPlanTheme(darkTheme = darkTheme) {
    BuilderContent(state, previewActions, remember { SnackbarHostState() })
}

private val previewReady = BuilderUiState.Ready(previewDraft, previewCatalog, isNew = true, isDirty = true)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun BuilderLightPreview() = BuilderPreview(false, previewReady)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun BuilderDarkPreview() = BuilderPreview(true, previewReady)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun BuilderEmptyErrorLightPreview() = BuilderPreview(
    false,
    previewReady.copy(draft = RoutineDraft("new"), nameError = true, isDirty = false),
)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun BuilderEmptyDarkPreview() = BuilderPreview(true, previewReady.copy(draft = RoutineDraft("new"), isDirty = false))
