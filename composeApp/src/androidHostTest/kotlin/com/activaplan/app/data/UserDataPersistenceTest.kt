package com.activaplan.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.activaplan.app.data.local.UserDataStore
import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineItem
import com.activaplan.app.domain.model.ScheduleEntry
import com.activaplan.app.domain.model.SessionLog
import com.activaplan.app.domain.model.SessionStatus
import com.activaplan.app.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Menulis lewat satu instance DataStore, menutupnya, lalu membaca lewat instance baru pada file yang sama. */
class UserDataPersistenceTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private class Session(file: File, json: Json) {
        private val job = Job()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file.absolutePath.toPath() },
        )
        val repository = UserDataRepository(
            store = UserDataStore(dataStore, json),
            exercises = ExerciseRepository(ExerciseDataSource { error("tidak dipakai") }, json),
        )

        suspend fun close() = job.cancelAndJoin()
    }

    @Test
    fun dataSurvivesClosingAndReopening() = runBlocking {
        val file = File.createTempFile("activaplan", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
        val routine = Routine("r1", "Pagi", listOf(RoutineItem("a", sets = 3, reps = 10, restSeconds = 30)))
        val entry = ScheduleEntry("s1", dayOfWeek = 7, timeMinutes = 990, routineId = "r1", reminder = true)
        val log = SessionLog("2026-10-25", "r1", SessionStatus.PARTIAL, completedSets = 2, totalSets = 3)

        val first = Session(file, json)
        first.repository.saveRoutine(routine)
        first.repository.saveScheduleEntry(entry)
        first.repository.appendSessionLog(log)
        first.repository.updateSettings { it.copy(theme = ThemeMode.DARK, defaultTimeMinutes = 420) }
        first.close()

        val second = Session(file, json)
        try {
            assertEquals(listOf(routine), second.repository.routines.first())
            assertEquals(listOf(entry), second.repository.schedule.first())
            assertEquals(listOf(log), second.repository.sessionLogs.first())
            val settings = second.repository.settings.first()
            assertEquals(ThemeMode.DARK, settings.theme)
            assertEquals(420, settings.defaultTimeMinutes)
        } finally {
            second.close()
        }
    }

    @Test
    fun corruptJsonIsBackedUpBeforeBeingOverwritten() = runBlocking {
        val file = File.createTempFile("activaplan", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
        val routine = Routine("r1", "Pagi", listOf(RoutineItem("a", sets = 3, reps = 10, restSeconds = 30)))
        val corrupt = "{bukan json"

        val session = Session(file, json)
        try {
            session.dataStore.edit { it[stringPreferencesKey("routines")] = corrupt }
            assertEquals(emptyList(), session.repository.routines.first())

            session.repository.saveRoutine(routine)

            assertEquals(listOf(routine), session.repository.routines.first())
            assertEquals(corrupt, session.dataStore.data.first()[stringPreferencesKey("routines_corrupt")])
        } finally {
            session.close()
        }
    }
}
