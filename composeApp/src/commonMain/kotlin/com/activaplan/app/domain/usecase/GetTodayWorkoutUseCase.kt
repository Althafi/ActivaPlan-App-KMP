package com.activaplan.app.domain.usecase

import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.ScheduleEntry
import com.activaplan.app.domain.model.SessionLog
import com.activaplan.app.domain.model.SessionStatus
import com.activaplan.app.domain.model.TodayWorkout
import com.activaplan.app.domain.time.minutesOfDay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber

private const val DAYS_IN_WEEK = 7

/**
 * Jadwal yang ditampilkan di Beranda.
 *
 * Selama masih ada jadwal hari ini yang belum selesai: yang berikutnya (belum lewat), atau yang terakhir bila semua
 * sudah lewat jamnya. Jam hanya pengingat, jadi jadwal yang jamnya lewat tetap bisa dimulai sampai hari berganti.
 *
 * Jadwal hari ini dianggap selesai bila ada log COMPLETED hari ini untuk rutinitasnya (satu log menyelesaikan satu
 * jadwal; rutinitas yang dijadwalkan dua kali sehari butuh dua log). Bila semuanya selesai, yang tampil adalah jadwal
 * hari terdekat berikutnya, ditandai [TodayWorkout.locked] sampai harinya tiba.
 *
 * Jadwal yang rutinitasnya tidak ada atau tanpa latihan dilewati karena tidak bisa dimulai. Null bila tidak ada jadwal hari ini.
 */
class GetTodayWorkoutUseCase(
    private val estimateMinutes: EstimateRoutineMinutesUseCase = EstimateRoutineMinutesUseCase(),
) {

    operator fun invoke(
        schedule: List<ScheduleEntry>,
        routines: List<Routine>,
        exerciseModes: Map<String, Mode>,
        now: LocalDateTime,
        logs: List<SessionLog> = emptyList(),
    ): TodayWorkout? {
        val routineById = routines.filter { it.items.isNotEmpty() }.associateBy { it.id }

        fun entriesOn(dayOfWeek: Int): List<Pair<ScheduleEntry, Routine>> = schedule
            .filter { it.dayOfWeek == dayOfWeek }
            .sortedWith(compareBy({ it.timeMinutes }, { it.id }))
            .mapNotNull { entry -> routineById[entry.routineId]?.let { entry to it } }

        fun workout(entry: ScheduleEntry, routine: Routine, passed: Boolean, daysAhead: Int) = TodayWorkout(
            routineId = routine.id,
            routineName = routine.name,
            timeMinutes = entry.timeMinutes,
            exerciseCount = routine.items.size,
            estimatedMinutes = estimateMinutes(routine),
            passed = passed,
            requiresSafetyCheck = routine.items.any { exerciseModes[it.exerciseId] == Mode.BOARD_ROPE },
            dayOfWeek = entry.dayOfWeek,
            daysAhead = daysAhead,
        )

        val todayNumber = now.dayOfWeek.isoDayNumber
        val today = entriesOn(todayNumber)
        if (today.isEmpty()) return null

        val pending = withoutCompleted(today, logs, now.date.toString())
        if (pending.isNotEmpty()) {
            val upcoming = pending.firstOrNull { (entry, _) -> entry.timeMinutes >= now.minutesOfDay }
            val (entry, routine) = upcoming ?: pending.last()
            return workout(entry, routine, passed = upcoming == null, daysAhead = 0)
        }

        // Semua selesai: hari terjadwal terdekat berikutnya; paling jauh hari yang sama pekan depan.
        for (ahead in 1..DAYS_IN_WEEK) {
            val next = entriesOn((todayNumber - 1 + ahead) % DAYS_IN_WEEK + 1).firstOrNull() ?: continue
            return workout(next.first, next.second, passed = false, daysAhead = ahead)
        }
        return null
    }

    private fun withoutCompleted(
        entries: List<Pair<ScheduleEntry, Routine>>,
        logs: List<SessionLog>,
        date: String,
    ): List<Pair<ScheduleEntry, Routine>> {
        val remaining = logs
            .filter { it.date == date && it.status == SessionStatus.COMPLETED }
            .groupingBy { it.routineId }
            .eachCount()
            .toMutableMap()
        return entries.filter { (entry, _) ->
            val left = remaining[entry.routineId] ?: 0
            if (left > 0) remaining[entry.routineId] = left - 1
            left == 0
        }
    }
}
