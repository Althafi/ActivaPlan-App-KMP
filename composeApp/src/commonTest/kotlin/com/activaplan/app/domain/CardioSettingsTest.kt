package com.activaplan.app.domain

import com.activaplan.app.data.InMemoryPreferencesDataStore
import com.activaplan.app.data.local.UserDataStore
import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseFiles
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.model.SessionLog
import com.activaplan.app.domain.model.SessionStatus
import com.activaplan.app.domain.model.Settings
import com.activaplan.app.domain.usecase.SetAgeUseCase
import com.activaplan.app.domain.usecase.SetGpsTrackingUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioSettingsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val files = mapOf(
        ExerciseFiles.GENERAL to """{"schemaVersion":1,"exercises":[],"templates":[]}""",
        ExerciseFiles.PUSHUP_BOARD to """{"schemaVersion":1,"exercises":[],"templates":[]}""",
        ExerciseFiles.I18N to """{"schemaVersion":1,"baseLanguage":"id","muscles":[],"packs":{}}""",
    )
    private val userData = UserDataRepository(
        UserDataStore(InMemoryPreferencesDataStore(), json),
        ExerciseRepository(ExerciseDataSource { files.getValue(it) }, json),
    )

    @Test
    fun gpsAndAgeDefaultOffAndPersist() = runTest {
        assertFalse(userData.settings.first().gpsTracking)
        assertNull(userData.settings.first().age)

        SetGpsTrackingUseCase(userData)(true)
        SetAgeUseCase(userData)(34)
        assertTrue(userData.settings.first().gpsTracking)
        assertEquals(34, userData.settings.first().age)

        SetAgeUseCase(userData)(null)
        assertNull(userData.settings.first().age)
    }

    @Test
    fun ageOutsideRangeIsRejected() = runTest {
        assertFailsWith<IllegalArgumentException> { SetAgeUseCase(userData)(9) }
        assertFailsWith<IllegalArgumentException> { SetAgeUseCase(userData)(91) }
    }

    @Test
    fun oldSettingsAndLogsWithoutNewFieldsStillDecode() {
        val settings = json.decodeFromString<Settings>(
            """{"theme":"SYSTEM","remindersEnabled":true,"defaultTimeMinutes":990,"keepHistory":true}""",
        )
        assertFalse(settings.gpsTracking)
        assertNull(settings.age)
        val log = json.decodeFromString<SessionLog>(
            """{"date":"2026-10-26","routineId":"r1","status":"COMPLETED","completedSets":3,"totalSets":3}""",
        )
        assertEquals(0, log.distanceMeters)
        assertEquals(SessionStatus.COMPLETED, log.status)
    }
}
