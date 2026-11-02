package com.activaplan.app.domain.usecase

import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.model.ConflictPolicy
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineOption
import com.activaplan.app.domain.model.SaveScheduleResult
import com.activaplan.app.domain.model.ScheduleData
import com.activaplan.app.domain.model.ScheduleDraft
import com.activaplan.app.domain.model.ScheduleEntry
import com.activaplan.app.domain.model.ScheduleItem
import com.activaplan.app.domain.model.ScheduleWeekDay
import com.activaplan.app.domain.schedule.ScheduleConflicts
import com.activaplan.app.domain.time.LocalClock
import com.activaplan.app.domain.time.minutesOfDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.previousOrSame
import kotlin.random.Random

internal fun randomScheduleId(): String = "schedule_${Random.nextLong().toULong().toString(36)}"

/**
 * Data layar Jadwal yang selalu mengikuti perubahan rutinitas, jadwal, setelan, dan waktu ([ticks]).
 * Entri yang rutinitasnya hilang atau kosong tidak ditampilkan. Melempar bila katalog latihan gagal dimuat.
 */
class ObserveScheduleUseCase(
    private val userData: UserDataRepository,
    private val exercises: ExerciseRepository,
    private val clock: LocalClock,
    private val ticks: Flow<Unit> = minuteTicks(),
    private val estimate: EstimateRoutineMinutesUseCase = EstimateRoutineMinutesUseCase(),
    private val cardio: CardioRepository? = null,
) {

    operator fun invoke(): Flow<ScheduleData> =
        combine(userData.validated, userData.settings, ticks) { validated, settings, _ -> validated to settings }
            .map { (validated, settings) -> build(validated.routines, validated.schedule, settings.defaultTimeMinutes) }

    private suspend fun build(
        routines: List<Routine>,
        schedule: List<ScheduleEntry>,
        defaultTimeMinutes: Int,
    ): ScheduleData {
        val difficulty = exercises.catalog().exercises.associate { it.id to it.difficulty } +
            cardio?.catalog()?.workouts?.associate { it.id to it.difficulty }.orEmpty()
        val options = routines.filter { it.items.isNotEmpty() }.map { routine ->
            RoutineOption(
                id = routine.id,
                name = routine.name,
                level = routine.items.mapNotNull { difficulty[it.exerciseId] }.maxOrNull(),
                exerciseCount = routine.items.size,
                estimatedMinutes = estimate(routine),
            )
        }
        val optionById = options.associateBy { it.id }
        val items = schedule
            .sortedWith(compareBy({ it.dayOfWeek }, { it.timeMinutes }, { it.id }))
            .mapNotNull { entry ->
                optionById[entry.routineId]?.let { routine ->
                    ScheduleItem(
                        id = entry.id,
                        dayOfWeek = entry.dayOfWeek,
                        timeMinutes = entry.timeMinutes,
                        routineId = routine.id,
                        routineName = routine.name,
                        level = routine.level,
                        exerciseCount = routine.exerciseCount,
                        estimatedMinutes = routine.estimatedMinutes,
                        reminder = entry.reminder,
                    )
                }
            }

        val now = clock.now()
        val today = now.date
        val scheduledDays = items.mapTo(mutableSetOf()) { it.dayOfWeek }
        val monday = today.previousOrSame(DayOfWeek.MONDAY)
        val week = (0..6).map { offset ->
            val date = monday.plus(offset, DateTimeUnit.DAY)
            val day = date.dayOfWeek.isoDayNumber
            ScheduleWeekDay(date, day, hasSchedule = day in scheduledDays, isToday = date == today)
        }
        val todayItems = items.filter { it.dayOfWeek == today.dayOfWeek.isoDayNumber }
        val nextToday = todayItems.firstOrNull { it.timeMinutes >= now.minutesOfDay } ?: todayItems.lastOrNull()
        return ScheduleData(today, week, items, nextToday?.id, options, defaultTimeMinutes)
    }
}

/**
 * Menyimpan hasil sheet Tambah/Ubah: satu entri per hari terpilih. Pemeriksaan bentrok dan penulisan terjadi
 * dalam satu transaksi, jadi hasilnya konsisten walau ada perubahan lain bersamaan.
 */
class SaveScheduleUseCase(
    private val userData: UserDataRepository,
    private val estimate: EstimateRoutineMinutesUseCase = EstimateRoutineMinutesUseCase(),
    private val newId: () -> String = ::randomScheduleId,
) {

    suspend operator fun invoke(draft: ScheduleDraft, policy: ConflictPolicy = ConflictPolicy.ASK): SaveScheduleResult {
        val days = draft.days.filter { it in 1..7 }.sorted()
        if (days.isEmpty()) return SaveScheduleResult.NoDay
        val routineId = draft.routineId ?: return SaveScheduleResult.NoRoutine

        var result: SaveScheduleResult = SaveScheduleResult.NoRoutine
        userData.updateSchedule { routines, schedule ->
            val routineById = routines.associateBy { it.id }
            if (routineById[routineId]?.items.isNullOrEmpty()) {
                result = SaveScheduleResult.NoRoutine
                return@updateSchedule schedule
            }
            val editing = schedule.firstOrNull { it.id == draft.editingId }
            val others = schedule.filterNot { it.id == editing?.id }
            val reusedDay = editing?.dayOfWeek?.takeIf { it in days } ?: days.first()
            val entries = days.map { day ->
                val id = if (editing != null && day == reusedDay) editing.id else newId()
                ScheduleEntry(id, day, draft.timeMinutes, routineId, draft.reminder)
            }
            val conflicts = ScheduleConflicts.find(entries, others) { id -> routineById[id]?.let { estimate(it) } ?: 0 }
            if (conflicts.isNotEmpty() && policy == ConflictPolicy.ASK) {
                result = SaveScheduleResult.Conflict(conflicts)
                return@updateSchedule schedule
            }
            result = SaveScheduleResult.Saved(entries)
            val kept = if (policy == ConflictPolicy.REPLACE) others.filterNot { it in conflicts } else others
            kept + entries
        }
        return result
    }
}

class DeleteScheduleEntryUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke(entryId: String) = userData.deleteScheduleEntry(entryId)
}

/** Menyalakan atau mematikan pengingat satu entri jadwal. */
class SetScheduleReminderUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke(entryId: String, enabled: Boolean) = userData.updateSchedule { _, schedule ->
        schedule.map { if (it.id == entryId) it.copy(reminder = enabled) else it }
    }
}

/** Membersihkan entri jadwal yang rutinitasnya sudah terhapus. Mengembalikan jumlah yang dibuang. */
class PruneScheduleUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke(): Int = userData.removeOrphanedSchedule()
}
