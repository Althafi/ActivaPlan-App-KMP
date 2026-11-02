package com.activaplan.app.domain

import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseFiles
import com.activaplan.app.data.repository.ExerciseFilter
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.validation.ExerciseCatalogValidator
import com.activaplan.app.domain.validation.TranslationValidator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Menjalankan repository terhadap salinan JSON di `composeResources/files`. */
class ExerciseRepositoryDataTest {
    private val resources = File("src/commonMain/composeResources/files")
    private val repository = ExerciseRepository(
        source = ExerciseDataSource { name -> File(resources, name).readText() },
        json = Json { ignoreUnknownKeys = true },
    )

    @Test
    fun reads70ExercisesAnd17Templates() = runTest {
        assertEquals(70, repository.exercises(language = Language.ID).size)
        assertEquals(17, repository.templates(Language.ID).size)
        assertEquals(39, repository.exercises(ExerciseFilter(mode = Mode.NO_EQUIPMENT), Language.ID).size)
        assertEquals(15, repository.exercises(ExerciseFilter(mode = Mode.PUSHUP_BOARD), Language.ID).size)
        assertEquals(16, repository.exercises(ExerciseFilter(mode = Mode.BOARD_ROPE), Language.ID).size)
    }

    @Test
    fun everyExerciseHasAStepImage() = runTest {
        val missing = repository.exercises(language = Language.ID).filter { it.images.isEmpty() }.map { it.id }
        assertTrue(missing.isEmpty(), "Tanpa gambar: $missing")
    }

    @Test
    fun progressionReferencesAreValid() = runTest {
        val c = repository.catalog()
        val issues = ExerciseCatalogValidator.validate(c.exercises, c.templates)
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun englishTranslationHasNoFindings() = runTest {
        val c = repository.catalog()
        val issues = TranslationValidator.validate(c.exercises, c.templates, c.dictionary, Language.EN)
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun englishOutputIsTranslated() = runTest {
        assertEquals("Wall Push-up", repository.exercise("ne_chest_wall_pushup", Language.EN)?.name)
        assertEquals("Push-up Dinding", repository.exercise("ne_chest_wall_pushup", Language.ID)?.name)
    }

    @Test
    fun resourceCopiesMatchDataFolder() {
        listOf(ExerciseFiles.GENERAL, ExerciseFiles.PUSHUP_BOARD, ExerciseFiles.I18N).forEach {
            assertEquals(File("../data/$it").readText(), File(resources, it).readText(), "$it berbeda dari data/")
        }
    }
}
