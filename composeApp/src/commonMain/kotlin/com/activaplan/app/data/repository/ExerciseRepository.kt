package com.activaplan.app.data.repository

import com.activaplan.app.domain.i18n.ExerciseDictionary
import com.activaplan.app.domain.i18n.ExerciseLocalizer
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.Category
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.Template
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Data sumber (berbahasa Indonesia) hasil gabungan semua file latihan. */
data class ExerciseCatalog(
    val exercises: List<Exercise>,
    val templates: List<Template>,
    val dictionary: ExerciseDictionary,
    /** Catatan keselamatan perangkat (`device.safetyNotes`) dalam bahasa sumber. */
    val safetyNotes: List<String> = emptyList(),
) {
    /** Indeks id → latihan, dibangun sekali; katalog tidak berubah setelah dimuat. */
    val byId: Map<String, Exercise> by lazy { exercises.associateBy { it.id } }

    /** Indeks id → mode, dibangun sekali; dipakai Beranda yang dihitung ulang tiap menit. */
    val modeById: Map<String, Mode> by lazy { exercises.associate { it.id to it.mode } }
}

/** Filter katalog; properti null berarti tidak memfilter. */
data class ExerciseFilter(
    val mode: Mode? = null,
    val category: Category? = null,
    val difficulty: Difficulty? = null,
)

@Serializable
private data class DeviceFile(val safetyNotes: List<String> = emptyList())

@Serializable
private data class ExerciseFile(
    val device: DeviceFile? = null,
    val exercises: List<Exercise> = emptyList(),
    val templates: List<Template> = emptyList(),
)

/** Membaca latihan dari JSON bawaan (read-only), memfilter, dan menerjemahkan lewat [ExerciseLocalizer]. */
class ExerciseRepository(
    private val source: ExerciseDataSource,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var cache: Loaded? = null
    private val templateCache = mutableMapOf<Language, List<Template>>()

    private class Loaded(val catalog: ExerciseCatalog, val localizer: ExerciseLocalizer)

    /** Data sumber tanpa terjemahan; untuk validasi. Dibaca sekali lalu disimpan. */
    suspend fun catalog(): ExerciseCatalog = loaded().catalog

    suspend fun exercises(filter: ExerciseFilter = ExerciseFilter(), language: Language): List<Exercise> {
        val l = loaded()
        return l.catalog.exercises
            .filter { e ->
                (filter.mode == null || e.mode == filter.mode) &&
                    (filter.category == null || e.category == filter.category) &&
                    (filter.difficulty == null || e.difficulty == filter.difficulty)
            }
            .map { l.localizer.localize(it, language) }
    }

    suspend fun exercise(id: String, language: Language): Exercise? {
        val l = loaded()
        return l.catalog.exercises.firstOrNull { it.id == id }?.let { l.localizer.localize(it, language) }
    }

    suspend fun templates(language: Language): List<Template> = mutex.withLock {
        val l = loadedLocked()
        templateCache.getOrPut(language) { l.catalog.templates.map { l.localizer.localize(it, language) } }
    }

    /** Catatan keselamatan board + tali; memakai teks sumber bila terjemahan tidak ada. */
    suspend fun safetyNotes(language: Language): List<String> {
        val l = loaded()
        return l.localizer.deviceText(language)?.safetyNotes ?: l.catalog.safetyNotes
    }

    private suspend fun loaded(): Loaded = mutex.withLock { loadedLocked() }

    /** Hanya dipanggil saat [mutex] dipegang. */
    private suspend fun loadedLocked(): Loaded = cache ?: load().also { cache = it }

    private suspend fun load(): Loaded {
        val files = listOf(ExerciseFiles.GENERAL, ExerciseFiles.PUSHUP_BOARD)
            .map { json.decodeFromString<ExerciseFile>(source.read(it)) }
        val dictionary = json.decodeFromString<ExerciseDictionary>(source.read(ExerciseFiles.I18N))
        val catalog = ExerciseCatalog(
            exercises = files.flatMap { it.exercises },
            templates = files.flatMap { it.templates },
            dictionary = dictionary,
            safetyNotes = files.flatMap { it.device?.safetyNotes.orEmpty() },
        )
        return Loaded(catalog, ExerciseLocalizer(dictionary))
    }
}
