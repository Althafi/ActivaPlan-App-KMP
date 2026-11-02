package com.activaplan.app.domain

import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.WeeklyPlanRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.PlanDayType
import com.activaplan.app.domain.usecase.EstimateRoutineMinutesUseCase
import com.activaplan.app.domain.usecase.GetProgramDetailUseCase
import com.activaplan.app.domain.usecase.PlanCatalog
import com.activaplan.app.domain.usecase.summarizePrograms
import com.activaplan.app.domain.validation.ProgramValidator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Menjalankan program 4 minggu terhadap salinan JSON di `composeResources/files`. */
class ProgramDataTest {
    private val resources = File("src/commonMain/composeResources/files")
    private val source = ExerciseDataSource { name -> File(resources, name).readText() }
    private val json = Json { ignoreUnknownKeys = true }
    private val plans = WeeklyPlanRepository(source, json)
    private val exercises = ExerciseRepository(source, json)
    private val cardio = CardioRepository(source, json)

    private fun pattern(days: List<com.activaplan.app.domain.model.PlanDay>) = days.sortedBy { it.dayOfWeek }.joinToString("") {
        when (it.type) {
            PlanDayType.STRENGTH -> "S"
            PlanDayType.CARDIO -> "C"
            PlanDayType.REST -> "R"
        }
    }

    @Test
    fun hasOneFourWeekProgramPerLevel() = runTest {
        val programs = plans.programs(Language.ID)
        assertEquals(Difficulty.entries.toSet(), programs.map { it.difficulty }.toSet())
        assertEquals(3, programs.size)
        programs.forEach { assertEquals(listOf(1, 2, 3, 4), it.weeks.map { w -> w.week }, it.id) }
    }

    @Test
    fun catalogHasNoFindings() = runTest {
        val issues = ProgramValidator.validate(
            plans.catalog().programs,
            exercises.catalog().exercises.associateBy { it.id },
            cardio.catalog().workouts.associateBy { it.id },
        )
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun englishTranslationHasNoFindings() = runTest {
        val c = plans.catalog()
        val issues = ProgramValidator.validateTranslation(c.programs, c.dictionary, Language.EN)
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun weeklyPatternsFollowTheDesign() = runTest {
        val byId = plans.programs(Language.ID).associateBy { it.id }
        fun patterns(id: String) = byId.getValue(id).weeks.map { pattern(it.days) }
        assertEquals(listOf("SRCRSCR", "SCSRSCR", "SCSRSCR", "SCRSRCR"), patterns("prog_easy_4w"))
        assertEquals(listOf("SCSCSRR", "SCSCSCR", "SCSCSCR", "SCRSRCR"), patterns("prog_medium_4w"))
        assertEquals(listOf("SCSCSSR", "SCSCSSR", "SCSCSSR", "SRSCRSR"), patterns("prog_hard_4w"))
    }

    @Test
    fun loadProgressesThroughWeekThreeThenDropsInWeekFour() = runTest {
        val catalog = PlanCatalog.load(exercises, cardio, Language.ID)
        summarizePrograms(plans.programs(Language.ID), catalog, EstimateRoutineMinutesUseCase()).forEach { program ->
            val minutes = program.weeks.map { it.weeklyMinutes }
            assertTrue(minutes[0] < minutes[1] && minutes[1] < minutes[2], "${program.id}: volume harus naik pekan 1 ke 3 $minutes")
            assertTrue(minutes[3] < minutes[0] * 1.0 + 1, "${program.id}: pekan 4 tidak boleh lebih berat dari pekan 1 $minutes")
        }
    }

    @Test
    fun everyWeekMixesStrengthAndCardio() = runTest {
        val catalog = PlanCatalog.load(exercises, cardio, Language.ID)
        summarizePrograms(plans.programs(Language.ID), catalog, EstimateRoutineMinutesUseCase()).forEach { program ->
            program.weeks.forEach { week ->
                assertTrue(week.strengthDays >= 2 && week.cardioDays >= 1, "${week.id}: ${week.strengthDays}S ${week.cardioDays}C")
            }
        }
    }

    @Test
    fun detailExposesWeeksWithFocusInEnglish() = runTest {
        val detail = assertNotNull(GetProgramDetailUseCase(plans, exercises, cardio)("prog_medium_4w", Language.EN))
        assertEquals("Medium · 4-Week Strength Builder", detail.name)
        assertEquals(4, detail.weeks.size)
        assertEquals("prog_medium_4w_w3", detail.weeks[2].plan.id)
        assertEquals("Medium · Week 3: Peak", detail.weeks[2].plan.name)
        assertTrue(detail.weeks[2].focus.isNotBlank())
        assertTrue(detail.modes.isNotEmpty())
    }
}
