package com.activaplan.app.domain.usecase

import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.ExploreQuery

/** Filter Jelajah untuk kardio: level dan teks (nama atau ringkasan, tanpa membedakan huruf) digabung AND. */
class FilterCardioUseCase {

    operator fun invoke(workouts: List<CardioWorkout>, query: ExploreQuery): List<CardioWorkout> {
        val text = query.text.trim()
        return workouts.filter { w ->
            (query.difficulty == null || w.difficulty == query.difficulty) &&
                (text.isEmpty() || w.name.contains(text, ignoreCase = true) || w.summary.contains(text, ignoreCase = true))
        }
    }
}
