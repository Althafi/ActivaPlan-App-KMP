package com.activaplan.app.domain

import com.activaplan.app.domain.i18n.ExerciseDictionary
import com.activaplan.app.domain.i18n.ExerciseLocalizer
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.ImageLicense
import com.activaplan.app.domain.model.Template
import com.activaplan.app.domain.validation.ExerciseCatalogValidator
import com.activaplan.app.domain.validation.TranslationValidator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Memeriksa file asli di folder `data/` terhadap model dan aturan validasi. */
class DataFilesTest {
    @Serializable
    private data class Catalog(
        val schemaVersion: Int,
        val exercises: List<Exercise>,
        val templates: List<Template>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun read(name: String) = File("../data/$name").readText()

    private val general = json.decodeFromString<Catalog>(read("exercises_general.json"))
    private val board = json.decodeFromString<Catalog>(read("exercises_pushup_board.json"))
    private val dictionary = json.decodeFromString<ExerciseDictionary>(read("i18n_exercises.json"))
    private val exercises = general.exercises + board.exercises
    private val templates = general.templates + board.templates

    @Test
    fun allExercisesAndTemplatesDecode() {
        assertEquals(39, general.exercises.size)
        assertEquals(31, board.exercises.size)
        assertEquals(70, exercises.size)
        assertEquals(17, templates.size)
    }

    @Test
    fun catalogPassesValidationRules() {
        val issues = ExerciseCatalogValidator.validate(exercises, templates)
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun everyImageHasRecordedLicenseAndExistingFile() {
        val drawable = File("src/commonMain/composeResources/drawable")
        val issues = exercises.flatMap { e ->
            e.images.flatMap { img ->
                buildList {
                    if (!File(drawable, img.file).isFile) add("${e.id}: file tidak ada ${img.file}")
                    if (img.alt.isBlank()) add("${e.id}: alt kosong")
                    if (img.license == ImageLicense.CC_BY && (img.author.isNullOrBlank() || img.sourceUrl.isNullOrBlank())) {
                        add("${e.id}: CC-BY wajib author dan sourceUrl")
                    }
                }
            }
        }
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
        assertTrue(exercises.all { it.images.isNotEmpty() }, "Latihan tanpa gambar memakai placeholder, tetapi data bawaan harus terisi")
    }

    @Test
    fun englishTranslationIsComplete() {
        val issues = TranslationValidator.validate(exercises, templates, dictionary, Language.EN)
        assertTrue(issues.isEmpty(), issues.joinToString("\n"))
    }

    @Test
    fun everyMuscleTermHasNamesInAllLanguages() {
        val localizer = ExerciseLocalizer(dictionary)
        exercises.flatMap { it.primaryMuscles }.toSet().forEach { m ->
            val term = dictionary.muscles.firstOrNull { m.lowercase() in it.terms }
            assertTrue(term != null, "Otot '$m' tidak ada di kamus")
            Language.entries.forEach { lang ->
                assertTrue(term.names[lang.code]?.isNotBlank() == true, "Otot '$m' tanpa nama ${lang.code}")
                assertTrue(localizer.muscle(m, lang) == term.names[lang.code])
            }
        }
    }

    @Test
    fun deviceTextCoversAllBoardZones() {
        val zones = dictionary.packs.getValue("en").device!!.zones.keys
        assertEquals(setOf("BLUE", "RED", "YELLOW", "GREEN"), zones)
    }
}
