package com.activaplan.app.domain.model

import kotlinx.serialization.Serializable

enum class PlanDayType { STRENGTH, CARDIO, REST }

/**
 * Satu entri latihan dalam hari rencana. [id] adalah ID latihan kekuatan atau kardio. Nilai lain menimpa bawaan latihan
 * (hanya berlaku untuk kekuatan): [reps] untuk latihan repetisi, [seconds] untuk latihan durasi.
 */
@Serializable
data class PlanItem(
    val id: String,
    val sets: Int? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    val restSeconds: Int? = null,
)

/** Satu hari dalam rencana; hari [PlanDayType.REST] tidak punya [items] dan boleh punya [note]. [name] jadi nama rutinitas (maks 40 karakter). */
@Serializable
data class PlanDay(
    val dayOfWeek: Int,
    val type: PlanDayType,
    val name: String,
    val items: List<PlanItem> = emptyList(),
    val note: String? = null,
)

/** Rencana satu pekan penuh (7 hari, 1 = Senin .. 7 = Minggu). Memakai rencana membuat satu rutinitas per hari latihan lalu mengisi Jadwal. */
@Serializable
data class WeeklyPlan(
    val id: String,
    val name: String,
    val difficulty: Difficulty,
    val summary: String,
    val tips: List<String>,
    val days: List<PlanDay>,
    /** Diisi bila rencana ini satu pekan dari sebuah [Program]; routine dan jadwalnya dipakai bersama semua pekan program. */
    val programId: String? = null,
    val week: Int? = null,
) {
    val trainingDays: List<PlanDay> get() = days.filter { it.type != PlanDayType.REST }
}

/** Kartu rencana di daftar. [days] urut Senin..Minggu; [modes] = alat yang dibutuhkan (kosong bila tanpa alat). */
data class PlanSummary(
    val id: String,
    val name: String,
    val difficulty: Difficulty,
    val days: List<PlanDayType>,
    val weeklyMinutes: Int,
    val modes: List<Mode>,
) {
    val strengthDays: Int get() = days.count { it == PlanDayType.STRENGTH }
    val cardioDays: Int get() = days.count { it == PlanDayType.CARDIO }
    val restDays: Int get() = days.count { it == PlanDayType.REST }
    val trainingDays: Int get() = days.size - restDays
}

/** Satu hari di pratinjau: [itemNames] sudah diterjemahkan, [minutes] estimasi (0 untuk istirahat). */
data class PlanDayDetail(
    val dayOfWeek: Int,
    val type: PlanDayType,
    val name: String,
    val note: String?,
    val itemNames: List<String>,
    val minutes: Int,
)

data class WeeklyPlanDetail(
    val id: String,
    val name: String,
    val difficulty: Difficulty,
    val summary: String,
    val tips: List<String>,
    val days: List<PlanDayDetail>,
    val modes: List<Mode>,
) {
    val trainingDays: Int get() = days.count { it.type != PlanDayType.REST }
    val restDays: Int get() = days.count { it.type == PlanDayType.REST }
    val weeklyMinutes: Int get() = days.sumOf { it.minutes }
}

sealed interface ApplyPlanResult {
    /** [routines] rutinitas dibuat atau diperbarui, masing-masing dijadwalkan pada harinya. */
    data class Applied(val routines: Int) : ApplyPlanResult

    /** Ada jadwal lain yang bertumpuk; belum ada yang diubah. */
    data class Conflict(val conflicts: List<ScheduleEntry>) : ApplyPlanResult

    data object NotFound : ApplyPlanResult
}
