package com.activaplan.app.domain.model

/** Satu jadwal rutinitas; [dayOfWeek] 1 = Senin .. 7 = Minggu, [timeMinutes] menit sejak 00:00. */
data class ScheduleSlot(val dayOfWeek: Int, val timeMinutes: Int)

/** Kartu rutinitas milik pengguna. [level] null bila tidak ada latihan yang dikenal; [schedule] urut pekan lalu jam. */
data class RoutineSummary(
    val id: String,
    val name: String,
    val level: Difficulty?,
    val exerciseCount: Int,
    val estimatedMinutes: Int,
    val schedule: List<ScheduleSlot>,
)

data class TemplateSummary(
    val id: String,
    val name: String,
    val difficulty: Difficulty,
    val exerciseCount: Int,
    val estimatedMinutes: Int,
)

data class RoutinesData(
    val routines: List<RoutineSummary>,
    val templates: List<TemplateSummary>,
    /** Rencana mingguan; kosong bila katalog rencana tidak tersedia. */
    val plans: List<PlanSummary> = emptyList(),
    /** Program bertahap beberapa pekan; kosong bila katalog program tidak tersedia. */
    val programs: List<ProgramSummary> = emptyList(),
)

/** Isi pratinjau template; [exercises] hanya latihan yang ada di katalog, sudah diterjemahkan. */
data class TemplateDetail(
    val id: String,
    val name: String,
    val difficulty: Difficulty,
    val exercises: List<Exercise>,
    val estimatedMinutes: Int,
)

/** Hasil memuat rutinitas ke Builder. [skippedItems] = latihan yang tidak ada di katalog dan dilewati. */
sealed interface DraftLoad {
    data class Loaded(val draft: RoutineDraft, val baseline: Routine, val skippedItems: Int) : DraftLoad
    data object NotFound : DraftLoad
}

sealed interface SaveRoutineResult {
    data object Saved : SaveRoutineResult
    data object InvalidName : SaveRoutineResult
    data object NoItems : SaveRoutineResult
    data object TooManyItems : SaveRoutineResult
}
