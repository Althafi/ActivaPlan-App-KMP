package com.activaplan.app.domain.model

/**
 * Penanda bahwa [SessionExercise] adalah satu segmen kardio. [itemIndex] = urutan item di rutinitas (membedakan
 * kardio yang sama dua kali), [position] dari [total] = urutan segmen di kardio itu, mulai dari 1.
 */
data class CardioStep(
    val itemIndex: Int,
    val workoutName: String,
    val segment: CardioSegment,
    val position: Int,
    val total: Int,
)

/**
 * Satu latihan dalam sesi: data latihan digabung dengan nilai rutinitas (set, rep atau detik, istirahat).
 * Tepat satu dari [reps] dan [durationSeconds] terisi.
 */
data class SessionExercise(
    val exercise: Exercise,
    val sets: Int,
    val reps: Int?,
    val durationSeconds: Int?,
    val restSeconds: Int,
    /** Terisi bila ini segmen kardio: pindah otomatis saat waktu habis, tanpa sheet Gagal. */
    val cardio: CardioStep? = null,
) {
    /** Latihan durasi: ring menghitung mundur, bukan menghitung repetisi. */
    val isTimed: Boolean get() = reps == null

    companion object {
        /** Nilai dari [item]; yang kosong di item memakai bawaan [exercise]. */
        fun of(item: RoutineItem, exercise: Exercise): SessionExercise {
            val useDefaults = item.reps == null && item.durationSeconds == null
            return SessionExercise(
                exercise = exercise,
                sets = item.sets,
                reps = if (useDefaults) exercise.reps else item.reps,
                durationSeconds = if (useDefaults) exercise.durationSeconds else item.durationSeconds,
                restSeconds = item.restSeconds,
            )
        }

        /**
         * Membuka [workout] menjadi satu langkah per segmen. Tiap segmen adalah latihan satu set berdurasi tanpa
         * istirahat, jadi state machine, jeda, dialog Hentikan, log, dan streak yang sama bisa dipakai.
         */
        fun ofCardio(workout: CardioWorkout, itemIndex: Int): List<SessionExercise> {
            val segments = workout.expand()
            return segments.mapIndexed { i, segment ->
                SessionExercise(
                    exercise = Exercise(
                        id = "${workout.id}#${i + 1}", name = workout.name, mode = Mode.NO_EQUIPMENT,
                        category = Category.FULL_BODY, difficulty = workout.difficulty, primaryMuscles = emptyList(),
                        sets = 1, durationSeconds = segment.durationSeconds, restSeconds = 0,
                        steps = emptyList(), tips = emptyList(), commonMistakes = emptyList(),
                    ),
                    sets = 1,
                    reps = null,
                    durationSeconds = segment.durationSeconds,
                    restSeconds = 0,
                    cardio = CardioStep(itemIndex, workout.name, segment, i + 1, segments.size),
                )
            }
        }

        /** Pengganti hasil "Turun level": set tetap, rep/durasi dan istirahat memakai bawaan latihan pengganti. */
        fun regressed(exercise: Exercise, sets: Int) = SessionExercise(
            exercise = exercise,
            sets = sets,
            reps = exercise.reps,
            durationSeconds = if (exercise.reps == null) exercise.durationSeconds else null,
            restSeconds = exercise.restSeconds,
        )
    }
}

/**
 * Bahan sesi dari satu rutinitas. [catalog] memuat semua latihan (sudah diterjemahkan) untuk mencari regresi.
 * [skippedItems] = item yang latihannya tidak ada di katalog. [safetyNotes] hanya terisi bila ada latihan tali.
 */
data class SessionPlan(
    val routineId: String,
    val routineName: String,
    val exercises: List<SessionExercise>,
    val catalog: Map<String, Exercise>,
    val requiresSafetyCheck: Boolean,
    val safetyNotes: List<String>,
    val skippedItems: Int,
) {
    val totalSets: Int get() = exercises.sumOf { it.sets }
}

/** Ringkasan akhir sesi, juga isi [SessionLog]. */
data class SessionSummary(
    val status: SessionStatus,
    val completedSets: Int,
    val totalSets: Int,
    val completedExercises: Int,
    val totalExercises: Int,
    val minutes: Int,
    /** Jarak GPS dalam meter; diisi oleh ViewModel, bukan state machine. */
    val distanceMeters: Int = 0,
)

/** Layar atau tahap sesi saat ini. */
sealed interface SessionPhase {
    /** Cek keselamatan sebelum sesi yang memuat latihan tali. */
    data object SafetyCheck : SessionPhase

    /** Latihan berjalan. [repsDone] untuk latihan repetisi; [remainingMillis] hanya untuk latihan durasi. */
    data class Working(val repsDone: Int = 0, val remainingMillis: Long? = null) : SessionPhase

    data class Resting(val remainingMillis: Long, val totalMillis: Long) : SessionPhase

    /**
     * Sesi berakhir. [stopped] true bila pengguna menghentikannya. Layar "Latihan Selesai!" hanya tampil
     * bila sesi berakhir wajar dan ada set yang selesai ([showsSummary]); selain itu layar sesi ditutup.
     */
    data class Finished(val summary: SessionSummary, val stopped: Boolean) : SessionPhase {
        val showsSummary: Boolean get() = !stopped && summary.completedSets > 0
    }
}

/** Lapisan di atas layar latihan: sheet Gagal atau dialog "Hentikan latihan?". Timer berhenti selama ada. */
enum class SessionOverlay { NONE, FAILED, CONFIRM_STOP }
