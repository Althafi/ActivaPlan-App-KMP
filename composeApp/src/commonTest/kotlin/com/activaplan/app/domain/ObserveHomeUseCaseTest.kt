package com.activaplan.app.domain

import com.activaplan.app.data.InMemoryPreferencesDataStore
import com.activaplan.app.data.local.UserDataStore
import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseFiles
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.model.AppLanguage
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.GreetingPeriod
import com.activaplan.app.domain.model.HomeData
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineItem
import com.activaplan.app.domain.model.ScheduleEntry
import com.activaplan.app.domain.model.SessionLog
import com.activaplan.app.domain.model.SessionStatus
import com.activaplan.app.domain.time.LocalClock
import com.activaplan.app.domain.usecase.ObserveHomeUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserveHomeUseCaseTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val files = mapOf(
        ExerciseFiles.GENERAL to """{"schemaVersion":1,"exercises":[
            {"id":"a","name":"A","mode":"NO_EQUIPMENT","category":"CHEST","difficulty":"EASY","primaryMuscles":["chest"],
             "sets":3,"reps":10,"restSeconds":30,"steps":[],"tips":[],"commonMistakes":[]}],
            "templates":[{"id":"t_easy","name":"Mudah","difficulty":"EASY","items":["a"]}]}""",
        ExerciseFiles.PUSHUP_BOARD to """{"schemaVersion":1,"exercises":[
            {"id":"b","name":"B","mode":"PUSHUP_BOARD","category":"CHEST","difficulty":"MEDIUM","boardZone":"BLUE",
             "primaryMuscles":["chest"],"sets":3,"reps":10,"restSeconds":30,"steps":[],"tips":[],"commonMistakes":[]}],
            "templates":[{"id":"t_med","name":"Sedang","difficulty":"MEDIUM","items":["b"]}]}""",
        ExerciseFiles.I18N to """{"schemaVersion":1,"baseLanguage":"id","muscles":[],"packs":{}}""",
    )

    private val repository = UserDataRepository(
        store = UserDataStore(InMemoryPreferencesDataStore(), json),
        exercises = ExerciseRepository(ExerciseDataSource { files.getValue(it) }, json),
    )

    /** Senin 26 Oktober 2026, 16.00. */
    private val clock = LocalClock { LocalDateTime(2026, 10, 26, 16, 0) }

    private val observe = ObserveHomeUseCase(
        userData = repository,
        exercises = ExerciseRepository(ExerciseDataSource { files.getValue(it) }, json),
        clock = clock,
        ticks = flowOf(Unit),
    )

    private val routine = Routine("r1", "Pagi Sehat", listOf(RoutineItem("a", sets = 3, reps = 10, restSeconds = 30)))
    private val mondayEntry = ScheduleEntry("s1", dayOfWeek = 1, timeMinutes = 17 * 60, routineId = "r1", reminder = true)

    private suspend fun useIndonesian() = repository.updateSettings { it.copy(language = AppLanguage.ID) }

    private suspend fun useIndonesianWithAllGear() =
        repository.updateSettings { it.copy(language = AppLanguage.ID, ownedModes = Mode.entries.toSet()) }

    @Test
    fun emptyDataShowsEmptyTodayAndDefaultRecommendations() = runTest {
        useIndonesianWithAllGear()

        val home = observe().first()

        assertEquals(GreetingPeriod.AFTERNOON, home.greeting)
        assertNull(home.today)
        assertEquals(0, home.week.streak)
        assertEquals(listOf("t_easy", "t_med"), home.recommendations.map { it.id })
    }

    @Test
    fun defaultGearOnlyRecommendsNoEquipmentTemplates() = runTest {
        useIndonesian()

        assertEquals(listOf("t_easy"), observe().first().recommendations.map { it.id })
    }

    @Test
    fun startLevelFromOnboardingDrivesRecommendationsUntilASessionIsCompleted() = runTest {
        useIndonesianWithAllGear()
        repository.updateSettings { it.copy(startLevel = Difficulty.MEDIUM) }

        assertEquals(listOf("t_med"), observe().first().recommendations.map { it.id })
    }

    @Test
    fun combinesScheduleLogsAndRoutine() = runTest {
        useIndonesian()
        repository.saveRoutine(routine)
        repository.saveScheduleEntry(mondayEntry)
        repository.appendSessionLog(SessionLog("2026-10-26", "r1", SessionStatus.COMPLETED, 3, 3))

        val home = observe().first()

        val today = home.today!!
        assertEquals("r1", today.routineId)
        assertEquals(17 * 60, today.timeMinutes)
        assertEquals(1, today.exerciseCount)
        assertEquals(4, today.estimatedMinutes)
        assertFalse(today.passed)
        // Sudah selesai hari ini: kartu menunjuk Senin berikutnya dan terkunci sampai harinya tiba.
        assertTrue(today.locked)
        assertEquals(7, today.daysAhead)
        assertEquals(1, home.week.streak)
        assertTrue(home.week.days.first().completed)
        assertTrue(home.week.days.first().isToday)
    }

    @Test
    fun todaysWorkoutIsStartableUntilItIsCompleted() = runTest {
        useIndonesian()
        repository.saveRoutine(routine)
        repository.saveScheduleEntry(mondayEntry.copy(timeMinutes = 9 * 60))

        val today = observe().first().today!!

        // Jam 09.00 sudah lewat pada 16.00, tetapi jam hanya pengingat.
        assertTrue(today.passed)
        assertFalse(today.locked)
    }

    @Test
    fun scheduleOfAMissingRoutineIsIgnored() = runTest {
        useIndonesian()
        repository.saveScheduleEntry(mondayEntry.copy(routineId = "hilang"))

        assertNull(observe().first().today)
    }

    @Test
    fun emitsAgainWhenUserDataChanges() = runTest {
        useIndonesian()
        val seen = mutableListOf<HomeData>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observe().toList(seen) }
        runCurrent()
        assertNull(seen.last().today)

        repository.saveRoutine(routine)
        repository.saveScheduleEntry(mondayEntry)
        runCurrent()

        assertEquals("r1", seen.last().today?.routineId)
    }
}
