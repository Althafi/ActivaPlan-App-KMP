package com.activaplan.app.data.repository

import activaplan.composeapp.generated.resources.Res

/** Sumber teks JSON latihan; [name] adalah nama file di `composeResources/files`. */
fun interface ExerciseDataSource {
    suspend fun read(name: String): String
}

object ExerciseFiles {
    const val GENERAL = "exercises_general.json"
    const val PUSHUP_BOARD = "exercises_pushup_board.json"
    const val CARDIO = "cardio_workouts.json"
    const val PLANS = "weekly_plans.json"
    const val PROGRAMS = "programs.json"
    const val I18N = "i18n_exercises.json"
}

class ComposeResourceExerciseDataSource : ExerciseDataSource {
    override suspend fun read(name: String): String = Res.readBytes("files/$name").decodeToString()
}
