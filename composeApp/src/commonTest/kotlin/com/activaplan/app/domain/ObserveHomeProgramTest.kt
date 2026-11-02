package com.activaplan.app.domain

import com.activaplan.app.data.InMemoryPreferencesDataStore
import com.activaplan.app.data.local.UserDataStore
import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseFiles
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.data.repository.WeeklyPlanRepository
import com.activaplan.app.domain.model.ActiveProgram
import com.activaplan.app.domain.time.LocalClock
import com.activaplan.app.domain.usecase.GetProgramDetailUseCase
import com.activaplan.app.domain.usecase.ObserveHomeUseCase
import com.activaplan.app.domain.i18n.Language
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kemajuan program aktif di Beranda dan penanda pekan aktif di layar program. */
class ObserveHomeProgramTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val files = mapOf(
        ExerciseFiles.GENERAL to """{"schemaVersion":1,"exercises":[
            {"id":"a","name":"A","mode":"NO_EQUIPMENT","category":"CHEST","difficulty":"EASY","primaryMuscles":["chest"],
             "sets":3,"reps":10,"restSeconds":30,"steps":[],"tips":[],"commonMistakes":[]}],"templates":[]}""",
        ExerciseFiles.PUSHUP_BOARD to """{"schemaVersion":1,"exercises":[],"templates":[]}""",
        ExerciseFiles.CARDIO to """{"schemaVersion":1,"workouts":[]}""",
        ExerciseFiles.PLANS to """{"schemaVersion":1,"plans":[]}""",
        ExerciseFiles.PROGRAMS to """{"schemaVersion":1,"programs":[{"id":"prog_t","name":"Program Uji","difficulty":"EASY","summary":"s","tips":[],"weeks":[
            {"week":1,"title":"Pekan 1","focus":"f","days":[{"dayOfWeek":1,"type":"STRENGTH","name":"S","items":[{"id":"a"}]},{"dayOfWeek":2,"type":"REST","name":"R","note":"n"}]},
            {"week":2,"title":"Pekan 2","focus":"f","days":[{"dayOfWeek":1,"type":"STRENGTH","name":"S","items":[{"id":"a"}]},{"dayOfWeek":2,"type":"REST","name":"R","note":"n"}]}]}]}""",
        ExerciseFiles.I18N to """{"schemaVersion":1,"baseLanguage":"id","muscles":[],"packs":{}}""",
    )

    private val source = ExerciseDataSource { files.getValue(it) }
    private val exercises = ExerciseRepository(source, json)
    private val plans = WeeklyPlanRepository(source, json)
    private val userData = UserDataRepository(UserDataStore(InMemoryPreferencesDataStore(), json), exercises, CardioRepository(source, json))

    private fun observe(day: Int) = ObserveHomeUseCase(
        userData = userData,
        exercises = exercises,
        clock = LocalClock { LocalDateTime(2026, 10, day, 9, 0) },
        ticks = flowOf(Unit),
        plans = plans,
    )

    private suspend fun activate(programId: String = "prog_t", week: Int, startedOn: String = "2026-10-01") =
        userData.updateSettings { it.copy(activeProgram = ActiveProgram(programId, week, startedOn)) }

    @Test
    fun noActiveProgramMeansNoCard() = runTest {
        assertNull(observe(10).invoke().first().program)
    }

    @Test
    fun midWeekShowsTheDayAndOffersTheNextWeek() = runTest {
        activate(week = 1)

        val program = assertNotNull(observe(3).invoke().first().program)

        assertEquals("Program Uji", program.programName)
        assertEquals(1, program.week)
        assertEquals(2, program.totalWeeks)
        assertEquals(3, program.dayInWeek)
        assertFalse(program.weekCompleted)
        assertEquals(2, program.nextWeek)
        assertEquals("prog_t_w2", program.nextPlanId)
        assertFalse(program.finished)
    }

    @Test
    fun afterSevenDaysTheWeekIsCompleted() = runTest {
        activate(week = 1)

        val program = assertNotNull(observe(8).invoke().first().program)

        assertTrue(program.weekCompleted)
        assertFalse(program.finished)
    }

    @Test
    fun lastWeekHasNoNextAndFinishesAfterSevenDays() = runTest {
        activate(week = 2)

        val running = assertNotNull(observe(4).invoke().first().program)
        assertNull(running.nextPlanId)
        assertFalse(running.finished)

        assertTrue(assertNotNull(observe(9).invoke().first().program).finished)
    }

    @Test
    fun unknownProgramOrBrokenDateIsHandledGracefully() = runTest {
        activate(programId = "hilang", week = 1)
        assertNull(observe(3).invoke().first().program)

        activate(week = 1, startedOn = "bukan-tanggal")
        assertEquals(1, assertNotNull(observe(3).invoke().first().program).dayInWeek)
    }

    @Test
    fun programDetailMarksTheActiveWeek() = runTest {
        val detail = GetProgramDetailUseCase(plans, exercises, CardioRepository(source, json), userData = userData)

        assertNull(assertNotNull(detail("prog_t", Language.ID)).activeWeek)

        activate(week = 2)
        assertEquals(2, assertNotNull(detail("prog_t", Language.ID)).activeWeek)

        activate(programId = "lain", week = 1)
        assertNull(assertNotNull(detail("prog_t", Language.ID)).activeWeek)
    }
}
