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
import com.activaplan.app.domain.model.ActiveProgram
import com.activaplan.app.domain.model.ApplyPlanResult
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.PlanDay
import com.activaplan.app.domain.model.PlanDayType
import com.activaplan.app.domain.model.PlanItem
import com.activaplan.app.domain.model.Program
import com.activaplan.app.domain.model.ProgramWeek
import com.activaplan.app.domain.time.LocalClock
import com.activaplan.app.domain.usecase.ApplyWeeklyPlanUseCase
import com.activaplan.app.domain.usecase.ClearAllDataUseCase
import com.activaplan.app.domain.validation.ProgramValidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgramApplyTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val files = mapOf(
        ExerciseFiles.GENERAL to """{"schemaVersion":1,"exercises":[
            {"id":"a","name":"Latihan A","mode":"NO_EQUIPMENT","category":"CHEST","difficulty":"EASY","primaryMuscles":["chest"],
             "sets":3,"reps":10,"restSeconds":60,"steps":[],"tips":[],"commonMistakes":[]}],"templates":[]}""",
        ExerciseFiles.PUSHUP_BOARD to """{"schemaVersion":1,"exercises":[],"templates":[]}""",
        ExerciseFiles.CARDIO to """{"schemaVersion":1,"workouts":[
            {"id":"cd_x","name":"Fartlek","type":"FARTLEK","difficulty":"MEDIUM","summary":"s",
             "blocks":[{"repeat":1,"segments":[{"kind":"WARMUP","motion":"JOG","durationSeconds":60,"rpe":3}]}],
             "tips":[],"commonMistakes":[]}]}""",
        ExerciseFiles.PLANS to """{"schemaVersion":1,"plans":[{"id":"plan_t","name":"Rencana","difficulty":"EASY","summary":"s","tips":[],"days":[
            {"dayOfWeek":1,"type":"STRENGTH","name":"Hari Satu","items":[{"id":"a"}]}]}]}""",
        ExerciseFiles.PROGRAMS to """{"schemaVersion":1,"programs":[{"id":"prog_t","name":"Program","difficulty":"EASY","summary":"s","tips":["t"],"weeks":[
            {"week":1,"title":"Pekan 1","focus":"f1","days":[
                {"dayOfWeek":1,"type":"STRENGTH","name":"P1 Kekuatan","items":[{"id":"a","sets":2}]},
                {"dayOfWeek":2,"type":"CARDIO","name":"P1 Kardio","items":[{"id":"cd_x"}]},
                {"dayOfWeek":3,"type":"REST","name":"Libur","note":"n"}]},
            {"week":2,"title":"Pekan 2","focus":"f2","days":[
                {"dayOfWeek":1,"type":"STRENGTH","name":"P2 Kekuatan","items":[{"id":"a","sets":4}]},
                {"dayOfWeek":2,"type":"REST","name":"Libur","note":"n"},
                {"dayOfWeek":3,"type":"STRENGTH","name":"P2 Kekuatan B","items":[{"id":"a","sets":3}]}]}]}]}""",
        ExerciseFiles.I18N to """{"schemaVersion":1,"baseLanguage":"id","muscles":[],"packs":{}}""",
    )

    private val source = ExerciseDataSource { files.getValue(it) }
    private val exercises = ExerciseRepository(source, json)
    private val cardio = CardioRepository(source, json)
    private val plans = WeeklyPlanRepository(source, json)
    private val userData = UserDataRepository(UserDataStore(InMemoryPreferencesDataStore(), json), exercises, cardio)
    private val clock = LocalClock { LocalDateTime(2026, 10, 26, 16, 0) }
    private val apply = ApplyWeeklyPlanUseCase(userData, plans, exercises, cardio, clock = clock)
    private val time = 16 * 60 + 30

    @Test
    fun weekIdsResolveAsPlansButStayOutOfThePlanList() = runTest {
        assertEquals(listOf("plan_t"), plans.plans(Language.ID).map { it.id })
        val week2 = assertNotNull(plans.plan("prog_t_w2", Language.ID))
        assertEquals("Pekan 2", week2.name)
        assertEquals("prog_t", week2.programId)
        assertEquals(2, week2.week)
        assertNull(plans.plan("prog_t_w9", Language.ID))
    }

    @Test
    fun applyingNextWeekReplacesThePreviousWeeksRoutinesAndSchedule() = runTest {
        assertEquals(ApplyPlanResult.Applied(2), apply("prog_t_w1", Language.ID, time, reminder = false))
        assertEquals(setOf("prog_t_d1", "prog_t_d2"), userData.routines.first().map { it.id }.toSet())
        assertEquals(listOf(1, 2), userData.schedule.first().map { it.dayOfWeek }.sorted())

        assertEquals(ApplyPlanResult.Applied(2), apply("prog_t_w2", Language.ID, time, reminder = false))

        val routines = userData.routines.first().associateBy { it.id }
        // Hari 2 menjadi istirahat di pekan 2: rutinitas dan jadwalnya dari pekan 1 harus hilang, bukan tertinggal.
        assertEquals(setOf("prog_t_d1", "prog_t_d3"), routines.keys)
        assertEquals("P2 Kekuatan", routines.getValue("prog_t_d1").name)
        assertEquals(4, routines.getValue("prog_t_d1").items.single().sets)
        assertEquals(listOf(1, 3), userData.schedule.first().map { it.dayOfWeek }.sorted())
        assertTrue(userData.validated.first().issues.isEmpty())
    }

    @Test
    fun applyingAProgramWeekMarksItActive() = runTest {
        assertNull(userData.settings.first().activeProgram)

        apply("prog_t_w1", Language.ID, time, reminder = false)
        assertEquals(ActiveProgram("prog_t", 1, "2026-10-26"), userData.settings.first().activeProgram)

        apply("prog_t_w2", Language.ID, time, reminder = false)
        assertEquals(ActiveProgram("prog_t", 2, "2026-10-26"), userData.settings.first().activeProgram)
    }

    @Test
    fun aStandalonePlanOrAConflictDoesNotChangeTheActiveProgram() = runTest {
        apply("prog_t_w1", Language.ID, time, reminder = false)

        // Jam berbeda agar tidak bentrok dengan hari 1 milik program; rencana mandiri berhasil diterapkan.
        assertEquals(ApplyPlanResult.Applied(1), apply("plan_t", Language.ID, time + 180, reminder = false))
        assertEquals(1, userData.settings.first().activeProgram?.week)

        // Jadwal lain di hari dan jam yang sama membuat pekan 2 ditolak; program aktif tetap pekan 1.
        userData.saveRoutine(Routine("other", "Lain", listOf(RoutineItem("a", 3, 10, null, 60))))
        userData.saveScheduleEntry(ScheduleEntry("s_other", dayOfWeek = 3, timeMinutes = time, routineId = "other", reminder = false))
        assertIs<ApplyPlanResult.Conflict>(apply("prog_t_w2", Language.ID, time, reminder = false))
        assertEquals(1, userData.settings.first().activeProgram?.week)
    }

    @Test
    fun clearingAllDataAlsoClearsTheActiveProgram() = runTest {
        apply("prog_t_w1", Language.ID, time, reminder = false)

        ClearAllDataUseCase(userData)()

        assertNull(userData.settings.first().activeProgram)
    }

    @Test
    fun programsRepositoryExposesLocalizedPrograms() = runTest {
        val program = assertNotNull(plans.program("prog_t", Language.ID))
        assertEquals(2, program.weeks.size)
        assertEquals(listOf("prog_t_w1", "prog_t_w2"), program.weekPlans.map { it.id })
    }

    @Test
    fun missingProgramsFileDoesNotBreakWeeklyPlans() = runTest {
        val withoutPrograms = WeeklyPlanRepository(ExerciseDataSource { name -> if (name == ExerciseFiles.PROGRAMS) error("tidak ada") else files.getValue(name) }, json)

        assertEquals(listOf("plan_t"), withoutPrograms.plans(Language.ID).map { it.id })
        assertEquals(emptyList(), withoutPrograms.programs(Language.ID))
        assertNull(withoutPrograms.plan("prog_t_w1", Language.ID))
    }

    // ---- validator ----

    private fun day(dow: Int, type: PlanDayType, id: String? = null) = PlanDay(
        dayOfWeek = dow,
        type = type,
        name = "Hari $dow",
        items = if (type == PlanDayType.REST) emptyList() else listOf(PlanItem(id ?: "a")),
        note = if (type == PlanDayType.REST) "n" else null,
    )

    private fun fullWeek(week: Int, withCardio: Boolean = true) = ProgramWeek(
        week = week,
        title = "Pekan $week",
        focus = "f",
        days = listOf(
            day(1, PlanDayType.STRENGTH),
            if (withCardio) day(2, PlanDayType.CARDIO, "cd_x") else day(2, PlanDayType.STRENGTH),
            day(3, PlanDayType.STRENGTH), day(4, PlanDayType.REST), day(5, PlanDayType.STRENGTH),
            day(6, PlanDayType.REST), day(7, PlanDayType.REST),
        ),
    )

    private suspend fun validate(program: Program) = ProgramValidator.validate(
        listOf(program),
        exercises.catalog().exercises.associateBy { it.id },
        cardio.catalog().workouts.associateBy { it.id },
    )

    @Test
    fun validatorAcceptsAProperProgram() = runTest {
        val program = Program("p", "P", Difficulty.MEDIUM, "s", weeks = listOf(fullWeek(1), fullWeek(2)))

        assertTrue(validate(program).isEmpty(), validate(program).joinToString())
    }

    @Test
    fun validatorRejectsTooManyWeeksAndMissingMix() = runTest {
        val tooLong = Program("p", "P", Difficulty.MEDIUM, "s", weeks = (1..5).map { fullWeek(it) })
        assertTrue(validate(tooLong).any { "paling banyak" in it.message })

        val noCardio = Program("p", "P", Difficulty.MEDIUM, "s", weeks = listOf(fullWeek(1, withCardio = false)))
        assertTrue(validate(noCardio).any { "memadukan kekuatan dan kardio" in it.message })

        val skipped = Program("p", "P", Difficulty.MEDIUM, "s", weeks = listOf(fullWeek(1), fullWeek(3)))
        assertTrue(validate(skipped).any { "berurutan" in it.message })
    }
}
