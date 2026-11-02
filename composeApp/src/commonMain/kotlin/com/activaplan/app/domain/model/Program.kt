package com.activaplan.app.domain.model

import kotlinx.serialization.Serializable

/** Satu pekan program: [title] sudah memuat level dan nomor pekan, [focus] menjelaskan tujuan pekan itu. */
@Serializable
data class ProgramWeek(
    val week: Int,
    val title: String,
    val focus: String,
    val tips: List<String> = emptyList(),
    val days: List<PlanDay>,
)

/**
 * Program bertahap beberapa pekan (maksimal empat) yang memadukan kekuatan dan kardio. Tiap pekan berbentuk
 * [WeeklyPlan] (lihat [toPlan]), jadi pratinjau dan penerapannya memakai alur rencana mingguan yang sama.
 */
@Serializable
data class Program(
    val id: String,
    val name: String,
    val difficulty: Difficulty,
    val summary: String,
    val tips: List<String> = emptyList(),
    val weeks: List<ProgramWeek>,
) {
    /** Pekan [week] sebagai rencana mingguan. ID rutinitas yang dibuat memakai [id] program, bukan ID pekan. */
    fun toPlan(week: ProgramWeek): WeeklyPlan = WeeklyPlan(
        id = programWeekId(id, week.week),
        name = week.title,
        difficulty = difficulty,
        summary = week.focus,
        tips = week.tips,
        days = week.days,
        programId = id,
        week = week.week,
    )

    val weekPlans: List<WeeklyPlan> get() = weeks.map(::toPlan)

    companion object {
        const val MAX_WEEKS = 4
    }
}

/** ID rencana untuk pekan [week] program [programId], mis. `prog_easy_4w_w2`. */
fun programWeekId(programId: String, week: Int): String = "${programId}_w$week"

/** Kartu program di daftar. [weeks] urut pekan; tiap pekan memakai [PlanSummary] (id = ID rencana pekan itu). */
data class ProgramSummary(
    val id: String,
    val name: String,
    val difficulty: Difficulty,
    val summary: String,
    val weeks: List<PlanSummary>,
    val modes: List<Mode>,
) {
    val totalMinutes: Int get() = weeks.sumOf { it.weeklyMinutes }
}

/** Satu pekan di layar program: [plan] untuk statistik dan membuka pratinjau, [focus] tujuan pekan itu. */
data class ProgramWeekCard(val week: Int, val plan: PlanSummary, val focus: String)

/** Isi layar program: ringkasan, tips, dan pekan-pekannya. */
data class ProgramDetail(
    val id: String,
    val name: String,
    val difficulty: Difficulty,
    val summary: String,
    val tips: List<String>,
    val weeks: List<ProgramWeekCard>,
    val modes: List<Mode>,
    /** Nomor pekan program ini yang sedang dijalani; null bila program ini bukan program aktif. */
    val activeWeek: Int? = null,
)
