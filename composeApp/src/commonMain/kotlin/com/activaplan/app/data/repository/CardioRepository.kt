package com.activaplan.app.data.repository

import com.activaplan.app.domain.i18n.ExerciseDictionary
import com.activaplan.app.domain.i18n.ExerciseLocalizer
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.Difficulty
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Data sumber kardio (berbahasa Indonesia) beserta kamus terjemahan. */
data class CardioCatalog(
    val workouts: List<CardioWorkout>,
    val dictionary: ExerciseDictionary,
)

@Serializable
private data class CardioFile(val workouts: List<CardioWorkout> = emptyList())

/** Membaca latihan kardio dari JSON bawaan (read-only), memfilter, dan menerjemahkan lewat [ExerciseLocalizer]. */
class CardioRepository(
    private val source: ExerciseDataSource,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var cache: Loaded? = null

    private class Loaded(val catalog: CardioCatalog, val localizer: ExerciseLocalizer)

    /** Data sumber tanpa terjemahan; untuk validasi. */
    suspend fun catalog(): CardioCatalog = loaded().catalog

    suspend fun workouts(difficulty: Difficulty? = null, language: Language): List<CardioWorkout> {
        val l = loaded()
        return l.catalog.workouts
            .filter { difficulty == null || it.difficulty == difficulty }
            .map { l.localizer.localize(it, language) }
    }

    suspend fun workout(id: String, language: Language): CardioWorkout? {
        val l = loaded()
        return l.catalog.workouts.firstOrNull { it.id == id }?.let { l.localizer.localize(it, language) }
    }

    private suspend fun loaded(): Loaded = mutex.withLock {
        cache ?: load().also { cache = it }
    }

    private suspend fun load(): Loaded {
        val file = json.decodeFromString<CardioFile>(source.read(ExerciseFiles.CARDIO))
        val dictionary = json.decodeFromString<ExerciseDictionary>(source.read(ExerciseFiles.I18N))
        return Loaded(CardioCatalog(file.workouts, dictionary), ExerciseLocalizer(dictionary))
    }
}
