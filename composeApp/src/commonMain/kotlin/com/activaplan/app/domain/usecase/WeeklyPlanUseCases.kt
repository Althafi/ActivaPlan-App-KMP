package com.activaplan.app.domain.usecase

import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.data.repository.WeeklyPlanRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.ApplyPlanResult
import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.ConflictPolicy
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.PlanDay
import com.activaplan.app.domain.model.PlanDayDetail
import com.activaplan.app.domain.model.PlanDayType
import com.activaplan.app.domain.model.PlanItem
import com.activaplan.app.domain.model.PlanSummary
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineItem
import com.activaplan.app.domain.model.RoutineRules
import com.activaplan.app.domain.model.ScheduleEntry
import com.activaplan.app.domain.model.WeeklyPlan
import com.activaplan.app.domain.model.WeeklyPlanDetail
import com.activaplan.app.domain.schedule.ScheduleConflicts

/** Katalog yang dibutuhkan untuk membaca isi rencana, dimuat sekali per pemanggilan dalam bahasa tampilan. */
internal class PlanCatalog(val strength: Map<String, Exercise>, val cardio: Map<String, CardioWorkout>) {

    /** Item rutinitas untuk satu hari rencana; ID yang tidak dikenal dilewati. */
    fun routineItems(day: PlanDay): List<RoutineItem> = day.items.mapNotNull { item ->
        strength[item.id]?.let { strengthItem(it, item) } ?: cardio[item.id]?.toRoutineItem()
    }

    fun itemNames(day: PlanDay): List<String> = day.items.mapNotNull { strength[it.id]?.name ?: cardio[it.id]?.name }

    /** Alat yang dibutuhkan rencana, urut sesuai enum; kosong bila hanya latihan tanpa alat. */
    fun modes(plan: WeeklyPlan): List<Mode> {
        val used = plan.days.flatMap { it.items }.mapNotNull { strength[it.id]?.mode }.toSet()
        return Mode.entries.filter { it in used && it != Mode.NO_EQUIPMENT }
    }

    private fun strengthItem(exercise: Exercise, item: PlanItem): RoutineItem {
        val base = exercise.toRoutineItem()
        return base.copy(
            sets = (item.sets ?: base.sets).coerceIn(RoutineRules.SETS),
            reps = base.reps?.let { (item.reps ?: it).coerceIn(RoutineRules.REPS) },
            durationSeconds = base.durationSeconds?.let { (item.seconds ?: it).coerceIn(RoutineRules.SECONDS) },
            restSeconds = (item.restSeconds ?: base.restSeconds).coerceIn(RoutineRules.REST_SECONDS),
        )
    }

    companion object {
        suspend fun load(exercises: ExerciseRepository, cardio: CardioRepository, language: Language) = PlanCatalog(
            strength = exercises.exercises(language = language).associateBy { it.id },
            cardio = cardio.workouts(language = language).associateBy { it.id },
        )
    }
}

private fun String.clampName(): String = trim().take(RoutineRules.MAX_NAME_LENGTH)

/** ID rutinitas yang dibuat rencana: tetap per rencana dan hari, jadi memakai rencana lagi memperbarui, bukan menggandakan. */
internal fun planRoutineId(planId: String, dayOfWeek: Int): String = "${planId}_d$dayOfWeek"

/** Kartu rencana untuk daftar Rutinitas; rencana tanpa hari latihan yang dikenal dilewati. */
internal fun summarizePlans(plans: List<WeeklyPlan>, catalog: PlanCatalog, estimate: EstimateRoutineMinutesUseCase): List<PlanSummary> =
    plans.mapNotNull { plan ->
        if (plan.trainingDays.none { catalog.routineItems(it).isNotEmpty() }) return@mapNotNull null
        PlanSummary(
            id = plan.id,
            name = plan.name,
            difficulty = plan.difficulty,
            days = plan.days.sortedBy { it.dayOfWeek }.map { it.type },
            weeklyMinutes = plan.trainingDays.sumOf { estimate(catalog.routineItems(it)) },
            modes = catalog.modes(plan),
        )
    }

/** Isi pratinjau rencana; null bila rencana tidak ada atau tak punya hari latihan yang dikenal. */
class GetWeeklyPlanDetailUseCase(
    private val plans: WeeklyPlanRepository,
    private val exercises: ExerciseRepository,
    private val cardio: CardioRepository,
    private val estimate: EstimateRoutineMinutesUseCase = EstimateRoutineMinutesUseCase(),
) {

    suspend operator fun invoke(planId: String, language: Language): WeeklyPlanDetail? {
        val plan = plans.plan(planId, language) ?: return null
        val catalog = PlanCatalog.load(exercises, cardio, language)
        if (plan.trainingDays.none { catalog.routineItems(it).isNotEmpty() }) return null
        return WeeklyPlanDetail(
            id = plan.id,
            name = plan.name,
            difficulty = plan.difficulty,
            summary = plan.summary,
            tips = plan.tips,
            days = plan.days.sortedBy { it.dayOfWeek }.map { day ->
                PlanDayDetail(
                    dayOfWeek = day.dayOfWeek,
                    type = day.type,
                    name = day.name,
                    note = day.note,
                    itemNames = catalog.itemNames(day),
                    minutes = if (day.type == PlanDayType.REST) 0 else estimate(catalog.routineItems(day)),
                )
            },
            modes = catalog.modes(plan),
        )
    }
}

/**
 * Memakai rencana: satu rutinitas per hari latihan (ID tetap, jadi memakai ulang memperbarui) dan satu jadwal per hari
 * pada [timeMinutes]. Semuanya satu transaksi. Jadwal lain yang bertumpuk ditangani [ConflictPolicy] seperti di layar Jadwal;
 * jadwal lama milik rencana ini sendiri selalu diganti.
 */
class ApplyWeeklyPlanUseCase(
    private val userData: UserDataRepository,
    private val plans: WeeklyPlanRepository,
    private val exercises: ExerciseRepository,
    private val cardio: CardioRepository,
    private val estimate: EstimateRoutineMinutesUseCase = EstimateRoutineMinutesUseCase(),
) {

    suspend operator fun invoke(
        planId: String,
        language: Language,
        timeMinutes: Int,
        reminder: Boolean,
        policy: ConflictPolicy = ConflictPolicy.ASK,
    ): ApplyPlanResult {
        require(timeMinutes in 0..1439) { "timeMinutes di luar 0..1439: $timeMinutes" }
        val plan = plans.plan(planId, language) ?: return ApplyPlanResult.NotFound
        val catalog = PlanCatalog.load(exercises, cardio, language)
        val built = plan.trainingDays.mapNotNull { day ->
            val items = catalog.routineItems(day)
            if (items.isEmpty()) null else day to Routine(planRoutineId(plan.id, day.dayOfWeek), day.name.clampName(), items)
        }
        if (built.isEmpty()) return ApplyPlanResult.NotFound
        val ownIds = built.mapTo(mutableSetOf()) { it.second.id }
        val entries = built.map { (day, routine) ->
            ScheduleEntry("${routine.id}_slot", day.dayOfWeek, timeMinutes, routine.id, reminder)
        }

        var result: ApplyPlanResult = ApplyPlanResult.NotFound
        userData.updateRoutinesAndSchedule { routines, schedule ->
            // Jadwal lama milik rencana ini diganti seluruhnya; yang lain hanya diperiksa bentroknya.
            val others = schedule.filterNot { it.routineId in ownIds }
            val allRoutines = routines.filterNot { it.id in ownIds } + built.map { it.second }
            val minutes = allRoutines.associate { it.id to estimate(it) }
            val conflicts = ScheduleConflicts.find(entries, others) { id -> minutes[id] ?: 0 }
            if (conflicts.isNotEmpty() && policy == ConflictPolicy.ASK) {
                result = ApplyPlanResult.Conflict(conflicts)
                return@updateRoutinesAndSchedule routines to schedule
            }
            val kept = if (policy == ConflictPolicy.REPLACE) others.filterNot { it in conflicts } else others
            result = ApplyPlanResult.Applied(built.size)
            allRoutines to (kept + entries)
        }
        return result
    }
}
