package com.activaplan.app.domain.model

import kotlinx.datetime.LocalDate

/** Satu sesi di layar Riwayat. [routineName] null bila rutinitasnya sudah dihapus. */
data class HistoryItem(
    val date: LocalDate,
    val routineName: String?,
    val status: SessionStatus,
    val completedSets: Int,
    val totalSets: Int,
    /** Jarak GPS dalam meter; 0 bila tidak ada. */
    val distanceMeters: Int,
)

/** Sesi dalam satu bulan kalender, terbaru di atas. */
data class HistoryMonth(val year: Int, val month: Int, val items: List<HistoryItem>)

/** Data layar Riwayat. [months] diurutkan dari bulan terbaru. */
data class HistoryData(
    val months: List<HistoryMonth>,
    /** False bila pengguna mematikan "Simpan riwayat sesi" di Setelan; dipakai untuk memilih pesan kosong. */
    val keepHistory: Boolean,
) {
    val totalSessions: Int get() = months.sumOf { it.items.size }
    val completedSessions: Int get() = months.sumOf { month -> month.items.count { it.status == SessionStatus.COMPLETED } }
    val isEmpty: Boolean get() = months.isEmpty()
}
