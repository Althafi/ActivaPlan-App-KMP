package com.activaplan.app.domain

import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.validation.CardioCatalogValidator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Menjalankan CardioRepository terhadap salinan JSON di `composeResources/files`. */
class CardioDataTest {
    private val resources = File("src/commonMain/composeResources/files")
    private val source = ExerciseDataSource { name -> File(resources, name).readText() }
    private val json = Json { ignoreUnknownKeys = true }
    private val repository = CardioRepository(source, json)

    @Test
    fun reads10Workouts() = runTest {
        assertEquals(10, repository.workouts(language = Language.ID).size)
        assertEquals(3, repository.workouts(Difficulty.EASY, Language.ID).count { it.id in setOf("cd_walk_brisk", "cd_walk_run_beginner", "cd_easy_run") })
    }

    @Test
    fun catalogHasNoFindings() = runTest {
        val strengthIds = ExerciseRepository(source, json).catalog().exercises.map { it.id }.toSet()
        val issues = CardioCatalogValidator.validate(repository.catalog().workouts, strengthIds)
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun englishTranslationHasNoFindings() = runTest {
        val c = repository.catalog()
        val issues = CardioCatalogValidator.validateTranslation(c.workouts, c.dictionary, Language.EN)
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun englishOutputIsTranslated() = runTest {
        assertEquals("Brisk Walk", repository.workout("cd_walk_brisk", Language.EN)?.name)
        assertEquals("Jalan Cepat", repository.workout("cd_walk_brisk", Language.ID)?.name)
    }

    @Test
    fun totalsMatchDesign() = runTest {
        val minutes = repository.workouts(language = Language.ID).associate { it.id to it.totalMinutes }
        assertEquals(30, minutes["cd_walk_brisk"])
        assertEquals(34, minutes["cd_walk_run_beginner"])
        assertEquals(70, minutes["cd_long_run"])
        assertEquals(23, minutes["cd_intervals_30_30"])
        assertNotNull(repository.workout("cd_hill_repeats", Language.ID))
    }
}
