package com.activaplan.app.domain.i18n

import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.Template
import com.activaplan.app.domain.model.WeeklyPlan
import kotlinx.serialization.Serializable

/** Satu istilah otot beserta nama di tiap bahasa. [terms] ditulis huruf kecil, sesuai data sumber. */
@Serializable
data class MuscleTerm(val terms: List<String>, val names: Map<String, String>)

@Serializable
data class ExerciseText(
    val name: String,
    val steps: List<String>,
    val tips: List<String>,
    val commonMistakes: List<String>,
)

@Serializable
data class CardioText(
    val name: String,
    val summary: String,
    val tips: List<String>,
    val commonMistakes: List<String>,
)

@Serializable
data class PlanDayText(val name: String, val note: String? = null)

/** [days] berkunci nomor hari ("1".."7"). */
@Serializable
data class PlanText(
    val name: String,
    val summary: String,
    val tips: List<String>,
    val days: Map<String, PlanDayText>,
)

@Serializable
data class ZoneText(val label: String, val target: String)

@Serializable
data class DeviceText(
    val name: String,
    val zones: Map<String, ZoneText>,
    val safetyNotes: List<String>,
)

@Serializable
data class LanguagePack(
    val exercises: Map<String, ExerciseText> = emptyMap(),
    val templates: Map<String, String> = emptyMap(),
    val cardio: Map<String, CardioText> = emptyMap(),
    val plans: Map<String, PlanText> = emptyMap(),
    val device: DeviceText? = null,
)

/** Isi `i18n_exercises.json`. Bahasa dasar (data sumber) tidak punya pack; bahasa lain berupa overlay per ID. */
@Serializable
data class ExerciseDictionary(
    val schemaVersion: Int,
    val baseLanguage: String = "id",
    val muscles: List<MuscleTerm> = emptyList(),
    val packs: Map<String, LanguagePack> = emptyMap(),
)

/** Menerjemahkan konten latihan; teks yang tidak ada di kamus memakai teks sumber. */
class ExerciseLocalizer(private val dictionary: ExerciseDictionary) {

    private val muscleIndex: Map<String, MuscleTerm> =
        dictionary.muscles.flatMap { m -> m.terms.map { it.lowercase() to m } }.toMap()

    fun muscle(term: String, language: Language): String =
        muscleIndex[term.lowercase()]?.names?.get(language.code) ?: term

    fun localize(exercise: Exercise, language: Language): Exercise {
        val text = pack(language)?.exercises?.get(exercise.id)
        return exercise.copy(
            name = text?.name ?: exercise.name,
            primaryMuscles = exercise.primaryMuscles.map { muscle(it, language) },
            steps = text?.steps ?: exercise.steps,
            tips = text?.tips ?: exercise.tips,
            commonMistakes = text?.commonMistakes ?: exercise.commonMistakes,
        )
    }

    fun localize(workout: CardioWorkout, language: Language): CardioWorkout {
        val text = pack(language)?.cardio?.get(workout.id) ?: return workout
        return workout.copy(
            name = text.name,
            summary = text.summary,
            tips = text.tips,
            commonMistakes = text.commonMistakes,
        )
    }

    fun localize(plan: WeeklyPlan, language: Language): WeeklyPlan {
        val text = pack(language)?.plans?.get(plan.id) ?: return plan
        return plan.copy(
            name = text.name,
            summary = text.summary,
            tips = text.tips,
            days = plan.days.map { day ->
                val dayText = text.days[day.dayOfWeek.toString()]
                day.copy(name = dayText?.name ?: day.name, note = dayText?.note ?: day.note)
            },
        )
    }

    fun localize(template: Template, language: Language): Template =
        template.copy(name = pack(language)?.templates?.get(template.id) ?: template.name)

    /** Teks perangkat untuk [language]; null berarti pakai data sumber. */
    fun deviceText(language: Language): DeviceText? = pack(language)?.device

    private fun pack(language: Language): LanguagePack? =
        if (language.code == dictionary.baseLanguage) null else dictionary.packs[language.code]
}
