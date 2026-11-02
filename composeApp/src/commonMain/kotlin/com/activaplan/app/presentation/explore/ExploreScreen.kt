package com.activaplan.app.presentation.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.designsystem.ActivaDimens
import com.activaplan.app.designsystem.ActivaPlanTheme
import com.activaplan.app.designsystem.ActivaShapes
import com.activaplan.app.designsystem.components.ActivaBackButton
import com.activaplan.app.designsystem.components.ActivaButton
import com.activaplan.app.designsystem.components.ActivaCard
import com.activaplan.app.designsystem.components.ActivaChip
import com.activaplan.app.designsystem.components.ActivaIcon
import com.activaplan.app.designsystem.components.ActivaIconKind
import com.activaplan.app.designsystem.components.ActivaSegmentedControl
import com.activaplan.app.designsystem.components.ActivaTextField
import com.activaplan.app.designsystem.components.BoardThumbnail
import com.activaplan.app.designsystem.components.ButtonStyle
import com.activaplan.app.designsystem.components.LevelBadge
import com.activaplan.app.designsystem.components.TextBadge
import com.activaplan.app.designsystem.components.ZoneChip
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.i18n.Strings
import com.activaplan.app.domain.model.ActivityKind
import com.activaplan.app.domain.model.BoardZone
import com.activaplan.app.domain.model.CardioBlock
import com.activaplan.app.domain.model.CardioSegment
import com.activaplan.app.domain.model.CardioType
import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.Category
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.ExploreQuery
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.Motion
import com.activaplan.app.domain.model.SegmentKind
import com.activaplan.app.domain.model.initialExploreMode
import com.activaplan.app.domain.usecase.FilterCardioUseCase
import com.activaplan.app.domain.usecase.FilterExercisesUseCase
import com.activaplan.app.domain.usecase.ObserveSettingsUseCase
import com.activaplan.app.presentation.ScreenListSkeleton
import com.activaplan.app.presentation.SkeletonBlock
import com.activaplan.app.presentation.rememberShowSkeleton
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

/** Aksi layar Jelajah, dipisah dari tampilan agar pratinjau tidak perlu ViewModel. */
class ExploreActions(
    val onMode: (Mode) -> Unit,
    val onText: (String) -> Unit,
    val onCategory: (Category?) -> Unit,
    val onDifficulty: (Difficulty?) -> Unit,
    val onResetFilters: () -> Unit,
    val onReload: () -> Unit,
    val onOpenExercise: (String) -> Unit,
    val onKind: (ActivityKind) -> Unit = {},
    /** Membuka detail kardio; bawaannya sama dengan [onOpenExercise] (mode pilih mengembalikan ID apa pun). */
    val onOpenCardio: (String) -> Unit = onOpenExercise,
)

/**
 * Jelajah: cari dan filter latihan per jenis (Kekuatan/Kardio), mode, kategori, dan level. [onOpenExercise] menerima
 * ID latihan kekuatan dan [onOpenCardio] ID kardio (bila null, memakai [onOpenExercise]).
 * Bila [onBack] diberikan, layar dalam mode pilih (dari Builder): ada tombol kembali, dan mengetuk kartu
 * memanggil [onOpenExercise] dengan latihan yang dipilih. Daftar mulai dari atas lagi tiap [scrollResetToken] berubah.
 */
@Composable
fun ExploreScreen(
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    scrollResetToken: Int = 0,
    onOpenCardio: ((String) -> Unit)? = null,
) {
    val repository = koinInject<ExerciseRepository>()
    val cardioRepository = koinInject<CardioRepository>()
    val filter = koinInject<FilterExercisesUseCase>()
    val filterCardio = koinInject<FilterCardioUseCase>()
    val observeSettings = koinInject<ObserveSettingsUseCase>()
    val language = ActivaPlanTheme.strings.language
    val viewModel = viewModel(key = "explore-${if (onBack != null) "pick-" else ""}${language.code}") {
        ExploreViewModel(repository, cardioRepository, filter, filterCardio, language) { observeSettings().first().initialExploreMode() }
    }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = ExploreActions(
        viewModel::onMode, viewModel::onText, viewModel::onCategory, viewModel::onDifficulty,
        viewModel::resetFilters, viewModel::reload, onOpenExercise, viewModel::onKind, onOpenCardio ?: onOpenExercise,
    )
    ExploreContent(query, state, actions, modifier, onBack, scrollResetToken)
}

private val modes = listOf(Mode.NO_EQUIPMENT, Mode.PUSHUP_BOARD, Mode.BOARD_ROPE)
private val kinds = listOf(ActivityKind.STRENGTH, ActivityKind.CARDIO)

private fun kindLabel(strings: Strings, kind: ActivityKind): String = strings[
    when (kind) {
        ActivityKind.STRENGTH -> StringKey.EXPLORE_KIND_STRENGTH
        ActivityKind.CARDIO -> StringKey.EXPLORE_KIND_CARDIO
    },
]

@Composable
fun ExploreContent(
    query: ExploreQuery,
    state: ExploreUiState,
    actions: ExploreActions,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    scrollResetToken: Int = 0,
) {
    // Posisi gulir tetap tersimpan saat ke Detail dan kembali, tapi dimulai dari atas lagi setelah pindah tab:
    // kunci simpanannya memuat [scrollResetToken] pada saat layar ini masuk, jadi token baru berarti tidak ada
    // posisi tersimpan untuk dipulihkan. Berada di luar `when` agar sama selama Loading dan Ready.
    val visitToken = remember { scrollResetToken }
    val listState = rememberSaveable(saver = LazyListState.Saver, key = "explore-list-$visitToken") { LazyListState() }
    val showSkeleton = rememberShowSkeleton(state == ExploreUiState.Loading, state is ExploreUiState.Ready)
    Box(modifier.fillMaxSize().background(ActivaPlanTheme.colors.bg).statusBarsPadding()) {
        if (showSkeleton) ExploreSkeleton() else when (state) {
            ExploreUiState.Loading -> Unit
            ExploreUiState.Error -> LoadError(actions.onReload)
            is ExploreUiState.Ready -> LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = ActivaDimens.screenPadding, end = ActivaDimens.screenPadding, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Filters(query, actions, onBack) }
                if (state.isEmpty) {
                    item { EmptyState(actions.onResetFilters) }
                } else if (query.kind == ActivityKind.CARDIO) {
                    items(state.cardioResults, key = { it.id }) {
                        CardioCard(it, picking = onBack != null) { actions.onOpenCardio(it.id) }
                    }
                } else {
                    items(state.results, key = { it.id }) {
                        ExerciseCard(it, picking = onBack != null) { actions.onOpenExercise(it.id) }
                    }
                }
            }
        }
    }
}

/** Kerangka Jelajah: kolom cari, mode, dan dua baris chip di atas daftar kartu, sama tinggi dengan isi aslinya. */
@Composable
private fun ExploreSkeleton() = ScreenListSkeleton(cardCount = 6) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(52.dp), ActivaShapes.button)
        SkeletonBlock(Modifier.fillMaxWidth().height(44.dp), ActivaShapes.button)
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { SkeletonBlock(Modifier.width(72.dp).height(36.dp), ActivaShapes.chip) }
            }
        }
    }
}

@Composable
private fun Filters(query: ExploreQuery, actions: ExploreActions, onBack: (() -> Unit)?) {
    val strings = ActivaPlanTheme.strings
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (onBack != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActivaBackButton(onBack)
                Text(strings[StringKey.PICKER_TITLE], style = ActivaPlanTheme.type.screenTitle, color = ActivaPlanTheme.colors.text)
            }
        } else {
            Text(strings[StringKey.EXPLORE_TITLE], style = ActivaPlanTheme.type.screenTitle, color = ActivaPlanTheme.colors.text)
        }
        Spacer(Modifier.height(12.dp))
        ActivaSegmentedControl(
            options = kinds.map { kindLabel(strings, it) },
            selectedIndex = kinds.indexOf(query.kind),
            onSelect = { actions.onKind(kinds[it]) },
        )
        Spacer(Modifier.height(4.dp))
        ActivaTextField(
            value = query.text,
            onValueChange = actions.onText,
            hint = strings[StringKey.EXPLORE_SEARCH_HINT],
            leadingIcon = ActivaIconKind.SEARCH,
            clearLabel = strings[StringKey.EXPLORE_CLEAR_SEARCH],
        )
        if (query.kind == ActivityKind.STRENGTH) {
            Spacer(Modifier.height(8.dp))
            ActivaSegmentedControl(
                options = modes.map { strings.label(it) },
                selectedIndex = modes.indexOf(query.mode),
                onSelect = { actions.onMode(modes[it]) },
            )
            ChipRow {
                (listOf<Category?>(null) + Category.entries).forEach { category ->
                    val isSelected = query.category == category
                    ActivaChip(
                        label = category?.let { strings.label(it) } ?: strings[StringKey.EXPLORE_ALL_CATEGORIES],
                        selected = isSelected,
                        onClick = { actions.onCategory(category) },
                        modifier = Modifier.semantics { selected = isSelected },
                    )
                }
            }
        }
        ChipRow {
            (listOf<Difficulty?>(null) + Difficulty.entries).forEach { difficulty ->
                val isSelected = query.difficulty == difficulty
                ActivaChip(
                    label = difficulty?.let { strings.label(it) } ?: strings[StringKey.EXPLORE_ALL_LEVELS],
                    selected = isSelected,
                    onClick = { actions.onDifficulty(difficulty) },
                    modifier = Modifier.semantics { selected = isSelected },
                )
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(exercise: Exercise, picking: Boolean, onClick: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    ActivaCard(Modifier.fillMaxWidth(), onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseThumbnail(exercise)
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = ActivaPlanTheme.type.cardTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LevelBadge(exercise.difficulty)
                    val zone = exercise.boardZone
                    if (zone != null) ZoneChip(zone) else TextBadge(strings.label(exercise.category))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    strings.summary(exercise),
                    style = ActivaPlanTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = c.textMuted,
                )
            }
            if (picking) {
                val addLabel = strings.format(StringKey.PICKER_ADD, exercise.name)
                Box(
                    Modifier.size(36.dp).background(c.primarySoft, CircleShape).semantics { contentDescription = addLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    ActivaIcon(ActivaIconKind.PLUS, c.primary, size = 18.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioCard(workout: CardioWorkout, picking: Boolean, onClick: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    ActivaCard(Modifier.fillMaxWidth(), onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(76.dp).clip(ActivaShapes.thumbnail).background(c.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                ActivaIcon(ActivaIconKind.PULSE, c.primary, size = 28.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(workout.name, style = ActivaPlanTheme.type.cardTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LevelBadge(workout.difficulty)
                    TextBadge(strings.label(workout.type))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    strings.cardioMeta(workout),
                    style = ActivaPlanTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = c.textMuted,
                )
            }
            if (picking) {
                val addLabel = strings.format(StringKey.PICKER_ADD, workout.name)
                Box(
                    Modifier.size(36.dp).background(c.primarySoft, CircleShape).semantics { contentDescription = addLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    ActivaIcon(ActivaIconKind.PLUS, c.primary, size = 18.dp)
                }
            }
        }
    }
}

/** Board bertitik zona untuk latihan board dan tali; latihan tanpa alat memakai ikon. */
@Composable
private fun ExerciseThumbnail(exercise: Exercise) {
    if (exercise.mode == Mode.NO_EQUIPMENT) {
        Box(
            Modifier.size(76.dp).clip(ActivaShapes.thumbnail).background(ActivaPlanTheme.colors.surfaceVar),
            contentAlignment = Alignment.Center,
        ) {
            ActivaIcon(ActivaIconKind.DUMBBELL, ActivaPlanTheme.colors.textMuted, size = 28.dp)
        }
    } else {
        BoardThumbnail(exercise.boardZone)
    }
}

@Composable
private fun EmptyState(onReset: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ActivaIcon(ActivaIconKind.SEARCH, c.textMuted, size = 32.dp)
        Spacer(Modifier.height(12.dp))
        Text(strings[StringKey.EXPLORE_EMPTY_TITLE], style = ActivaPlanTheme.type.cardTitle, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        ActivaButton(strings[StringKey.EXPLORE_RESET_FILTER], onReset, style = ButtonStyle.SOFT)
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

private fun previewExercise(id: String, name: String, difficulty: Difficulty, reps: Int, sets: Int = 3, rest: Int = 60) = Exercise(
    id = id, name = name, mode = Mode.PUSHUP_BOARD, category = Category.CHEST, difficulty = difficulty,
    boardZone = BoardZone.BLUE, primaryMuscles = listOf("Dada"), sets = sets, reps = reps, restSeconds = rest,
    steps = emptyList(), tips = emptyList(), commonMistakes = emptyList(),
)

private val previewResults = listOf(
    previewExercise("1", "Push-up Dada dari Lutut", Difficulty.EASY, reps = 8),
    previewExercise("2", "Push-up Dada Standar", Difficulty.MEDIUM, reps = 10),
    previewExercise("3", "Push-up Dada Kaki Ditinggikan", Difficulty.HARD, reps = 10, sets = 4, rest = 75),
)

private val previewActions = ExploreActions({}, {}, {}, {}, {}, {}, {})

@Composable
private fun ExplorePreview(darkTheme: Boolean, state: ExploreUiState, query: ExploreQuery) = ActivaPlanTheme(darkTheme = darkTheme) {
    ExploreContent(query, state, previewActions)
}

private val previewQuery = ExploreQuery(mode = Mode.PUSHUP_BOARD, category = Category.CHEST)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ExploreLightPreview() = ExplorePreview(false, ExploreUiState.Ready(previewResults), previewQuery)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ExploreDarkPreview() = ExplorePreview(true, ExploreUiState.Ready(previewResults), previewQuery)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ExploreEmptyLightPreview() =
    ExplorePreview(false, ExploreUiState.Ready(emptyList()), previewQuery.copy(text = "zzz"))

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ExploreNoEquipmentDarkPreview() = ExplorePreview(
    true,
    ExploreUiState.Ready(previewResults.map { it.copy(mode = Mode.NO_EQUIPMENT, boardZone = null) }),
    previewQuery.copy(mode = Mode.NO_EQUIPMENT),
)

private val previewCardio = listOf(
    CardioWorkout(
        id = "cd_walk_brisk", name = "Jalan Cepat", type = CardioType.WALK, difficulty = Difficulty.EASY, summary = "",
        blocks = listOf(CardioBlock(segments = listOf(CardioSegment(SegmentKind.STEADY, Motion.WALK, 1200, 5)))),
        tips = emptyList(), commonMistakes = emptyList(),
    ),
    CardioWorkout(
        id = "cd_fartlek", name = "Fartlek", type = CardioType.FARTLEK, difficulty = Difficulty.MEDIUM, summary = "",
        blocks = listOf(
            CardioBlock(repeat = 6, segments = listOf(CardioSegment(SegmentKind.WORK, Motion.RUN, 60, 7), CardioSegment(SegmentKind.RECOVERY, Motion.JOG, 120, 3))),
        ),
        tips = emptyList(), commonMistakes = emptyList(),
    ),
)

private val previewCardioQuery = ExploreQuery(mode = Mode.NO_EQUIPMENT, kind = ActivityKind.CARDIO)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ExploreCardioLightPreview() =
    ExplorePreview(false, ExploreUiState.Ready(emptyList(), previewCardio), previewCardioQuery)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ExploreCardioDarkPreview() =
    ExplorePreview(true, ExploreUiState.Ready(emptyList(), previewCardio), previewCardioQuery)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ExercisePickLightPreview() = ActivaPlanTheme(darkTheme = false) {
    ExploreContent(previewQuery, ExploreUiState.Ready(previewResults), previewActions, onBack = {})
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ExercisePickDarkPreview() = ActivaPlanTheme(darkTheme = true) {
    ExploreContent(previewQuery, ExploreUiState.Ready(previewResults), previewActions, onBack = {})
}
