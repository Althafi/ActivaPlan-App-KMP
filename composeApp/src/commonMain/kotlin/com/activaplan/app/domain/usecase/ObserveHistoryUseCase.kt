package com.activaplan.app.domain.usecase

import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.model.HistoryData
import com.activaplan.app.domain.model.HistoryItem
import com.activaplan.app.domain.model.HistoryMonth
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.SessionLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

/**
 * Riwayat sesi untuk layar Riwayat: terbaru di atas, dikelompokkan per bulan.
 * Log bertanggal tidak valid dilewati; log dengan rutinitas yang sudah dihapus tetap tampil tanpa nama.
 */
class ObserveHistoryUseCase(private val userData: UserDataRepository) {

    operator fun invoke(): Flow<HistoryData> =
        combine(userData.routines, userData.sessionLogs, userData.settings) { routines, logs, settings ->
            HistoryData(months = group(logs, routines), keepHistory = settings.keepHistory)
        }

    private fun group(logs: List<SessionLog>, routines: List<Routine>): List<HistoryMonth> {
        val names = routines.associate { it.id to it.name }
        // Log ditambahkan berurutan waktu, jadi untuk hari yang sama indeks yang lebih besar berarti lebih baru.
        val items = logs.withIndex().mapNotNull { (index, log) ->
            val date = runCatching { LocalDate.parse(log.date) }.getOrNull() ?: return@mapNotNull null
            index to HistoryItem(date, names[log.routineId], log.status, log.completedSets, log.totalSets, log.distanceMeters)
        }.sortedWith(compareByDescending<Pair<Int, HistoryItem>> { it.second.date }.thenByDescending { it.first }).map { it.second }
        return items.groupBy { it.date.year to (it.date.month.ordinal + 1) }
            .map { (key, group) -> HistoryMonth(key.first, key.second, group) }
    }
}
