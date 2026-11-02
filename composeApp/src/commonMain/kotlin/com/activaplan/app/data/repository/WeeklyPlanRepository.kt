package com.activaplan.app.data.repository

import com.activaplan.app.domain.i18n.ExerciseDictionary
import com.activaplan.app.domain.i18n.ExerciseLocalizer
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.WeeklyPlan
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Data sumber rencana mingguan (berbahasa Indonesia) beserta kamus terjemahan. */
data class WeeklyPlanCatalog(
    val plans: List<WeeklyPlan>,
    val dictionary: ExerciseDictionary,
)

@Serializable
private data class PlanFile(val plans: List<WeeklyPlan> = emptyList())

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

    suspend fun plan(id: String, language: Language): WeeklyPlan? {
        val l = loaded()
        return l.catalog.plans.firstOrNull { it.id == id }?.let { l.localizer.localize(it, language) }
    }

    private suspend fun loaded(): Loaded = mutex.withLock {
        cache ?: load().also { cache = it }
    }

    private suspend fun load(): Loaded {
        val file = json.decodeFromString<PlanFile>(source.read(ExerciseFiles.PLANS))
        val dictionary = json.decodeFromString<ExerciseDictionary>(source.read(ExerciseFiles.I18N))
        return Loaded(WeeklyPlanCatalog(file.plans, dictionary), ExerciseLocalizer(dictionary))
    }
}
