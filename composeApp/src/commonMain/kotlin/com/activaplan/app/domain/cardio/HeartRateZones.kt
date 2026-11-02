package com.activaplan.app.domain.cardio

import kotlin.math.roundToInt

/**
 * Perkiraan zona detak jantung dari skala usaha (RPE 1-10). Aplikasi tidak membaca sensor detak jantung, jadi
 * angka ini hanya panduan: RPE dan uji bicara tetap yang utama. Detak maksimal memakai rumus Tanaka (208 - 0,7 x usia),
 * yang bisa meleset ±10 bpm antarorang.
 */
object HeartRateZones {
    val AGE = 10..90

    /** Usia yang ditampilkan di stepper Setelan saat belum diisi. */
    const val DEFAULT_AGE = 30

    /** Di atas ini (persen detak maksimal) tubuh sebaiknya turun tempo; dipakai sebagai batas waspada. */
    const val CAUTION_PERCENT = 90

    fun maxHeartRate(age: Int): Int = (208 - 0.7 * age.coerceIn(AGE)).roundToInt()

    /** Persen detak maksimal untuk [rpe]: RPE 1 = 50-55%, tiap tingkat naik 5 poin, RPE 10 = 95-100%. */
    fun percentRange(rpe: Int): IntRange {
        val low = 50 + (rpe.coerceIn(1, 10) - 1) * 5
        return low..(low + 5)
    }

    /** Zona 1-5 (dua tingkat RPE per zona): 1-2 = Z1 ... 9-10 = Z5. */
    fun zone(rpe: Int): Int = (rpe.coerceIn(1, 10) + 1) / 2

    /** Rentang bpm perkiraan untuk [rpe] pada [age]. */
    fun bpmRange(rpe: Int, age: Int): IntRange {
        val max = maxHeartRate(age)
        val percent = percentRange(rpe)
        return (max * percent.first / 100.0).roundToInt()..(max * percent.last / 100.0).roundToInt()
    }

    /** Batas waspada dalam bpm ([CAUTION_PERCENT] dari detak maksimal). */
    fun cautionBpm(age: Int): Int = (maxHeartRate(age) * CAUTION_PERCENT / 100.0).roundToInt()
}
