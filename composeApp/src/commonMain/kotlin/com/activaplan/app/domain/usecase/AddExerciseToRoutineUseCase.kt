package com.activaplan.app.domain.usecase

import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.model.AddToRoutineResult
import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineItem
import com.activaplan.app.domain.model.RoutineRules
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

internal fun randomRoutineId(): String = "routine_${Random.nextLong().toULong().toString(36)}"

/** Nilai awal item rutinitas: set, rep (atau detik), dan istirahat bawaan latihan. */
internal fun Exercise.toRoutineItem() = RoutineItem(
    exerciseId = id,
    sets = sets,
    reps = reps,
    durationSeconds = durationSeconds,
    restSeconds = restSeconds,
)

/**
 * Item rutinitas untuk kardio: satu "set" tanpa istirahat, `durationSeconds` = total detik saat ditambahkan.
 * Angka itu hanya agar estimasi menit rutinitas benar tanpa mengubah penghitungnya; sesi memakai katalog kardio.
 */
internal fun CardioWorkout.toRoutineItem() = RoutineItem(
    exerciseId = id,
    sets = 1,
    reps = null,
    durationSeconds = totalSeconds,
    restSeconds = 0,
)

/** Menambah latihan ke rutinitas dengan nilai default set, rep (atau detik), dan istirahat dari latihan itu. */
class AddExerciseToRoutineUseCase(
    private val userData: UserDataRepository,
    private val newId: () -> String = ::randomRoutineId,
) {

    /** Rutinitas milik pengguna, untuk daftar pilihan. */
    val routines: Flow<List<Routine>> get() = userData.routines

    suspend fun toExisting(routineId: String, exercise: Exercise): AddToRoutineResult =
        toExisting(routineId, exercise.toRoutineItem())

    suspend fun toExisting(routineId: String, workout: CardioWorkout): AddToRoutineResult =
        toExisting(routineId, workout.toRoutineItem())

    /** Membuat rutinitas baru bernama [name] (1-40 karakter setelah dirapikan) berisi [exercise]. */
    suspend fun toNew(name: String, exercise: Exercise): AddToRoutineResult = toNew(name, exercise.toRoutineItem())

    suspend fun toNew(name: String, workout: CardioWorkout): AddToRoutineResult = toNew(name, workout.toRoutineItem())

    private suspend fun toExisting(routineId: String, item: RoutineItem): AddToRoutineResult =
        userData.appendRoutineItem(routineId, item, RoutineRules.MAX_ITEMS)

    private suspend fun toNew(name: String, item: RoutineItem): AddToRoutineResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > RoutineRules.MAX_NAME_LENGTH) return AddToRoutineResult.InvalidName
        userData.saveRoutine(Routine(newId(), trimmed, listOf(item)))
        return AddToRoutineResult.Added(trimmed)
    }
}
