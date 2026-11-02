package com.activaplan.app.domain

import com.activaplan.app.data.InMemoryPreferencesDataStore
import com.activaplan.app.data.local.UserDataStore
import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseFiles
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.data.repository.WeeklyPlanRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.ApplyPlanResult
import com.activaplan.app.domain.model.ConflictPolicy
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineItem
import com.activaplan.app.domain.model.ScheduleEntry
import com.activaplan.app.domain.usecase.ApplyWeeklyPlanUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WeeklyPlanApplyTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val files = mapOf(
        ExerciseFiles.GENERAL to """{"schemaVersion":1,"exercises":[
            {"id":"a","name":"Latihan A","mode":"NO_EQUIPMENT","category":"CHEST","difficulty":"EASY","primaryMuscles":["chest"],
             "sets":3,"reps":10,"restSeconds":60,"steps":[],"tips":[],"commonMistakes":[]},
            {"id":"t","name":"Latihan T","mode":"NO_EQUIPMENT","category":"ABS","difficulty":"EASY","primaryMuscles":["abs"],
             "sets":3,"durationSeconds":20,"restSeconds":30,"steps":[],"tips":[],"commonMistakes":[]}],"templates":[]}""",
        ExerciseFiles.PUSHUP_BOARD to """{"schemaVersion":1,"exercises":[],"templates":[]}""",
        ExerciseFiles.CARDIO to """{"schemaVersion":1,"workouts":[
            {"id":"cd_x","name":"Fartlek","type":"FARTLEK","difficulty":"MEDIUM","summary":"s",
             "blocks":[{"repeat":1,"segments":[{"kind":"WARMUP","motion":"JOG","durationSeconds":60,"rpe":3}]},
                       {"repeat":2,"segments":[{"kind":"WORK","motion":"RUN","durationSeconds":30,"rpe":7},
                                               {"kind":"RECOVERY","motion":"JOG","durationSeconds":20,"rpe":3}]}],
             "tips":[],"commonMistakes":[]}]}""",
        ExerciseFiles.PLANS to """{"schemaVersion":1,"plans":[{"id":"plan_t","name":"Rencana","difficulty":"EASY","summary":"s","tips":[],"days":[
            {"dayOfWeek":1,"type":"STRENGTH","name":"Hari Satu","items":[{"id":"a","sets":2,"reps":8},{"id":"t","seconds":25}]},
            {"dayOfWeek":2,"type":"CARDIO","name":"Hari Dua","items":[{"id":"cd_x"}]},
            {"dayOfWeek":3,"type":"REST","name":"Libur","note":"n"}]}]}""",
        ExerciseFiles.I18N to """{"schemaVersion":1,"baseLanguage":"id","muscles":[],"packs":{}}""",
    )

    private val source = ExerciseDataSource { files.getValue(it) }
    private val exercises = ExerciseRepository(source, json)
    private val cardio = CardioRepository(source, json)
    private val plans = WeeklyPlanRepository(source, json)
    private val userData = UserDataRepository(UserDataStore(InMemoryPreferencesDataStore(), json), exercises, cardio)
    private val apply = ApplyWeeklyPlanUseCase(userData, plans, exercises, cardio)

    private val monday = 16 * 60 + 30

    @Test
    fun appliesRoutinesAndScheduleForTrainingDaysOnly() = runTest {
        val result = apply("plan_t", Language.ID, monday, reminder = true)
        assertEquals(ApplyPlanResult.Applied(2), result)

        val routines = userData.routines.first().associateBy { it.id }
        assertEquals(setOf("plan_t_d1", "plan_t_d2"), routines.keys)
        assertEquals("Hari Satu", routines.getValue("plan_t_d1").name)
        // Timpaan plan: set 2 x 8 rep untuk A; 25 detik untuk T; bawaan sisanya.
        assertEquals(
            listOf(RoutineItem("a", 2, 8, null, 60), RoutineItem("t", 3, null, 25, 30)),
            routines.getValue("plan_t_d1").items,
        )
        // Kardio: satu set tanpa istirahat, durasi = total detik (60 + 2 x 50).
        assertEquals(listOf(RoutineItem("cd_x", 1, null, 160, 0)), routines.getValue("plan_t_d2").items)

        val schedule = userData.schedule.first().sortedBy { it.dayOfWeek }
        assertEquals(listOf(1, 2), schedule.map { it.dayOfWeek })
        assertTrue(schedule.all { it.timeMinutes == monday && it.reminder })
        assertEquals(listOf("plan_t_d1", "plan_t_d2"), schedule.map { it.routineId })
        assertTrue(userData.validated.first().issues.isEmpty())
    }

    @Test
    fun applyingAgainRefreshesInsteadOfDuplicating() = runTest {
        apply("plan_t", Language.ID, monday, reminder = true)
        userData.saveRoutine(Routine("plan_t_d1", "Diubah pengguna", listOf(RoutineItem("a", 1, 1, null, 15))))

        assertEquals(ApplyPlanResult.Applied(2), apply("plan_t", Language.ID, 7 * 60, reminder = false))

        assertEquals(2, userData.routines.first().size)
        assertEquals("Hari Satu", userData.routines.first().first { it.id == "plan_t_d1" }.name)
        val schedule = userData.schedule.first()
        assertEquals(2, schedule.size)
        assertTrue(schedule.all { it.timeMinutes == 7 * 60 && !it.reminder })
    }

    private suspend fun seedConflict() {
        userData.saveRoutine(Routine("other", "Lain", listOf(RoutineItem("a", 3, 10, null, 60))))
        userData.saveScheduleEntry(ScheduleEntry("s_other", dayOfWeek = 1, timeMinutes = monday, routineId = "other", reminder = false))
    }

    @Test
    fun conflictAsksFirstAndChangesNothing() = runTest {
        seedConflict()
        val result = apply("plan_t", Language.ID, monday, reminder = true)
        assertEquals(listOf("s_other"), assertIs<ApplyPlanResult.Conflict>(result).conflicts.map { it.id })
        assertEquals(listOf("other"), userData.routines.first().map { it.id })
        assertEquals(listOf("s_other"), userData.schedule.first().map { it.id })
    }

    @Test
    fun replaceRemovesConflictingScheduleAndKeepBothKeepsIt() = runTest {
        seedConflict()
        assertEquals(ApplyPlanResult.Applied(2), apply("plan_t", Language.ID, monday, true, ConflictPolicy.REPLACE))
        assertEquals(setOf("plan_t_d1_slot", "plan_t_d2_slot"), userData.schedule.first().map { it.id }.toSet())
        assertEquals(3, userData.routines.first().size)

        userData.saveScheduleEntry(ScheduleEntry("s_other", 1, monday, "other", false))
        assertEquals(ApplyPlanResult.Applied(2), apply("plan_t", Language.ID, monday, true, ConflictPolicy.KEEP_BOTH))
        assertEquals(3, userData.schedule.first().size)
    }

    @Test
    fun unknownPlanAndBadTimeAreRejected() = runTest {
        assertEquals(ApplyPlanResult.NotFound, apply("plan_hilang", Language.ID, monday, true))
        assertFailsWith<IllegalArgumentException> { apply("plan_t", Language.ID, 24 * 60, true) }
        assertTrue(userData.routines.first().isEmpty())
    }
}
