package com.activaplan.app.domain.usecase

import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.model.CardioDetail

/** Memuat detail kardio; null bila [id] tidak ada di katalog. Melempar bila katalog gagal dimuat. */
class GetCardioDetailUseCase(private val cardio: CardioRepository) {

    suspend operator fun invoke(id: String, language: Language): CardioDetail? {
        val workout = cardio.workout(id, language) ?: return null
        val known = cardio.catalog().workouts.mapTo(mutableSetOf()) { it.id }
        return CardioDetail(
            workout = workout,
            regressionId = workout.regressionId?.takeIf { it in known },
            progressionId = workout.progressionId?.takeIf { it in known },
        )
    }
}
