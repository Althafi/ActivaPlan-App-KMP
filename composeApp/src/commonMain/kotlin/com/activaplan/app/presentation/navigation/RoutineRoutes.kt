package com.activaplan.app.presentation.navigation

import kotlinx.serialization.Serializable

/** Builder rutinitas; [routineId] null membuat rutinitas baru. */
@Serializable
data class RoutineBuilderRoute(val routineId: String? = null)

/** Jelajah dalam mode pilih; hasilnya dikirim ke Builder lewat [PICKED_EXERCISE_KEY]. */
@Serializable
data object ExercisePickerRoute

/** Pratinjau template; [id] adalah `Template.id`. */
@Serializable
data class TemplatePreviewRoute(val id: String)

/** Pratinjau rencana mingguan; [id] adalah `WeeklyPlan.id`. */
@Serializable
data class PlanPreviewRoute(val id: String)

/** Layar program bertahap; [id] adalah `Program.id`. */
@Serializable
data class ProgramPreviewRoute(val id: String)

/** Sesi latihan; [routineId] adalah `Routine.id`. */
@Serializable
data class SessionRoute(val routineId: String)

/** Riwayat sesi; dibuka dari layar Selesai. */
@Serializable
data object HistoryRoute

/** Kunci `savedStateHandle` entri sebelumnya: ID latihan terpilih (String?) dari mode pilih. */
const val PICKED_EXERCISE_KEY = "picked_exercise"

/** Kunci `savedStateHandle` entri sebelumnya: true setelah Builder menyimpan, agar daftar menampilkan snackbar. */
const val ROUTINE_SAVED_KEY = "routine_saved"

/** Kunci `savedStateHandle` entri Beranda: true setelah Setelan menghapus semua data, agar Beranda menampilkan snackbar. */
const val DATA_CLEARED_KEY = "data_cleared"
