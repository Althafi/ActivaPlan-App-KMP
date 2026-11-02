package com.activaplan.app.domain.cardio

import com.activaplan.app.domain.model.Motion

/**
 * Rentang pace acuan (detik per km) untuk pemula sampai menengah, per jenis gerakan. Ini angka umum, bukan target
 * pribadi: pace sangat bergantung pada tinggi badan, kebugaran, cuaca, dan tanjakan, jadi usaha (RPE) mengalahkan pace.
 */
object PaceGuide {
    fun range(motion: Motion): IntRange = when (motion) {
        Motion.WALK -> 540..720
        Motion.JOG -> 420..600
        Motion.RUN -> 360..450
        Motion.FAST -> 300..390
    }

    /** "m:ss", mis. 375 -> "6:15". */
    fun format(secondsPerKm: Int): String {
        val total = secondsPerKm.coerceAtLeast(0)
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }
}
