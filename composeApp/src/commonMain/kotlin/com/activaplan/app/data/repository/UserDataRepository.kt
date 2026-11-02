package com.activaplan.app.data.repository

import com.activaplan.app.data.local.UserDataStore
import com.activaplan.app.domain.model.AddToRoutineResult
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineItem
import com.activaplan.app.domain.model.ScheduleEntry
import com.activaplan.app.domain.model.SessionLog
import com.activaplan.app.domain.model.Settings
import com.activaplan.app.domain.validation.UserDataValidation
import com.activaplan.app.domain.validation.UserDataValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Batas riwayat tersimpan; yang terlama dibuang agar satu JSON di DataStore tidak membengkak tanpa batas. */
private const val MAX_SESSION_LOGS = 1_000

/** Akses data pengguna (rutinitas, jadwal, riwayat sesi, setelan) di atas [UserDataStore]. */
class UserDataRepository(
    private val store: UserDataStore,
    private val exercises: ExerciseRepository,
    private val cardio: CardioRepository? = null,
) {
    val routines: Flow<List<Routine>> = store.data.map { it.routines }.distinctUntilChanged()
    val schedule: Flow<List<ScheduleEntry>> = store.data.map { it.schedule }.distinctUntilChanged()
    val sessionLogs: Flow<List<SessionLog>> = store.data.map { it.sessionLogs }.distinctUntilChanged()
    val settings: Flow<Settings> = store.data.map { it.settings }.distinctUntilChanged()

    /** Rutinitas dan jadwal yang valid terhadap katalog latihan kekuatan dan kardio; entri rusak diabaikan dan dilaporkan di `issues`. */
    val validated: Flow<UserDataValidation> = flow {
        val exerciseIds = exercises.catalog().exercises.map { it.id }.toSet() +
            cardio?.catalog()?.workouts?.map { it.id }.orEmpty()
        emitAll(store.data.map { UserDataValidator.validate(it.routines, it.schedule, exerciseIds) })
    }

    /** Menambah rutinitas baru, atau mengganti yang ber-ID sama. */
    suspend fun saveRoutine(routine: Routine) = store.update { d ->
        d.copy(routines = d.routines.upsert(routine) { it.id })
    }

    /** Menambah [item] di akhir rutinitas [routineId] secara atomik; ditolak bila rutinitas tidak ada atau sudah berisi [maxItems] item. */
    suspend fun appendRoutineItem(routineId: String, item: RoutineItem, maxItems: Int): AddToRoutineResult {
        var result: AddToRoutineResult = AddToRoutineResult.RoutineNotFound
        store.update { d ->
            val routine = d.routines.firstOrNull { it.id == routineId }
            when {
                routine == null -> d
                routine.items.size >= maxItems -> {
                    result = AddToRoutineResult.RoutineFull
                    d
                }
                else -> {
                    result = AddToRoutineResult.Added(routine.name)
                    d.copy(routines = d.routines.upsert(routine.copy(items = routine.items + item)) { it.id })
                }
            }
        }
        return result
    }

    /** Menghapus rutinitas beserta jadwal yang menunjuk padanya. */
    suspend fun deleteRoutine(id: String) = store.update { d ->
        d.copy(
            routines = d.routines.filterNot { it.id == id },
            schedule = d.schedule.filterNot { it.routineId == id },
        )
    }

    suspend fun saveScheduleEntry(entry: ScheduleEntry) = store.update { d ->
        d.copy(schedule = d.schedule.upsert(entry) { it.id })
    }

    suspend fun deleteScheduleEntry(id: String) = store.update { d ->
        d.copy(schedule = d.schedule.filterNot { it.id == id })
    }

    /** Mengganti seluruh jadwal secara atomik dari jadwal dan rutinitas saat ini, mis. untuk memeriksa bentrok lalu menulis. */
    suspend fun updateSchedule(transform: (routines: List<Routine>, schedule: List<ScheduleEntry>) -> List<ScheduleEntry>) =
        store.update { d -> d.copy(schedule = transform(d.routines, d.schedule)) }

    /**
     * Mengganti rutinitas dan jadwal sekaligus secara atomik, mis. saat memakai rencana mingguan. [settings] dipanggil
     * sesudah [transform] dan ikut dalam transaksi yang sama, jadi boleh membaca hasil yang ditangkap dari [transform].
     */
    suspend fun updateRoutinesAndSchedule(
        settings: (Settings) -> Settings = { it },
        transform: (routines: List<Routine>, schedule: List<ScheduleEntry>) -> Pair<List<Routine>, List<ScheduleEntry>>,
    ) = store.update { d ->
        val (routines, schedule) = transform(d.routines, d.schedule)
        d.copy(routines = routines, schedule = schedule, settings = settings(d.settings))
    }

    /** Membuang entri jadwal yang rutinitasnya sudah tidak ada. Mengembalikan jumlah yang dibuang. */
    suspend fun removeOrphanedSchedule(): Int {
        var removed = 0
        store.update { d ->
            val routineIds = d.routines.mapTo(mutableSetOf()) { it.id }
            val kept = d.schedule.filter { it.routineId in routineIds }
            removed = d.schedule.size - kept.size
            d.copy(schedule = kept)
        }
        return removed
    }

    /** Menambah log sesi; tidak melakukan apa pun jika `keepHistory` mati. Mengembalikan true bila log ditulis. */
    suspend fun appendSessionLog(log: SessionLog): Boolean {
        var written = false
        store.update { d ->
            written = d.settings.keepHistory
            if (written) d.copy(sessionLogs = (d.sessionLogs + log).takeLast(MAX_SESSION_LOGS)) else d
        }
        return written
    }

    suspend fun clearSessionLogs() = store.update { it.copy(sessionLogs = emptyList()) }

    suspend fun updateSettings(transform: (Settings) -> Settings) = store.update { d ->
        d.copy(settings = transform(d.settings))
    }

    /** Menghapus rutinitas, jadwal, riwayat, dan program aktif. Setelan lain dipertahankan. */
    suspend fun clearAllData() = store.update {
        it.copy(
            routines = emptyList(),
            schedule = emptyList(),
            sessionLogs = emptyList(),
            settings = it.settings.copy(activeProgram = null),
        )
    }

    private inline fun <T> List<T>.upsert(item: T, id: (T) -> String): List<T> {
        val index = indexOfFirst { id(it) == id(item) }
        return if (index < 0) this + item else toMutableList().also { it[index] = item }
    }
}
