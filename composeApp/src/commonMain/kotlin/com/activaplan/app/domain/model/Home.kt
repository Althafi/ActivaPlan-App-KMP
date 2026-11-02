package com.activaplan.app.domain.model

/** Waktu sapaan di Beranda: pagi 04-10, siang 11-14, sore 15-17, malam 18-03. */
enum class GreetingPeriod {
    MORNING, NOON, AFTERNOON, EVENING;

    companion object {
        fun at(hour: Int): GreetingPeriod = when (hour) {
            in 4..10 -> MORNING
            in 11..14 -> NOON
            in 15..17 -> AFTERNOON
            else -> EVENING
        }
    }
}

/**
 * Jadwal yang ditampilkan di kartu "Hari ini". [passed] true bila semua jadwal hari ini sudah lewat dan ini yang terakhir.
 * [requiresSafetyCheck] true bila ada latihan tali, jadi sesi diawali cek keselamatan.
 */
data class TodayWorkout(
    val routineId: String,
    val routineName: String,
    val timeMinutes: Int,
    val exerciseCount: Int,
    val estimatedMinutes: Int,
    val passed: Boolean,
    val requiresSafetyCheck: Boolean,
)

/** Satu lingkaran streak. [dayOfWeek]: 1 = Senin .. 7 = Minggu. */
data class StreakDay(val dayOfWeek: Int, val completed: Boolean, val isToday: Boolean)

/** Tujuh hari pekan ini (Senin-Minggu) dan [streak]: hari terjadwal berturut-turut yang selesai. */
data class WeekStreak(val days: List<StreakDay>, val streak: Int)

/** Template untuk kartu rekomendasi. [primaryMode] adalah mode latihan terbanyak di template, null bila tidak diketahui. */
data class TemplateRecommendation(
    val id: String,
    val name: String,
    val difficulty: Difficulty,
    val exerciseCount: Int,
    val primaryMode: Mode?,
)

/**
 * Kemajuan program bertahap di Beranda. [dayInWeek] = hari ke berapa sejak pekan ini diterapkan (1 dan seterusnya);
 * [weekCompleted] true setelah tujuh hari. [nextWeek] dan [nextPlanId] null bila ini pekan terakhir.
 */
data class ProgramProgress(
    val programId: String,
    val programName: String,
    val week: Int,
    val totalWeeks: Int,
    val weekTitle: String,
    val dayInWeek: Int,
    val weekCompleted: Boolean,
    val nextWeek: Int?,
    val nextPlanId: String?,
) {
    /** Pekan terakhir sudah lewat tujuh hari. */
    val finished: Boolean get() = nextWeek == null && weekCompleted
}

data class HomeData(
    val greeting: GreetingPeriod,
    val today: TodayWorkout?,
    val week: WeekStreak,
    val recommendations: List<TemplateRecommendation>,
    /** Null bila tidak ada program yang sedang dijalani. */
    val program: ProgramProgress? = null,
)
