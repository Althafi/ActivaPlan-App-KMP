package com.activaplan.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.activaplan.app.data.local.UserDataStore
import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseFiles
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineItem
import com.activaplan.app.domain.model.ScheduleEntry
import com.activaplan.app.domain.model.SessionLog
import com.activaplan.app.domain.model.SessionStatus
import com.activaplan.app.domain.model.Settings
import com.activaplan.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** DataStore tanpa file untuk menguji logika; ketahanan di file diuji di androidHostTest. */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    override val data = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}

class UserDataRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dataStore = InMemoryPreferencesDataStore()
    private val repository = UserDataRepository(
        store = UserDataStore(dataStore, json),
        exercises = ExerciseRepository(ExerciseDataSource { files.getValue(it) }, json),
    )

    private val files = mapOf(
        ExerciseFiles.GENERAL to """{"schemaVersion":1,"exercises":[
            {"id":"a","name":"A","mode":"NO_EQUIPMENT","category":"CHEST","difficulty":"EASY","primaryMuscles":["chest"],
             "sets":3,"reps":10,"restSeconds":30,"steps":[],"tips":[],"commonMistakes":[]}],"templates":[]}""",
        ExerciseFiles.PUSHUP_BOARD to """{"schemaVersion":1,"exercises":[],"templates":[]}""",
        ExerciseFiles.I18N to """{"schemaVersion":1,"baseLanguage":"id","muscles":[],"packs":{}}""",
    )

    private fun routine(id: String, vararg exerciseIds: String = arrayOf("a")) =
        Routine(id, "Rutinitas $id", exerciseIds.map { RoutineItem(it, sets = 3, reps = 10, restSeconds = 30) })

    private fun entry(id: String, routineId: String, day: Int = 1) =
        ScheduleEntry(id, dayOfWeek = day, timeMinutes = 990, routineId = routineId, reminder = true)

    private fun log(routineId: String = "r1") =
        SessionLog("2026-10-25", routineId, SessionStatus.COMPLETED, completedSets = 3, totalSets = 3)

    @Test
    fun emptyStoreGivesEmptyListsAndDefaultSettings() = runTest {
        assertEquals(emptyList(), repository.routines.first())
        assertEquals(emptyList(), repository.schedule.first())
        assertEquals(emptyList(), repository.sessionLogs.first())
        assertEquals(Settings.DEFAULT, repository.settings.first())
    }

    @Test
    fun saveRoutineAddsThenReplacesById() = runTest {
        repository.saveRoutine(routine("r1"))
        repository.saveRoutine(routine("r2"))
        repository.saveRoutine(routine("r1").copy(name = "Baru"))

        val saved = repository.routines.first()
        assertEquals(listOf("r1", "r2"), saved.map { it.id })
        assertEquals("Baru", saved.first().name)
    }

    @Test
    fun deleteRoutineAlsoDeletesItsSchedule() = runTest {
        repository.saveRoutine(routine("r1"))
        repository.saveRoutine(routine("r2"))
        repository.saveScheduleEntry(entry("s1", "r1"))
        repository.saveScheduleEntry(entry("s2", "r2", day = 3))

        repository.deleteRoutine("r1")

        assertEquals(listOf("r2"), repository.routines.first().map { it.id })
        assertEquals(listOf("s2"), repository.schedule.first().map { it.id })
    }

    @Test
    fun scheduleUpsertAndDelete() = runTest {
        repository.saveScheduleEntry(entry("s1", "r1"))
        repository.saveScheduleEntry(entry("s1", "r1", day = 5))
        assertEquals(listOf(5), repository.schedule.first().map { it.dayOfWeek })

        repository.deleteScheduleEntry("s1")
        assertEquals(emptyList(), repository.schedule.first())
    }

    @Test
    fun sessionLogOnlyWrittenWhenKeepHistoryOn() = runTest {
        assertTrue(repository.appendSessionLog(log()))

        repository.updateSettings { it.copy(keepHistory = false) }
        assertFalse(repository.appendSessionLog(log("r2")))

        assertEquals(listOf("r1"), repository.sessionLogs.first().map { it.routineId })
    }

    @Test
    fun sessionLogsAreCappedKeepingTheNewest() = runTest {
        repeat(1_001) { repository.appendSessionLog(log("r$it")) }

        val logs = repository.sessionLogs.first()
        assertEquals(1_000, logs.size)
        assertEquals("r1", logs.first().routineId)
        assertEquals("r1000", logs.last().routineId)
    }

    @Test
    fun turningKeepHistoryOffKeepsOldLogsUntilCleared() = runTest {
        repository.appendSessionLog(log())
        repository.updateSettings { it.copy(keepHistory = false) }
        assertEquals(1, repository.sessionLogs.first().size)

        repository.clearSessionLogs()
        assertEquals(emptyList(), repository.sessionLogs.first())
    }

    @Test
    fun updateSettingsPersistsChange() = runTest {
        repository.updateSettings { it.copy(theme = ThemeMode.DARK, defaultTimeMinutes = 420) }
        val s = repository.settings.first()
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(420, s.defaultTimeMinutes)
        assertTrue(s.keepHistory)
    }

    @Test
    fun clearAllDataKeepsSettings() = runTest {
        repository.saveRoutine(routine("r1"))
        repository.saveScheduleEntry(entry("s1", "r1"))
        repository.appendSessionLog(log())
        repository.updateSettings { it.copy(theme = ThemeMode.LIGHT) }

        repository.clearAllData()

        assertEquals(emptyList(), repository.routines.first())
        assertEquals(emptyList(), repository.schedule.first())
        assertEquals(emptyList(), repository.sessionLogs.first())
        assertEquals(ThemeMode.LIGHT, repository.settings.first().theme)
    }

    @Test
    fun validatedReportsBrokenReferencesAndDropsThem() = runTest {
        repository.saveRoutine(routine("r1", "a", "hilang"))
        repository.saveScheduleEntry(entry("s1", "r1"))
        repository.saveScheduleEntry(entry("s2", "tidak-ada"))

        val v = repository.validated.first()

        assertEquals(listOf("a"), v.routines.single().items.map { it.exerciseId })
        assertEquals(listOf("s1"), v.schedule.map { it.id })
        assertEquals(setOf("r1", "s2"), v.issues.map { it.subjectId }.toSet())
        assertTrue(v.hasBrokenData)
    }

    @Test
    fun corruptJsonFallsBackInsteadOfCrashing() = runTest {
        dataStore.edit {
            it[stringPreferencesKey("routines")] = "{bukan json"
            it[stringPreferencesKey("settings")] = "[]"
        }
        repository.saveScheduleEntry(entry("s1", "r1"))

        assertEquals(emptyList(), repository.routines.first())
        assertEquals(Settings.DEFAULT, repository.settings.first())
        assertEquals(listOf("s1"), repository.schedule.first().map { it.id })
    }

    @Test
    fun usesDocumentedKeys() = runTest {
        repository.saveRoutine(routine("r1"))
        repository.saveScheduleEntry(entry("s1", "r1"))
        repository.appendSessionLog(log())
        repository.updateSettings { it.copy(theme = ThemeMode.DARK) }

        val keys = dataStore.data.first().asMap().keys.map { it.name }.toSet()
        assertEquals(setOf("routines", "schedule", "session_logs", "settings", "schema_version"), keys)
    }
}
