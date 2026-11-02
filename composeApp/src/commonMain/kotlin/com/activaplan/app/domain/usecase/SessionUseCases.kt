package com.activaplan.app.domain.usecase

import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.SessionExercise
import com.activaplan.app.domain.model.SessionLog
import com.activaplan.app.domain.model.SessionPlan
import com.activaplan.app.domain.model.SessionSummary
import com.activaplan.app.domain.time.LocalClock
import kotlinx.coroutines.flow.first

/**
 * Menyusun sesi dari rutinitas [routineId]. Item yang latihannya tidak ada di katalog dilewati dan dihitung di
 * [SessionPlan.skippedItems]. Item kardio dibuka menjadi satu langkah per segmen (lihat [SessionExercise.ofCardio]).
 * Null bila rutinitas tidak ada atau tidak punya latihan yang dikenal.
 * Melempar bila katalog latihan gagal dimuat.
 */
class LoadSessionPlanUseCase(
    private val userData: UserDataRepository,
    private val exercises: ExerciseRepository,
    private val cardio: CardioRepository? = null,
) {

    suspend operator fun invoke(routineId: String, language: Language): SessionPlan? {
        val routine = userData.routines.first().firstOrNull { it.id == routineId } ?: return null
        val catalog = exercises.exercises(language = language).associateBy { it.id }
        val cardioCatalog = cardio?.workouts(language = language)?.associateBy { it.id }.orEmpty()
        val items = routine.items.flatMapIndexed { index, item ->
            val strength = catalog[item.exerciseId]
            val workout = cardioCatalog[item.exerciseId]
            when {
                strength != null -> listOf(SessionExercise.of(item, strength))
                workout != null -> SessionExercise.ofCardio(workout, index)
                else -> emptyList()
            }
        }
        if (items.isEmpty()) return null
        val recognized = routine.items.count { it.exerciseId in catalog || it.exerciseId in cardioCatalog }
        val needsSafetyCheck = items.any { it.exercise.mode == Mode.BOARD_ROPE }
        return SessionPlan(
            routineId = routine.id,
            routineName = routine.name,
            exercises = items,
            catalog = catalog,
            requiresSafetyCheck = needsSafetyCheck,
            safetyNotes = if (needsSafetyCheck) exercises.safetyNotes(language) else emptyList(),
            skippedItems = routine.items.size - recognized,
        )
    }
}

/** Mencatat hasil sesi dan menghitung streak setelahnya. */
class FinishSessionUseCase(
    private val userData: UserDataRepository,
    private val clock: LocalClock,
    private val calculateWeekStreak: CalculateWeekStreakUseCase = CalculateWeekStreakUseCase(),
) {

    /**
     * Menulis `SessionLog` hari ini untuk [summary]. Tidak menulis apa pun bila `keepHistory` mati;
     * mengembalikan true bila log ditulis. Melempar bila penyimpanan gagal.
     */
    suspend fun record(routineId: String, summary: SessionSummary): Boolean =
        userData.appendSessionLog(log(routineId, summary))

    /**
     * Streak setelah sesi ini. Sesi ini ikut dihitung walau lognya tidak ditulis (`keepHistory` mati),
     * jadi streak tidak bergantung pada pengaturan riwayat.
     */
    suspend fun streak(routineId: String, summary: SessionSummary): Int {
        val logs = userData.sessionLogs.first() + log(routineId, summary)
        val schedule = userData.validated.first().schedule
        return calculateWeekStreak(schedule, logs, clock.now().date).streak
    }

    private fun log(routineId: String, summary: SessionSummary) = SessionLog(
        date = clock.now().date.toString(),
        routineId = routineId,
        status = summary.status,
        completedSets = summary.completedSets,
        totalSets = summary.totalSets,
        distanceMeters = summary.distanceMeters,
    )
}
