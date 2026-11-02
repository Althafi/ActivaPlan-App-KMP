package com.activaplan.app.domain

import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.WeeklyPlanRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.PlanDayType
import com.activaplan.app.domain.usecase.EstimateRoutineMinutesUseCase
import com.activaplan.app.domain.usecase.GetWeeklyPlanDetailUseCase
import com.activaplan.app.domain.usecase.PlanCatalog
import com.activaplan.app.domain.usecase.summarizePlans
import com.activaplan.app.domain.validation.WeeklyPlanValidator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Menjalankan rencana mingguan terhadap salinan JSON di `composeResources/files`. */
class WeeklyPlanDataTest {
    private val resources = File("src/commonMain/composeResources/files")
    private val source = ExerciseDataSource { name -> File(resources, name).readText() }
    private val json = Json { ignoreUnknownKeys = true }
    private val plans = WeeklyPlanRepository(source, json)
    private val exercises = ExerciseRepository(source, json)
    private val cardio = CardioRepository(source, json)

    private fun pattern(id: String, days: List<com.activaplan.app.domain.model.PlanDay>) = days.sortedBy { it.dayOfWeek }
        .joinToString("") { d ->
            when (d.type) {
                PlanDayType.STRENGTH -> "S"
                PlanDayType.CARDIO -> "C"
                PlanDayType.REST -> "R"
            }
        }.also { assertEquals(7, it.length, id) }

    @Test
    fun readsTwelvePlansFourPerLevel() = runTest {
        val all = plans.plans(Language.ID)
        assertEquals(12, all.size)
        Difficulty.entries.forEach { level ->
            val ofLevel = all.filter { it.difficulty == level }
            assertEquals(4, ofLevel.size, "$level")
            val withCardio = ofLevel.count { p -> p.days.any { it.type == PlanDayType.CARDIO } }
            assertEquals(2, withCardio, "$level: dua rencana dengan kardio, dua kekuatan saja")
        }
    }

    @Test
    fun catalogHasNoFindings() = runTest {
        val strength = exercises.catalog().exercises.associateBy { it.id }
        val cardioById = cardio.catalog().workouts.associateBy { it.id }
        val issues = WeeklyPlanValidator.validate(plans.catalog().plans, strength, cardioById)
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun englishTranslationHasNoFindings() = runTest {
        val c = plans.catalog()
        val issues = WeeklyPlanValidator.validateTranslation(c.plans, c.dictionary, Language.EN)
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun weeklyPatternsFollowTheBrief() = runTest {
        val byId = plans.plans(Language.ID).associateBy { it.id }
        // Senin, Rabu, Jumat kekuatan; Selasa, Kamis kardio; Sabtu dan Minggu istirahat.
        assertEquals("SCSCSRR", pattern("easy_mix", byId.getValue("plan_easy_mix").days))
        assertEquals("SSSSSRR", pattern("easy_full", byId.getValue("plan_easy_full").days))
        assertEquals("SCSCSCR", pattern("medium_mix", byId.getValue("plan_medium_mix").days))
        assertEquals("SSSSSSR", pattern("medium_full", byId.getValue("plan_medium_full").days))
        assertEquals("SCSSCCR", pattern("hard_mix", byId.getValue("plan_hard_mix").days))
        assertEquals("SSSSSSR", pattern("hard_full", byId.getValue("plan_hard_full").days))
        assertEquals("CSRCSCR", pattern("easy_cardio", byId.getValue("plan_easy_cardio").days))
        assertEquals("SRSRSRR", pattern("easy_3day", byId.getValue("plan_easy_3day").days))
        assertEquals("SRSRSRR", pattern("medium_3day", byId.getValue("plan_medium_3day").days))
        assertEquals("SCSCSRR", pattern("medium_core", byId.getValue("plan_medium_core").days))
        assertEquals("SRSRSRR", pattern("hard_3day", byId.getValue("plan_hard_3day").days))
        assertEquals("SCSCCCR", pattern("hard_run", byId.getValue("plan_hard_run").days))
    }

    @Test
    fun summariesCountDaysMinutesAndEquipment() = runTest {
        val catalog = PlanCatalog.load(exercises, cardio, Language.ID)
        val summaries = summarizePlans(plans.plans(Language.ID), catalog, EstimateRoutineMinutesUseCase()).associateBy { it.id }
        assertEquals(12, summaries.size)
        val easyMix = summaries.getValue("plan_easy_mix")
        assertEquals(5, easyMix.trainingDays)
        assertEquals(3, easyMix.strengthDays)
        assertEquals(2, easyMix.cardioDays)
        assertEquals(2, easyMix.restDays)
        assertTrue(easyMix.modes.isEmpty())
        assertTrue(easyMix.weeklyMinutes > 60)
        assertEquals(listOf(Mode.PUSHUP_BOARD, Mode.BOARD_ROPE), summaries.getValue("plan_medium_full").modes)
    }

    @Test
    fun detailListsEveryDayWithLocalizedNames() = runTest {
        val detail = assertNotNull(GetWeeklyPlanDetailUseCase(plans, exercises, cardio)("plan_easy_mix", Language.EN))
        assertEquals("Easy · Mixed 5 Days", detail.name)
        assertEquals(7, detail.days.size)
        assertEquals(listOf("Brisk Walk"), detail.days[1].itemNames)
        assertEquals(30, detail.days[1].minutes)
        assertEquals(PlanDayType.REST, detail.days[5].type)
        assertTrue(!detail.days[5].note.isNullOrBlank())
        assertEquals(5, detail.trainingDays)
    }
}
