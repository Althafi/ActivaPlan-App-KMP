package com.activaplan.app.data.repository

import com.activaplan.app.domain.i18n.ExerciseDictionary
import com.activaplan.app.domain.i18n.ExerciseLocalizer
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.Program
import com.activaplan.app.domain.model.WeeklyPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Data sumber rencana mingguan (berbahasa Indonesia) beserta kamus terjemahan. */
data class WeeklyPlanCatalog(
    val plans: List<WeeklyPlan>,
    val dictionary: ExerciseDictionary,
    /** Program bertahap beberapa pekan; pekannya dapat dibuka sebagai rencana lewat [WeeklyPlanRepository.plan]. */
    val programs: List<Program> = emptyList(),
)

@Serializable
private data class PlanFile(val plans: List<WeeklyPlan> = emptyList())

@Serializable
private data class ProgramFile(val programs: List<Program> = emptyList())

/** Membaca rencana mingguan dari JSON bawaan (read-only) dan menerjemahkannya lewat [ExerciseLocalizer]. */
class WeeklyPlanRepository(
    private val source: ExerciseDataSource,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var cache: Loaded? = null

    private class Loaded(val catalog: WeeklyPlanCatalog, val localizer: ExerciseLocalizer)

    /** Data sumber tanpa terjemahan; untuk validasi. */
    suspend fun catalog(): WeeklyPlanCatalog = loaded().catalog

    suspend fun plans(language: Language): List<WeeklyPlan> {
        val l = loaded()
        return l.catalog.plans.map { l.localizer.localize(it, language) }
    }

    /** Rencana mingguan mandiri, atau satu pekan sebuah program bila [id] adalah ID pekan (mis. `prog_easy_4w_w2`). */
    suspend fun plan(id: String, language: Language): WeeklyPlan? {
        val l = loaded()
        l.catalog.plans.firstOrNull { it.id == id }?.let { return l.localizer.localize(it, language) }
        val program = l.catalog.programs.firstOrNull { p -> p.weekPlans.any { it.id == id } } ?: return null
        return l.localizer.localize(program, language).weekPlans.firstOrNull { it.id == id }
    }

    suspend fun programs(language: Language): List<Program> {
        val l = loaded()
        return l.catalog.programs.map { l.localizer.localize(it, language) }
    }

    suspend fun program(id: String, language: Language): Program? {
        val l = loaded()
        return l.catalog.programs.firstOrNull { it.id == id }?.let { l.localizer.localize(it, language) }
    }

    private suspend fun loaded(): Loaded = mutex.withLock {
        cache ?: load().also { cache = it }
    }

    private suspend fun load(): Loaded {
        val file = json.decodeFromString<PlanFile>(source.read(ExerciseFiles.PLANS))
        val dictionary = json.decodeFromString<ExerciseDictionary>(source.read(ExerciseFiles.I18N))
        return Loaded(WeeklyPlanCatalog(file.plans, dictionary, loadPrograms()), ExerciseLocalizer(dictionary))
    }

    /** Program bersifat tambahan: bila berkasnya tidak ada atau rusak, rencana mingguan tetap berfungsi. */
    private suspend fun loadPrograms(): List<Program> = try {
        json.decodeFromString<ProgramFile>(source.read(ExerciseFiles.PROGRAMS)).programs
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
}
