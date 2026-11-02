package com.activaplan.app.domain

import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.i18n.Strings
import com.activaplan.app.domain.model.BoardLayout
import com.activaplan.app.domain.model.BoardZone
import com.activaplan.app.domain.model.Category
import com.activaplan.app.domain.model.ExploreQuery
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.usecase.FilterExercisesUseCase
import com.activaplan.app.domain.usecase.GetExerciseDetailUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kriteria selesai Jelajah & Detail, diuji terhadap salinan JSON di `composeResources/files`. */
class ExploreDataTest {
    private val resources = File("src/commonMain/composeResources/files")
    private val repository = ExerciseRepository(
        source = ExerciseDataSource { name -> File(resources, name).readText() },
        json = Json { ignoreUnknownKeys = true },
    )
    private val filter = FilterExercisesUseCase()
    private val getDetail = GetExerciseDetailUseCase(repository)

    private val boardModes = listOf(Mode.PUSHUP_BOARD, Mode.BOARD_ROPE)

    @Test
    fun allBoardAndRopeExercisesAppearAndEachBelongsToItsTab() = runTest {
        val all = repository.exercises(language = Language.ID)
        val shown = boardModes.map { mode -> mode to filter(all, ExploreQuery(mode)) }

        assertEquals(31, shown.sumOf { it.second.size })
        assertEquals(15, shown.first { it.first == Mode.PUSHUP_BOARD }.second.size)
        assertEquals(16, shown.first { it.first == Mode.BOARD_ROPE }.second.size)
        shown.forEach { (mode, list) -> assertTrue(list.all { it.mode == mode }) }
        assertEquals(39, filter(all, ExploreQuery(Mode.NO_EQUIPMENT)).size)
    }

    @Test
    fun categoryChipsPartitionEachTab() = runTest {
        val all = repository.exercises(language = Language.ID)
        Mode.entries.forEach { mode ->
            val everything = filter(all, ExploreQuery(mode)).map { it.id }.toSet()
            val byCategory = Category.entries.flatMap { c -> filter(all, ExploreQuery(mode, category = c)).map { it.id } }
            assertEquals(everything.size, byCategory.size, "$mode: tiap latihan harus tepat di satu chip kategori")
            assertEquals(everything, byCategory.toSet(), "$mode")
        }
    }

    @Test
    fun fullBodyExercisesAreReachableThroughTheirChip() = runTest {
        val all = repository.exercises(language = Language.ID)
        assertTrue(filter(all, ExploreQuery(Mode.BOARD_ROPE, category = Category.FULL_BODY)).isNotEmpty())
        assertTrue(filter(all, ExploreQuery(Mode.NO_EQUIPMENT, category = Category.FULL_BODY)).isNotEmpty())
    }

    @Test
    fun searchFindsExercisesByNameAndByMuscle() = runTest {
        val all = repository.exercises(language = Language.ID)
        val byName = filter(all, ExploreQuery(Mode.PUSHUP_BOARD, text = "lutut")).map { it.id }
        assertTrue("pb_chest_knee_pushup" in byName)

        val byMuscle = filter(all, ExploreQuery(Mode.PUSHUP_BOARD, text = "triceps"))
        assertTrue(byMuscle.size > 1)
        assertTrue(byMuscle.any { !it.name.contains("triceps", ignoreCase = true) }, "harus ada yang cocok lewat primaryMuscles saja")
        assertEquals(emptyList(), filter(all, ExploreQuery(Mode.PUSHUP_BOARD, text = "zzzz-tidak-ada")))
    }

    @Test
    fun levelNavigationNeverLeadsToAnUnknownId() = runTest {
        val catalog = repository.catalog().exercises
        val known = catalog.map { it.id }.toSet()
        catalog.forEach { source ->
            val detail = assertNotNull(getDetail(source.id, Language.ID), source.id)
            // ID di data sumber selalu dipertahankan (valid), dan hasilnya selalu ID yang ada.
            assertEquals(source.regressionId, detail.regressionId, source.id)
            assertEquals(source.progressionId, detail.progressionId, source.id)
            listOfNotNull(detail.regressionId, detail.progressionId).forEach {
                assertTrue(it in known, "${source.id} -> $it tidak dikenal")
                assertNotNull(getDetail(it, Language.ID))
            }
        }
    }

    @Test
    fun boardZonesMatchTheDiagram() = runTest {
        val catalog = repository.catalog().exercises
        catalog.filter { it.mode == Mode.PUSHUP_BOARD }.forEach {
            val zone = assertNotNull(it.boardZone, it.id)
            assertTrue(BoardLayout.highlightedHoles(zone).isNotEmpty(), it.id)
        }
        catalog.filter { it.mode != Mode.PUSHUP_BOARD }.forEach { assertNull(it.boardZone, it.id) }
        // Zona sesuai kategori: Biru = Dada, Merah = Bahu, Kuning = Punggung, Hijau = Triceps (kategori Lengan).
        val expected = mapOf(
            BoardZone.BLUE to Category.CHEST, BoardZone.RED to Category.SHOULDERS,
            BoardZone.YELLOW to Category.BACK, BoardZone.GREEN to Category.ARMS,
        )
        catalog.filter { it.boardZone != null }.forEach { assertEquals(expected[it.boardZone], it.category, it.id) }
    }

    @Test
    fun durationExercisesShowSecondsNotReps() = runTest {
        val timed = repository.catalog().exercises.filter { it.durationSeconds != null }
        assertTrue(timed.isNotEmpty())
        Language.entries.forEach { language ->
            val strings = Strings(language)
            timed.forEach {
                assertEquals(strings[StringKey.DETAIL_UNIT_SECONDS], strings.workUnit(it), it.id)
                assertTrue(!strings.summary(it).contains("rep"), "${it.id}: ${strings.summary(it)}")
                assertTrue(strings.summary(it).contains("${it.durationSeconds}"), it.id)
            }
        }
    }

    @Test
    fun onlyRopeExercisesCarrySafetyNotes() = runTest {
        repository.catalog().exercises.forEach {
            val notes = getDetail(it.id, Language.ID)!!.safetyNotes
            if (it.mode == Mode.BOARD_ROPE) assertEquals(3, notes.size, it.id) else assertTrue(notes.isEmpty(), it.id)
        }
        val english = getDetail("rope_row", Language.EN)!!.safetyNotes
        assertEquals(3, english.size)
        assertTrue(english != getDetail("rope_row", Language.ID)!!.safetyNotes)
    }
}
