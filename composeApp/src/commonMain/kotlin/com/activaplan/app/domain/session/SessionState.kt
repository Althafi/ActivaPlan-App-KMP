package com.activaplan.app.domain.session

import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.RoutineRules
import com.activaplan.app.domain.model.SessionExercise
import com.activaplan.app.domain.model.SessionOverlay
import com.activaplan.app.domain.model.SessionPhase
import com.activaplan.app.domain.model.SessionPlan
import com.activaplan.app.domain.model.SessionStatus
import com.activaplan.app.domain.model.SessionSummary

/** Set atau latihan yang menyusul setelah istirahat. */
data class NextUp(val exercise: SessionExercise, val setNumber: Int)

/**
 * State machine sesi latihan, murni dan tanpa jam: waktu masuk lewat [advance] sehingga hasilnya sama
 * pada rotasi layar maupun kembali dari background (pemanggil memberi selisih waktu nyata).
 *
 * Alur: [SessionPhase.SafetyCheck] (hanya bila ada latihan tali) -> [SessionPhase.Working] -> [SessionPhase.Resting]
 * -> ... -> [SessionPhase.Finished]. Sheet Gagal dan dialog Hentikan adalah [overlay] di atas Working/Resting.
 * Semua transisi di luar tahap yang sah dibiarkan (mengembalikan state yang sama).
 */
data class SessionState(
    val plan: SessionPlan,
    /** Latihan sesi ini; berbeda dari rutinitas asli bila ada "Turun level". Rutinitas asli tidak berubah. */
    val exercises: List<SessionExercise>,
    /** Set yang selesai per latihan, sejajar dengan [exercises]. */
    val setsDone: List<Int>,
    val index: Int,
    val phase: SessionPhase,
    val overlay: SessionOverlay = SessionOverlay.NONE,
    val paused: Boolean = false,
    /** Waktu berjalan tanpa jeda, sumber "menit" di ringkasan. */
    val activeMillis: Long = 0,
) {
    val current: SessionExercise get() = exercises[index]

    /** Nomor set yang sedang dikerjakan, mulai dari 1. */
    val setNumber: Int get() = (setsDone[index] + 1).coerceAtMost(current.sets)

    /** Kunci kelompok tiap langkah: latihan kekuatan berdiri sendiri, segmen kardio dikelompokkan per item rutinitas. */
    private val groupKeys: List<String>
        get() = exercises.mapIndexed { i, e -> e.cardio?.let { "c${it.itemIndex}" } ?: "s$i" }

    /** Urutan "latihan" saat ini (satu kardio dihitung satu), mulai dari 0, dan jumlahnya. Untuk judul "Latihan 2 dari 5". */
    val groupIndex: Int get() = groupKeys.take(index + 1).distinct().size - 1
    val groupCount: Int get() = groupKeys.distinct().size

    /** Latihan regresi untuk "Turun ke ..."; null bila tidak ada atau tidak ada di katalog. */
    val regression: Exercise? get() = current.exercise.regressionId?.let { plan.catalog[it] }

    /** Timer berjalan hanya pada Working/Resting tanpa jeda dan tanpa sheet atau dialog. */
    val isRunning: Boolean
        get() = !paused && overlay == SessionOverlay.NONE && (phase is SessionPhase.Working || phase is SessionPhase.Resting)

    /** Yang menyusul setelah istirahat: set berikutnya latihan ini, atau latihan berikutnya. */
    val nextUp: NextUp?
        get() = if (setsDone[index] < current.sets) {
            NextUp(current, setNumber)
        } else {
            exercises.getOrNull(index + 1)?.let { NextUp(it, 1) }
        }

    fun confirmSafety(): SessionState =
        if (phase == SessionPhase.SafetyCheck) copy(phase = working(current)) else this

    /** Menambah waktu berjalan sebesar [deltaMillis]; latihan durasi yang habis membuka sheet Gagal, istirahat yang habis lanjut. */
    fun advance(deltaMillis: Long): SessionState {
        if (!isRunning || deltaMillis <= 0) return this
        val ticked = copy(activeMillis = activeMillis + deltaMillis)
        return when (val p = phase) {
            is SessionPhase.Working -> {
                val remaining = p.remainingMillis ?: return ticked
                val left = remaining - deltaMillis
                when {
                    left > 0 -> ticked.copy(phase = p.copy(remainingMillis = left))
                    // Segmen kardio yang habis berpindah sendiri; sisa waktu ketukan dibawa ke segmen berikutnya.
                    current.cardio != null -> {
                        val overshoot = -left
                        copy(activeMillis = activeMillis + deltaMillis - overshoot).completeSet().advance(overshoot)
                    }
                    else -> ticked.copy(phase = p.copy(remainingMillis = 0L), overlay = SessionOverlay.FAILED)
                }
            }
            is SessionPhase.Resting -> {
                val left = p.remainingMillis - deltaMillis
                if (left > 0) ticked.copy(phase = p.copy(remainingMillis = left)) else ticked.afterRest()
            }
            else -> ticked
        }
    }

    /** Ketuk ring: satu repetisi lagi, paling banyak sampai target. Hanya untuk latihan repetisi. */
    fun countRep(): SessionState {
        val p = phase as? SessionPhase.Working ?: return this
        val target = current.reps ?: return this
        if (paused || overlay != SessionOverlay.NONE || p.repsDone >= target) return this
        return copy(phase = p.copy(repsDone = p.repsDone + 1))
    }

    /** Selesai Set: set dihitung selesai berapa pun repetisi yang sudah diketuk. */
    fun completeSet(): SessionState {
        if (phase !is SessionPhase.Working || paused || overlay != SessionOverlay.NONE) return this
        val done = setsDone.toMutableList().also { it[index] += 1 }
        val counted = copy(setsDone = done)
        val exerciseDone = done[index] >= current.sets
        return when {
            exerciseDone && index == exercises.lastIndex -> counted.finish(stopped = false)
            current.restSeconds > 0 -> {
                val millis = current.restSeconds * MILLIS_PER_SECOND
                counted.copy(phase = SessionPhase.Resting(millis, millis))
            }
            else -> counted.afterRest()
        }
    }

    /** Lewati ditekan: set dianggap belum selesai dan sheet Gagal terbuka. */
    fun requestSkip(): SessionState = when {
        phase !is SessionPhase.Working || paused || overlay != SessionOverlay.NONE -> this
        // Segmen kardio tidak "gagal": melewatinya langsung lanjut ke segmen berikutnya.
        current.cardio != null -> completeSet()
        else -> copy(overlay = SessionOverlay.FAILED)
    }

    /** Coba lagi: set yang sama diulang dari awal (rep 0, durasi penuh). Juga dipakai saat sheet ditutup tanpa memilih. */
    fun retry(): SessionState =
        if (overlay == SessionOverlay.FAILED) copy(overlay = SessionOverlay.NONE, phase = working(current)) else this

    /** Turun ke latihan regresi untuk sisa set; set yang sudah selesai tetap dihitung. Diabaikan bila tak ada regresi. */
    fun regress(): SessionState {
        val easier = regression
        if (overlay != SessionOverlay.FAILED || easier == null) return this
        val replacement = SessionExercise.regressed(easier, current.sets)
        return copy(
            exercises = exercises.toMutableList().also { it[index] = replacement },
            overlay = SessionOverlay.NONE,
            phase = working(replacement),
        )
    }

    /** Lewati latihan ini: sisa setnya tidak dihitung dan sesi lanjut ke latihan berikutnya tanpa istirahat. */
    fun skipExercise(): SessionState {
        if (overlay != SessionOverlay.FAILED) return this
        if (index == exercises.lastIndex) return finish(stopped = false)
        return copy(index = index + 1, overlay = SessionOverlay.NONE, phase = working(exercises[index + 1]))
    }

    fun addRest(): SessionState {
        val p = phase as? SessionPhase.Resting ?: return this
        if (paused || overlay != SessionOverlay.NONE) return this
        val extra = REST_EXTENSION_SECONDS * MILLIS_PER_SECOND
        return copy(phase = p.copy(remainingMillis = p.remainingMillis + extra, totalMillis = p.totalMillis + extra))
    }

    fun skipRest(): SessionState =
        if (phase is SessionPhase.Resting && !paused && overlay == SessionOverlay.NONE) afterRest() else this

    fun togglePause(): SessionState =
        if ((phase is SessionPhase.Working || phase is SessionPhase.Resting) && overlay == SessionOverlay.NONE) {
            copy(paused = !paused)
        } else {
            this
        }

    /** Tombol tutup atau Kembali: membuka dialog "Hentikan latihan?". */
    fun requestStop(): SessionState =
        if ((phase is SessionPhase.Working || phase is SessionPhase.Resting) && overlay == SessionOverlay.NONE) {
            copy(overlay = SessionOverlay.CONFIRM_STOP)
        } else {
            this
        }

    /** Lanjut di dialog. */
    fun dismissStop(): SessionState =
        if (overlay == SessionOverlay.CONFIRM_STOP) copy(overlay = SessionOverlay.NONE) else this

    fun confirmStop(): SessionState =
        if (overlay == SessionOverlay.CONFIRM_STOP) finish(stopped = true) else this

    /**
     * Ringkasan keadaan saat ini: COMPLETED bila semua set selesai, PARTIAL bila sebagian, selain itu ABANDONED.
     * Sama untuk berhenti di tengah maupun sesi yang berakhir dengan ada latihan yang dilewati.
     */
    fun summarize(): SessionSummary {
        val done = setsDone.sum()
        val total = exercises.sumOf { it.sets }
        val minutes = ((activeMillis + HALF_MINUTE_MILLIS) / MILLIS_PER_MINUTE).toInt()
        return SessionSummary(
            status = when {
                done >= total -> SessionStatus.COMPLETED
                done > 0 -> SessionStatus.PARTIAL
                else -> SessionStatus.ABANDONED
            },
            completedSets = done,
            totalSets = total,
            completedExercises = groupKeys.withIndex().groupBy({ it.value }, { it.index })
                .count { (_, steps) -> steps.all { setsDone[it] >= exercises[it].sets } },
            totalExercises = groupCount,
            minutes = if (done > 0) maxOf(1, minutes) else minutes,
        )
    }

    private fun finish(stopped: Boolean): SessionState =
        copy(phase = SessionPhase.Finished(summarize(), stopped), overlay = SessionOverlay.NONE, paused = false)

    /** Istirahat selesai atau dilewati: set berikutnya, atau latihan berikutnya bila set habis. */
    private fun afterRest(): SessionState {
        val nextIndex = if (setsDone[index] >= current.sets) index + 1 else index
        if (nextIndex > exercises.lastIndex) return finish(stopped = false)
        return copy(index = nextIndex, phase = working(exercises[nextIndex]))
    }

    companion object {
        const val REST_EXTENSION_SECONDS = 15
        private const val MILLIS_PER_SECOND = 1_000L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val HALF_MINUTE_MILLIS = 30_000L

        fun start(plan: SessionPlan): SessionState {
            require(plan.exercises.isNotEmpty()) { "Sesi butuh minimal satu latihan." }
            val first = plan.exercises.first()
            return SessionState(
                plan = plan,
                exercises = plan.exercises,
                setsDone = List(plan.exercises.size) { 0 },
                index = 0,
                phase = if (plan.requiresSafetyCheck) SessionPhase.SafetyCheck else working(first),
            )
        }

        /** Latihan durasi mulai dari durasi penuh; latihan repetisi dari nol. */
        private fun working(exercise: SessionExercise) = SessionPhase.Working(
            repsDone = 0,
            remainingMillis = if (exercise.isTimed) (exercise.durationSeconds ?: RoutineRules.SECONDS.first) * MILLIS_PER_SECOND else null,
        )
    }
}
