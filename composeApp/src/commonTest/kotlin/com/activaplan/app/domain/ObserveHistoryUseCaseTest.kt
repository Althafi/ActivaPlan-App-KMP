package com.activaplan.app.domain

import com.activaplan.app.data.InMemoryPreferencesDataStore
import com.activaplan.app.data.local.UserDataStore
import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseFiles
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineItem
import com.activaplan.app.domain.model.SessionLog
import com.activaplan.app.domain.model.SessionStatus
import com.activaplan.app.domain.usecase.ObserveHistoryUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserveHistoryUseCaseTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val files = mapOf(
        ExerciseFiles.GENERAL to """{"schemaVersion":1,"exercises":[
            {"id":"a","name":"A","mode":"NO_EQUIPMENT","category":"CHEST","difficulty":"EASY","primaryMuscles":["chest"],
             "sets":3,"reps":10,"restSeconds":30,"steps":[],"tips":[],"commonMistakes":[]}],"templates":[]}""",
        ExerciseFiles.PUSHUP_BOARD to """{"schemaVersion":1,"exercises":[],"templates":[]}""",
        ExerciseFiles.I18N to """{"schemaVersion":1,"baseLanguage":"id","muscles":[],"packs":{}}""",
    )

    private val repository = UserDataRepository(
        store = UserDataStore(InMemoryPreferencesDataStore(), json),
        exercises = ExerciseRepository(ExerciseDataSource { files.getValue(it) }, json),
    )
    private val observe = ObserveHistoryUseCase(repository)

    private fun log(date: String, routineId: String = "r1", status: SessionStatus = SessionStatus.COMPLETED, distance: Int = 0) =
        SessionLog(date, routineId, status, completedSets = 3, totalSets = 3, distanceMeters = distance)

    @Test
    fun emptyWhenNoLogs() = runTest {
        val data = observe().first()

        assertTrue(data.isEmpty)
        assertEquals(0, data.totalSessions)
        assertTrue(data.keepHistory)
    }

    @Test
    fun newestFirstGroupedByMonth() = runTest {
        repository.saveRoutine(Routine("r1", "Pagi", listOf(RoutineItem("a", sets = 3, reps = 10, restSeconds = 30))))
        repository.appendSessionLog(log("2026-09-30"))
        repository.appendSessionLog(log("2026-10-02"))
        repository.appendSessionLog(log("2026-10-25", status = SessionStatus.PARTIAL))

        val data = observe().first()

        assertEquals(listOf(2026 to 10, 2026 to 9), data.months.map { it.year to it.month })
        assertEquals(listOf("2026-10-25", "2026-10-02"), data.months[0].items.map { it.date.toString() })
        assertEquals(3, data.totalSessions)
        assertEquals(2, data.completedSessions)
        assertEquals("Pagi", data.months[0].items[0].routineName)
    }

    @Test
    fun sameDayShowsLatestFirst() = runTest {
        val items = listOf(RoutineItem("a", sets = 3, reps = 10, restSeconds = 30))
        repository.saveRoutine(Routine("r1", "Satu", items))
        repository.saveRoutine(Routine("r2", "Dua", items))
        repository.appendSessionLog(log("2026-10-25", routineId = "r1"))
        repository.appendSessionLog(log("2026-10-25", routineId = "r2"))

        val names = observe().first().months.single().items.map { it.routineName }

        assertEquals(listOf("Dua", "Satu"), names)
    }

    @Test
    fun deletedRoutineStaysInHistoryWithoutName() = runTest {
        repository.appendSessionLog(log("2026-10-25", routineId = "sudah-dihapus"))

        val item = observe().first().months.single().items.single()

        assertNull(item.routineName)
        assertEquals(SessionStatus.COMPLETED, item.status)
    }

    @Test
    fun invalidDateIsSkippedAndDistanceKept() = runTest {
        repository.appendSessionLog(log("bukan-tanggal"))
        repository.appendSessionLog(log("2026-10-25", distance = 1250))

        val data = observe().first()

        assertEquals(1, data.totalSessions)
        assertEquals(1250, data.months.single().items.single().distanceMeters)
    }

    @Test
    fun reportsKeepHistorySetting() = runTest {
        repository.updateSettings { it.copy(keepHistory = false) }

        assertFalse(observe().first().keepHistory)
    }
}
