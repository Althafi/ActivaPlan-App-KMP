package com.activaplan.app.domain.usecase

import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.WeeklyPlanRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.i18n.resolve
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.PlanSummary
import com.activaplan.app.domain.model.ProgramSummary
import com.activaplan.app.domain.model.RoutineItem
import com.activaplan.app.domain.model.RoutineSummary
import com.activaplan.app.domain.model.RoutinesData
import com.activaplan.app.domain.model.ScheduleSlot
import com.activaplan.app.domain.model.TemplateSummary
import com.activaplan.app.domain.validation.UserDataValidation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Daftar Rutinitas: rutinitas pengguna (sudah divalidasi, jadi latihan hilang dilewati) dan template siap pakai,
 * selalu mengikuti perubahan data. Melempar bila katalog latihan gagal dimuat.
 */
class ObserveRoutinesUseCase(
    private val userData: UserDataRepository,
    private val exercises: ExerciseRepository,
    private val estimate: EstimateRoutineMinutesUseCase = EstimateRoutineMinutesUseCase(),
    private val cardio: CardioRepository? = null,
    private val plans: WeeklyPlanRepository? = null,
) {

    operator fun invoke(): Flow<RoutinesData> =
        combine(userData.validated, userData.settings) { validated, settings -> validated to settings.language }
            .map { (validated, language) -> build(validated, language.resolve()) }

    private suspend fun build(validated: UserDataValidation, language: Language): RoutinesData {
        val byId = exercises.catalog().byId
        val levels = byId.mapValues { it.value.difficulty } +
            cardio?.catalog()?.workouts?.associate { it.id to it.difficulty }.orEmpty()
        val slots = validated.schedule.groupBy({ it.routineId }, { ScheduleSlot(it.dayOfWeek, it.timeMinutes) })
        val routines = validated.routines.map { routine ->
            RoutineSummary(
                id = routine.id,
                name = routine.name,
                level = routine.items.mapNotNull { levels[it.exerciseId] }.maxOrNull(),
                exerciseCount = routine.items.size,
                estimatedMinutes = estimate(routine),
                schedule = slots[routine.id].orEmpty().sortedWith(compareBy({ it.dayOfWeek }, { it.timeMinutes })),
            )
        }
        val templates = exercises.templates(language).mapNotNull { template ->
            val items = byId.routineItems(template.items)
            if (items.isEmpty()) {
                null
            } else {
                TemplateSummary(template.id, template.name, template.difficulty, items.size, estimate(items))
            }
        }
        var planSummaries: List<PlanSummary> = emptyList()
        var programSummaries: List<ProgramSummary> = emptyList()
        if (plans != null && cardio != null) {
            val catalog = PlanCatalog.load(exercises, cardio, language)
            planSummaries = summarizePlans(plans.plans(language), catalog, estimate)
            programSummaries = summarizePrograms(plans.programs(language), catalog, estimate)
        }
        return RoutinesData(routines, templates, planSummaries, programSummaries)
    }
}

internal fun Map<String, Exercise>.routineItems(ids: List<String>): List<RoutineItem> =
    ids.mapNotNull { this[it]?.toRoutineItem() }
