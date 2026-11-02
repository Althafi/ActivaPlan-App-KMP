package com.activaplan.app.domain.i18n

import com.activaplan.app.domain.cardio.HeartRateZones
import com.activaplan.app.domain.cardio.PaceGuide
import com.activaplan.app.domain.model.AppLanguage
import com.activaplan.app.domain.model.BoardZone
import com.activaplan.app.domain.model.CardioSegment
import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.CardioType
import com.activaplan.app.domain.model.Category
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.Motion
import com.activaplan.app.domain.model.PlanDayType
import com.activaplan.app.domain.model.SegmentKind
import com.activaplan.app.domain.model.SessionStatus
import com.activaplan.app.domain.model.ThemeMode

internal fun applyArgs(text: String, vararg args: Any): String =
    args.foldIndexed(text) { i, acc, arg -> acc.replace("{$i}", arg.toString()) }

/** Akses teks UI untuk satu [language]; sepenuhnya offline. */
class Strings(val language: Language) {

    operator fun get(key: StringKey): String = key.text(language)

    /** Mengganti `{0}`, `{1}`, ... dengan [args] sesuai urutan. */
    fun format(key: StringKey, vararg args: Any): String = applyArgs(get(key), *args)

    fun label(value: Difficulty): String = get(
        when (value) {
            Difficulty.EASY -> StringKey.DIFFICULTY_EASY
            Difficulty.MEDIUM -> StringKey.DIFFICULTY_MEDIUM
            Difficulty.HARD -> StringKey.DIFFICULTY_HARD
        },
    )

    fun label(value: BoardZone): String = get(
        when (value) {
            BoardZone.BLUE -> StringKey.ZONE_BLUE
            BoardZone.RED -> StringKey.ZONE_RED
            BoardZone.YELLOW -> StringKey.ZONE_YELLOW
            BoardZone.GREEN -> StringKey.ZONE_GREEN
        },
    )

    /** Label chip zona, mis. "Biru · Dada". */
    fun zoneChip(value: BoardZone): String = get(
        when (value) {
            BoardZone.BLUE -> StringKey.ZONE_COLOR_BLUE
            BoardZone.RED -> StringKey.ZONE_COLOR_RED
            BoardZone.YELLOW -> StringKey.ZONE_COLOR_YELLOW
            BoardZone.GREEN -> StringKey.ZONE_COLOR_GREEN
        },
    ) + " \u00b7 " + label(value)

    fun label(value: Category): String = get(
        when (value) {
            Category.CHEST -> StringKey.CATEGORY_CHEST
            Category.ABS -> StringKey.CATEGORY_ABS
            Category.ARMS -> StringKey.CATEGORY_ARMS
            Category.SHOULDERS -> StringKey.CATEGORY_SHOULDERS
            Category.BACK -> StringKey.CATEGORY_BACK
            Category.LEGS -> StringKey.CATEGORY_LEGS
            Category.FULL_BODY -> StringKey.CATEGORY_FULL_BODY
        },
    )

    fun label(value: Mode): String = get(
        when (value) {
            Mode.NO_EQUIPMENT -> StringKey.MODE_NO_EQUIPMENT
            Mode.PUSHUP_BOARD -> StringKey.MODE_PUSHUP_BOARD
            Mode.BOARD_ROPE -> StringKey.MODE_BOARD_ROPE
        },
    )

    fun label(value: CardioType): String = get(
        when (value) {
            CardioType.WALK -> StringKey.CARDIO_TYPE_WALK
            CardioType.RUN_WALK -> StringKey.CARDIO_TYPE_RUN_WALK
            CardioType.EASY_RUN -> StringKey.CARDIO_TYPE_EASY_RUN
            CardioType.RECOVERY_RUN -> StringKey.CARDIO_TYPE_RECOVERY_RUN
            CardioType.LONG_RUN -> StringKey.CARDIO_TYPE_LONG_RUN
            CardioType.FARTLEK -> StringKey.CARDIO_TYPE_FARTLEK
            CardioType.TEMPO -> StringKey.CARDIO_TYPE_TEMPO
            CardioType.INTERVAL -> StringKey.CARDIO_TYPE_INTERVAL
            CardioType.HILLS -> StringKey.CARDIO_TYPE_HILLS
        },
    )

    fun label(value: PlanDayType): String = get(
        when (value) {
            PlanDayType.STRENGTH -> StringKey.PLAN_DAY_STRENGTH
            PlanDayType.CARDIO -> StringKey.PLAN_DAY_CARDIO
            PlanDayType.REST -> StringKey.PLAN_DAY_REST
        },
    )

    fun label(value: Motion): String = get(
        when (value) {
            Motion.WALK -> StringKey.MOTION_WALK
            Motion.JOG -> StringKey.MOTION_JOG
            Motion.RUN -> StringKey.MOTION_RUN
            Motion.FAST -> StringKey.MOTION_FAST
        },
    )

    fun label(value: SegmentKind): String = get(
        when (value) {
            SegmentKind.WARMUP -> StringKey.SEGMENT_WARMUP
            SegmentKind.WORK -> StringKey.SEGMENT_WORK
            SegmentKind.RECOVERY -> StringKey.SEGMENT_RECOVERY
            SegmentKind.COOLDOWN -> StringKey.SEGMENT_COOLDOWN
            SegmentKind.STEADY -> StringKey.SEGMENT_STEADY
        },
    )

    fun label(value: SessionStatus): String = get(
        when (value) {
            SessionStatus.COMPLETED -> StringKey.SESSION_COMPLETED
            SessionStatus.PARTIAL -> StringKey.SESSION_PARTIAL
            SessionStatus.ABANDONED -> StringKey.SESSION_ABANDONED
        },
    )

    fun label(value: ThemeMode): String = get(
        when (value) {
            ThemeMode.LIGHT -> StringKey.THEME_LIGHT
            ThemeMode.DARK -> StringKey.THEME_DARK
            ThemeMode.SYSTEM -> StringKey.THEME_SYSTEM
        },
    )

    fun label(value: AppLanguage): String = get(
        when (value) {
            AppLanguage.SYSTEM -> StringKey.LANGUAGE_SYSTEM
            AppLanguage.ID -> StringKey.LANGUAGE_ID
            AppLanguage.EN -> StringKey.LANGUAGE_EN
        },
    )

    /** Ringkasan kartu Jelajah; latihan durasi memakai "dtk", bukan "rep". */
    fun summary(exercise: Exercise): String = if (exercise.reps != null) {
        format(StringKey.EXPLORE_SUMMARY_REPS, exercise.sets, exercise.reps, exercise.restSeconds)
    } else {
        format(StringKey.EXPLORE_SUMMARY_SECONDS, exercise.sets, exercise.durationSeconds ?: 0, exercise.restSeconds)
    }

    /** "30 dtk", "5 mnt", atau "1 mnt 30 dtk". */
    fun duration(seconds: Int): String {
        val minutes = seconds / 60
        val rest = seconds % 60
        return when {
            minutes == 0 -> format(StringKey.DURATION_SEC, rest)
            rest == 0 -> format(StringKey.DURATION_MIN, minutes)
            else -> format(StringKey.DURATION_MIN_SEC, minutes, rest)
        }
    }

    /** Pemanasan dan pendinginan memakai nama perannya; segmen lain memakai nama gerakannya (Jalan, Lari cepat, ...). */
    fun segmentName(segment: CardioSegment): String = when (segment.kind) {
        SegmentKind.WARMUP, SegmentKind.COOLDOWN -> label(segment.kind)
        else -> label(segment.motion)
    }

    /** Ringkasan kartu kardio: "34 menit · usaha 6/10" atau rentang bila usaha segmen kerja berbeda. */
    fun cardioMeta(workout: CardioWorkout): String {
        val effort = workout.effortRange
        return if (effort.first == effort.last) {
            format(StringKey.CARDIO_META_SINGLE, workout.totalMinutes, effort.first)
        } else {
            format(StringKey.CARDIO_META_RANGE, workout.totalMinutes, effort.first, effort.last)
        }
    }

    /** "850 m" atau "1,25 km" (koma untuk Indonesia, titik untuk Inggris). */
    fun distance(meters: Int): String {
        if (meters < 1000) return "$meters m"
        val separator = if (language == Language.ID) "," else "."
        return "${meters / 1000}$separator${((meters % 1000) / 10).toString().padStart(2, '0')} km"
    }

    /** "m:ss" per km; "-" bila belum ada pace. */
    fun pace(secondsPerKm: Int?): String = secondsPerKm?.let(PaceGuide::format) ?: "\u2013"

    /** Baris zona detak jantung untuk [rpe]: bpm bila [age] diketahui, selain itu persen detak maksimal. */
    fun heartRateLine(rpe: Int, age: Int?): String {
        val zone = HeartRateZones.zone(rpe)
        return if (age != null) {
            val bpm = HeartRateZones.bpmRange(rpe, age)
            format(StringKey.CARDIO_HR_LINE_BPM, rpe, zone, bpm.first, bpm.last)
        } else {
            val percent = HeartRateZones.percentRange(rpe)
            format(StringKey.CARDIO_HR_LINE_PERCENT, rpe, zone, percent.first, percent.last)
        }
    }

    /** Satuan tile kerja di Detail: "rep" untuk latihan repetisi, "detik" untuk latihan durasi. */
    fun workUnit(exercise: Exercise): String =
        get(if (exercise.reps != null) StringKey.DETAIL_UNIT_REP else StringKey.DETAIL_UNIT_SECONDS)

    /** Nama hari tiga huruf ("Sen", "Min"); [dayOfWeek]: 1 = Senin .. 7 = Minggu. */
    fun dayShort(dayOfWeek: Int): String {
        require(dayOfWeek in 1..7) { "dayOfWeek harus 1..7, bukan $dayOfWeek" }
        return get(StringKey.entries.first { it.name == "DAY_SHORT_$dayOfWeek" })
    }

    /** [month]: 1 = Januari .. 12 = Desember. */
    fun monthName(month: Int): String {
        require(month in 1..12) { "month harus 1..12, bukan $month" }
        return get(StringKey.entries.first { it.name == "MONTH_$month" })
    }

    /** [dayOfWeek]: 1 = Senin .. 7 = Minggu. */
    fun dayName(dayOfWeek: Int): String {
        require(dayOfWeek in 1..7) { "dayOfWeek harus 1..7, bukan $dayOfWeek" }
        return get(StringKey.entries.first { it.name == "DAY_$dayOfWeek" })
    }
}
