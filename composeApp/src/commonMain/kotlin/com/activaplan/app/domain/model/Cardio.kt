package com.activaplan.app.domain.model

import kotlinx.serialization.Serializable

/** Dua jenis latihan di aplikasi; dipakai switch Jelajah dan penanda log. */
enum class ActivityKind { STRENGTH, CARDIO }

/** Jenis sesi kardio; menentukan label dan ikon, bukan perilaku timer. */
enum class CardioType { WALK, RUN_WALK, EASY_RUN, RECOVERY_RUN, LONG_RUN, FARTLEK, TEMPO, INTERVAL, HILLS }

/** Peran segmen dalam sesi. [STEADY] = satu segmen tetap tanpa pemanasan/pemulihan (mis. Jalan Cepat). */
enum class SegmentKind { WARMUP, WORK, RECOVERY, COOLDOWN, STEADY }

/** Gerakan yang dilakukan di segmen; teksnya ada di `Strings.label(Motion)`. */
enum class Motion { WALK, JOG, RUN, FAST }

/** Satu segmen: [rpe] 1-10 (skala usaha yang dirasakan), bukan target detak jantung. */
@Serializable
data class CardioSegment(
    val kind: SegmentKind,
    val motion: Motion,
    val durationSeconds: Int,
    val rpe: Int,
)

/** Kumpulan segmen yang diulang [repeat] kali, mis. 8 x (30 dtk cepat, 30 dtk pelan). */
@Serializable
data class CardioBlock(
    val repeat: Int = 1,
    val segments: List<CardioSegment>,
)

@Serializable
data class CardioWorkout(
    val id: String,
    val name: String,
    val type: CardioType,
    val difficulty: Difficulty,
    val summary: String,
    val blocks: List<CardioBlock>,
    val tips: List<String>,
    val commonMistakes: List<String>,
    val progressionId: String? = null,
    val regressionId: String? = null,
) {
    /** Semua segmen berurutan dengan pengulangan sudah dibuka. */
    fun expand(): List<CardioSegment> = blocks.flatMap { b -> List(b.repeat) { b.segments }.flatten() }

    val totalSeconds: Int get() = expand().sumOf { it.durationSeconds }

    /** Estimasi menit, dibulatkan ke atas, minimal 1. */
    val totalMinutes: Int get() = ((totalSeconds + 59) / 60).coerceAtLeast(1)

    /** Rentang usaha (RPE) segmen kerja; untuk ringkasan kartu. */
    val effortRange: IntRange
        get() {
            val rpe = expand().filter { it.kind == SegmentKind.WORK || it.kind == SegmentKind.STEADY }.map { it.rpe }
            return (rpe.minOrNull() ?: 0)..(rpe.maxOrNull() ?: 0)
        }

    /** Jumlah segmen "kerja" (WORK atau STEADY); dipakai sebagai "set" untuk log dan streak. */
    val workSegments: Int get() = expand().count { it.kind == SegmentKind.WORK || it.kind == SegmentKind.STEADY }
}

/** Batas data kardio, dipakai validator. */
object CardioRules {
    val RPE = 1..10
    val REPEAT = 1..30
    const val MAX_SEGMENT_SECONDS = 7200
}
