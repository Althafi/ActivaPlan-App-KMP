package com.activaplan.app.domain

import com.activaplan.app.data.InMemoryPreferencesDataStore
import com.activaplan.app.data.local.UserDataStore
import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseFiles
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.AddToRoutineResult
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
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineItem
import com.activaplan.app.domain.model.SegmentKind
import com.activaplan.app.domain.model.SessionExercise
import com.activaplan.app.domain.model.SessionOverlay
import com.activaplan.app.domain.model.SessionPhase
import com.activaplan.app.domain.model.SessionPlan
import com.activaplan.app.domain.model.SessionStatus
import com.activaplan.app.domain.session.SessionState
import com.activaplan.app.domain.usecase.AddExerciseToRoutineUseCase
import com.activaplan.app.domain.usecase.FilterCardioUseCase
import com.activaplan.app.domain.usecase.LoadSessionPlanUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kardio dalam sesi: tiap segmen menjadi langkah berdurasi yang pindah sendiri saat waktunya habis. */
class CardioSessionTest {

    private fun seg(kind: SegmentKind, sec: Int, rpe: Int = 5) = CardioSegment(kind, Motion.JOG, sec, rpe)

    private val workout = CardioWorkout(
        id = "cd_x", name = "Fartlek", type = CardioType.FARTLEK, difficulty = Difficulty.MEDIUM, summary = "s",
        blocks = listOf(
            CardioBlock(segments = listOf(seg(SegmentKind.WARMUP, 60))),
            CardioBlock(repeat = 2, segments = listOf(seg(SegmentKind.WORK, 30, 7), seg(SegmentKind.RECOVERY, 20, 3))),
        ),
        tips = emptyList(), commonMistakes = emptyList(),
    )

    private fun strength(id: String) = SessionExercise(
        Exercise(
            id = id, name = "Latihan $id", mode = Mode.NO_EQUIPMENT, category = Category.CHEST, difficulty = Difficulty.EASY,
            primaryMuscles = emptyList(), sets = 1, reps = 10, restSeconds = 0, steps = emptyList(), tips = emptyList(),
            commonMistakes = emptyList(),
        ),
        sets = 1, reps = 10, durationSeconds = null, restSeconds = 0,
    )

    private fun plan(items: List<SessionExercise>) = SessionPlan(
        routineId = "r1", routineName = "R", exercises = items, catalog = emptyMap(),
        requiresSafetyCheck = false, safetyNotes = emptyList(), skippedItems = 0,
    )

    private fun cardioState() = SessionState.start(plan(SessionExercise.ofCardio(workout, 0)))

    @Test
    fun workoutOpensIntoOneStepPerSegment() {
        val steps = SessionExercise.ofCardio(workout, 3)
        assertEquals(5, steps.size)
        assertEquals(listOf(60, 30, 20, 30, 20), steps.map { it.durationSeconds })
        assertTrue(steps.all { it.isTimed && it.sets == 1 && it.restSeconds == 0 })
        assertEquals(listOf(1, 2, 3, 4, 5), steps.map { it.cardio!!.position })
        assertTrue(steps.all { it.cardio!!.itemIndex == 3 && it.cardio!!.total == 5 && it.cardio!!.workoutName == "Fartlek" })
    }

    @Test
    fun expiredSegmentAdvancesWithoutFailedSheetAndCarriesLeftover() {
        val after = cardioState().advance(61_000)
        assertEquals(1, after.index)
        assertEquals(SessionOverlay.NONE, after.overlay)
        assertEquals(listOf(1, 0, 0, 0, 0), after.setsDone)
        // 1 detik dari ketukan terakhir dipakai segmen berikutnya (30 dtk).
        assertEquals(29_000L, assertIs<SessionPhase.Working>(after.phase).remainingMillis)
        // Waktu aktif tidak dihitung dua kali.
        assertEquals(61_000L, after.activeMillis)
    }

    @Test
    fun skippingSegmentMovesOnAndCountsAsDone() {
        val after = cardioState().requestSkip()
        assertEquals(1, after.index)
        assertEquals(SessionOverlay.NONE, after.overlay)
        assertEquals(1, after.setsDone[0])
    }

    @Test
    fun lastSegmentFinishesSessionAsCompleted() {
        var state = cardioState()
        repeat(5) { state = state.requestSkip() }
        val finished = assertIs<SessionPhase.Finished>(state.phase)
        assertEquals(SessionStatus.COMPLETED, finished.summary.status)
        assertEquals(5, finished.summary.completedSets)
        // Kardio dihitung satu latihan, bukan lima.
        assertEquals(1, finished.summary.totalExercises)
        assertEquals(1, finished.summary.completedExercises)
    }

    @Test
    fun stoppingMidWorkoutIsPartialAndCountsNoFinishedExercise() {
        val stopped = cardioState().advance(61_000).requestStop().confirmStop()
        val summary = assertIs<SessionPhase.Finished>(stopped.phase).summary
        assertEquals(SessionStatus.PARTIAL, summary.status)
        assertEquals(1, summary.completedSets)
        assertEquals(5, summary.totalSets)
        assertEquals(0, summary.completedExercises)
    }

    @Test
    fun pausedCardioDoesNotAdvance() {
        val paused = cardioState().togglePause()
        assertTrue(paused.advance(120_000) === paused)
    }

    @Test
    fun mixedRoutineCountsCardioAsOneExercise() {
        val state = SessionState.start(plan(listOf(strength("a")) + SessionExercise.ofCardio(workout, 1) + strength("b")))
        assertEquals(3, state.groupCount)
        assertEquals(0, state.groupIndex)
        val atCardio = state.completeSet()
        assertEquals(1, atCardio.groupIndex)
        var later = atCardio
        repeat(5) { later = later.requestSkip() }
        assertEquals(2, later.groupIndex)
        assertNull(later.current.cardio)
    }

    // region Use case

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val files = mapOf(
        ExerciseFiles.GENERAL to """{"schemaVersion":1,"exercises":[
            {"id":"a","name":"Latihan A","mode":"NO_EQUIPMENT","category":"CHEST","difficulty":"MEDIUM","primaryMuscles":["chest"],
             "sets":3,"reps":10,"restSeconds":60,"steps":[],"tips":[],"commonMistakes":[]}],"templates":[]}""",
        ExerciseFiles.PUSHUP_BOARD to """{"schemaVersion":1,"exercises":[],"templates":[]}""",
        ExerciseFiles.CARDIO to """{"schemaVersion":1,"workouts":[
            {"id":"cd_x","name":"Fartlek","type":"FARTLEK","difficulty":"MEDIUM","summary":"s",
             "blocks":[{"repeat":1,"segments":[{"kind":"WARMUP","motion":"JOG","durationSeconds":60,"rpe":3}]},
                       {"repeat":2,"segments":[{"kind":"WORK","motion":"RUN","durationSeconds":30,"rpe":7},
                                               {"kind":"RECOVERY","motion":"JOG","durationSeconds":20,"rpe":3}]}],
             "tips":[],"commonMistakes":[]}]}""",
        ExerciseFiles.I18N to """{"schemaVersion":1,"baseLanguage":"id","muscles":[],"packs":{}}""",
    )

    private val source = ExerciseDataSource { files.getValue(it) }
    private val exercises = ExerciseRepository(source, json)
    private val cardio = CardioRepository(source, json)
    private val userData = UserDataRepository(UserDataStore(InMemoryPreferencesDataStore(), json), exercises, cardio)

    @Test
    fun mixedRoutineLoadsStrengthThenCardioSegments() = runTest {
        userData.saveRoutine(
            Routine("r1", "Campur", listOf(RoutineItem("a", 3, 10, null, 60), RoutineItem("cd_x", 1, null, 140, 0), RoutineItem("hilang", 1, 1, null, 0))),
        )
        val plan = assertNotNull(LoadSessionPlanUseCase(userData, exercises, cardio)("r1", Language.ID))
        assertEquals(1 + 5, plan.exercises.size)
        assertEquals(1, plan.skippedItems)
        assertNull(plan.exercises.first().cardio)
        assertEquals(1, plan.exercises[1].cardio?.itemIndex)
    }

    @Test
    fun withoutCardioRepositoryCardioItemsAreSkipped() = runTest {
        userData.saveRoutine(Routine("r1", "R", listOf(RoutineItem("cd_x", 1, null, 140, 0))))
        assertNull(LoadSessionPlanUseCase(userData, exercises)("r1", Language.ID))
    }

    @Test
    fun cardioIdsSurviveValidationAndAddUsesTotalSeconds() = runTest {
        val add = AddExerciseToRoutineUseCase(userData) { "new" }
        val workout = assertNotNull(cardio.workout("cd_x", Language.ID))
        assertIs<AddToRoutineResult.Added>(add.toNew("Lari", workout))

        val validated = userData.validated.first()
        val item = validated.routines.single().items.single()
        assertEquals("cd_x", item.exerciseId)
        assertEquals(1, item.sets)
        assertEquals(workout.totalSeconds, item.durationSeconds)
        assertEquals(0, item.restSeconds)
        assertTrue(validated.issues.isEmpty())
    }

    @Test
    fun filterMatchesLevelAndText() {
        val easy = workout.copy(id = "e", name = "Jalan Cepat", difficulty = Difficulty.EASY, summary = "dasar")
        val all = listOf(workout, easy)
        val filter = FilterCardioUseCase()
        val q = ExploreQuery(Mode.NO_EQUIPMENT)
        assertEquals(listOf("cd_x", "e"), filter(all, q).map { it.id })
        assertEquals(listOf("e"), filter(all, q.copy(difficulty = Difficulty.EASY)).map { it.id })
        assertEquals(listOf("e"), filter(all, q.copy(text = "JALAN")).map { it.id })
        assertEquals(listOf("e"), filter(all, q.copy(text = "dasar")).map { it.id })
        assertTrue(filter(all, q.copy(text = "zzz")).isEmpty())
    }

    // endregion
}
