package com.activaplan.app.presentation.navigation

import kotlinx.serialization.Serializable

/** Rute detail latihan; [id] adalah `Exercise.id`. */
@Serializable
data class ExerciseDetailRoute(val id: String)

/** Rute detail kardio; [id] adalah `CardioWorkout.id`. */
@Serializable
data class CardioDetailRoute(val id: String)
