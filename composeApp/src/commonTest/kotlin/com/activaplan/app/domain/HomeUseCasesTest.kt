package com.activaplan.app.domain

import com.activaplan.app.domain.model.Category
import com.activaplan.app.domain.model.BoardZone
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.Exercise
import com.activaplan.app.domain.model.GreetingPeriod
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.Routine
import com.activaplan.app.domain.model.RoutineItem
import com.activaplan.app.domain.model.ScheduleEntry
import com.activaplan.app.domain.model.SessionLog
import com.activaplan.app.domain.model.SessionStatus
import com.activaplan.app.domain.model.Template
import com.activaplan.app.domain.time.formatClock
import com.activaplan.app.domain.usecase.CalculateWeekStreakUseCase
import com.activaplan.app.domain.usecase.EstimateRoutineMinutesUseCase
import com.activaplan.app.domain.usecase.GetTodayWorkoutUseCase
import com.activaplan.app.domain.usecase.RecommendTemplatesUseCase
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MON = 1
private const val WED = 3
private const val FRI = 5

/** Oktober 2026: Senin 26, Selasa 27, Rabu 28, Kamis 29, Jumat 30, Sabtu 31, Minggu 1 November. */
private fun oct(day: Int) = LocalDate(2026, 10, day)

private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime(2026, 10, day, hour, minute)

private fun exercise(id: String, difficulty: Difficulty = Difficulty.EASY, mode: Mode = Mode.NO_EQUIPMENT) = Exercise(
    id = id, name = id, mode = mode, category = Category.CHEST, difficulty = difficulty,
    boardZone = if (mode == Mode.PUSHUP_BOARD) BoardZone.BLUE else null,
    primaryMuscles = listOf("chest"), sets = 3, reps = 10, restSeconds = 30,
    steps = emptyList(), tips = emptyList(), commonMistakes = emptyList(),
)

private fun routine(id: String, vararg exerciseIds: String) =
    Routine(id, "Rutinitas $id", exerciseIds.map { RoutineItem(it, sets = 3, reps = 10, restSeconds = 30) })

private fun entry(id: String, day: Int, time: Int, routineId: String) =
    ScheduleEntry(id, dayOfWeek = day, timeMinutes = time, routineId = routineId, reminder = true)

private fun log(date: LocalDate, routineId: String = "r1", status: SessionStatus = SessionStatus.COMPLETED) =
    SessionLog(date.toString(), routineId, status, completedSets = 3, totalSets = 3)

class EstimateRoutineMinutesUseCaseTest {
    private val estimate = EstimateRoutineMinutesUseCase()

    @Test
    fun repExerciseUsesFourSecondsPerRep() {
        // 3 set x (10 rep x 4 dtk + 30 dtk istirahat) = 210 dtk = 3,5 menit, dibulatkan 4.
        assertEquals(4, estimate(Routine("r", "R", listOf(RoutineItem("a", 3, reps = 10, restSeconds = 30)))))
    }

    @Test
    fun durationExerciseUsesItsDuration() {
        // 2 set x (30 + 15) = 90 dtk = 1,5 menit, dibulatkan 2.
        assertEquals(2, estimate(Routine("r", "R", listOf(RoutineItem("a", 2, durationSeconds = 30, restSeconds = 15)))))
    }

    @Test
    fun sumsAllItemsAndHandlesEmpty() {
        val items = List(2) { RoutineItem("a", 3, reps = 10, restSeconds = 30) }
        assertEquals(7, estimate(Routine("r", "R", items)))
        assertEquals(0, estimate(Routine("r", "R", emptyList())))
    }

    @Test
    fun neverRoundsAPositiveEstimateDownToZero() {
        assertEquals(1, estimate(Routine("r", "R", listOf(RoutineItem("a", 1, durationSeconds = 5, restSeconds = 15)))))
    }
}

class GetTodayWorkoutUseCaseTest {
    private val getToday = GetTodayWorkoutUseCase()
    private val routines = listOf(routine("r1", "a", "b"), routine("r2", "a"), routine("r3", "a", "b", "c"))
    private val modes = mapOf("a" to Mode.NO_EQUIPMENT, "b" to Mode.PUSHUP_BOARD, "c" to Mode.BOARD_ROPE)
    private val monday = 26

    @Test
    fun noScheduleGivesNull() {
        assertNull(getToday(emptyList(), routines, modes, at(monday, 10)))
    }

    @Test
    fun scheduleOnOtherDaysOnlyGivesNull() {
        assertNull(getToday(listOf(entry("s1", WED, 600, "r1")), routines, modes, at(monday, 10)))
    }

    @Test
    fun oneUpcomingEntryFillsTheCard() {
        val today = getToday(listOf(entry("s1", MON, 990, "r1")), routines, modes, at(monday, 10))!!

        assertEquals("r1", today.routineId)
        assertEquals("Rutinitas r1", today.routineName)
        assertEquals(990, today.timeMinutes)
        assertEquals(2, today.exerciseCount)
        assertEquals(7, today.estimatedMinutes)
        assertFalse(today.passed)
        assertFalse(today.requiresSafetyCheck)
    }

    @Test
    fun manyEntriesPicksTheNextOneNotYetPassed() {
        val schedule = listOf(entry("s3", MON, 18 * 60, "r3"), entry("s1", MON, 6 * 60 + 30, "r1"), entry("s2", MON, 12 * 60, "r2"))

        val today = getToday(schedule, routines, modes, at(monday, 10))!!

        assertEquals("r2", today.routineId)
        assertFalse(today.passed)
    }

    @Test
    fun entryAtExactlyNowIsNotPassedYet() {
        val schedule = listOf(entry("s1", MON, 6 * 60, "r1"), entry("s2", MON, 10 * 60 + 15, "r2"))

        val today = getToday(schedule, routines, modes, at(monday, 10, 15))!!

        assertEquals("r2", today.routineId)
        assertFalse(today.passed)
    }

    @Test
    fun allPassedShowsTheLastOneMarkedPassed() {
        val schedule = listOf(entry("s2", MON, 12 * 60, "r2"), entry("s1", MON, 6 * 60 + 30, "r1"))

        val today = getToday(schedule, routines, modes, at(monday, 20))!!

        assertEquals("r2", today.routineId)
        assertTrue(today.passed)
    }

    @Test
    fun ropeExerciseRequiresSafetyCheck() {
        val today = getToday(listOf(entry("s1", MON, 600, "r3")), routines, modes, at(monday, 8))!!
        assertTrue(today.requiresSafetyCheck)
    }

    @Test
    fun entriesWithMissingOrEmptyRoutineAreSkipped() {
        val schedule = listOf(entry("s1", MON, 9 * 60, "hilang"), entry("s2", MON, 10 * 60, "kosong"), entry("s3", MON, 11 * 60, "r2"))
        val withEmpty = routines + Routine("kosong", "Kosong", emptyList())

        assertEquals("r2", getToday(schedule, withEmpty, modes, at(monday, 8))!!.routineId)
        assertNull(getToday(schedule.take(2), withEmpty, modes, at(monday, 8)))
    }

    @Test
    fun passedTimeAloneNeverLocksTodaysWorkout() {
        val today = getToday(listOf(entry("s1", MON, 21 * 60, "r1")), routines, modes, at(monday, 22))!!

        assertTrue(today.passed)
        assertFalse(today.locked)
        assertEquals(0, today.daysAhead)
    }

    @Test
    fun completedTodayShowsTheNextScheduledDayLocked() {
        val schedule = listOf(entry("s1", MON, 600, "r1"), entry("s2", WED, 600, "r2"))

        val today = getToday(schedule, routines, modes, at(monday, 20), listOf(log(oct(26), "r1")))!!

        assertEquals("r2", today.routineId)
        assertEquals(WED, today.dayOfWeek)
        assertEquals(2, today.daysAhead)
        assertTrue(today.locked)
        assertFalse(today.passed)
    }

    @Test
    fun completedOnlyScheduleWrapsToTheSameWeekdayNextWeek() {
        val today = getToday(listOf(entry("s1", MON, 600, "r1")), routines, modes, at(monday, 20), listOf(log(oct(26), "r1")))!!

        assertEquals("r1", today.routineId)
        assertEquals(MON, today.dayOfWeek)
        assertEquals(7, today.daysAhead)
        assertTrue(today.locked)
    }

    @Test
    fun nextScheduledDayWrapsAcrossTheWeekend() {
        // Jumat selesai; jadwal lain hanya Senin, jadi tiga hari lagi.
        val schedule = listOf(entry("s1", FRI, 600, "r1"), entry("s2", MON, 600, "r2"))

        val today = getToday(schedule, routines, modes, at(30, 20), listOf(log(oct(30), "r1")))!!

        assertEquals("r2", today.routineId)
        assertEquals(3, today.daysAhead)
    }

    @Test
    fun partialAndAbandonedSessionsDoNotCompleteTheSchedule() {
        val logs = listOf(log(oct(26), "r1", SessionStatus.PARTIAL), log(oct(26), "r1", SessionStatus.ABANDONED))

        val today = getToday(listOf(entry("s1", MON, 600, "r1")), routines, modes, at(monday, 20), logs)!!

        assertFalse(today.locked)
    }

    @Test
    fun logsFromOtherDaysOrRoutinesDoNotCount() {
        val logs = listOf(log(oct(19), "r1"), log(oct(26), "r2"))

        val today = getToday(listOf(entry("s1", MON, 600, "r1")), routines, modes, at(monday, 20), logs)!!

        assertFalse(today.locked)
        assertEquals("r1", today.routineId)
    }

    @Test
    fun finishedEntryIsSkippedAndTheRemainingOneStaysStartable() {
        val schedule = listOf(entry("s1", MON, 6 * 60, "r1"), entry("s2", MON, 18 * 60, "r2"))

        val today = getToday(schedule, routines, modes, at(monday, 10), listOf(log(oct(26), "r1")))!!

        assertEquals("r2", today.routineId)
        assertFalse(today.locked)
    }

    @Test
    fun missedMorningEntryStaysStartableAfterItsTime() {
        val schedule = listOf(entry("s1", MON, 6 * 60, "r1"), entry("s2", MON, 18 * 60, "r2"))

        // Pagi terlewat, malam sudah selesai: yang tersisa hanya pagi, tetap bisa dimulai walau jamnya lewat.
        val today = getToday(schedule, routines, modes, at(monday, 20), listOf(log(oct(26), "r2")))!!

        assertEquals("r1", today.routineId)
        assertTrue(today.passed)
        assertFalse(today.locked)
    }

    @Test
    fun sameRoutineScheduledTwiceNeedsTwoCompletions() {
        val schedule = listOf(entry("s1", MON, 6 * 60, "r1"), entry("s2", MON, 18 * 60, "r1"))
        val one = listOf(log(oct(26), "r1"))

        val afterOne = getToday(schedule, routines, modes, at(monday, 10), one)!!
        val afterTwo = getToday(schedule, routines, modes, at(monday, 10), one + one)!!

        assertFalse(afterOne.locked)
        assertEquals(18 * 60, afterOne.timeMinutes)
        assertTrue(afterTwo.locked)
    }
}

class CalculateWeekStreakUseCaseTest {
    private val calculate = CalculateWeekStreakUseCase()
    private val monWedFri = listOf(entry("s1", MON, 600, "r1"), entry("s2", WED, 600, "r1"), entry("s3", FRI, 600, "r1"))

    @Test
    fun weekIsMondayToSundayWithOnlyTodayMarked() {
        val week = calculate(emptyList(), emptyList(), oct(29)).days

        assertEquals((1..7).toList(), week.map { it.dayOfWeek })
        assertEquals(listOf(4), week.filter { it.isToday }.map { it.dayOfWeek })
        assertTrue(week.none { it.completed })
    }

    @Test
    fun noScheduleMeansZeroStreakEvenWithLogs() {
        assertEquals(0, calculate(emptyList(), listOf(log(oct(26)), log(oct(27))), oct(27)).streak)
    }

    @Test
    fun missedScheduledDayBreaksTheStreak() {
        val logs = listOf(log(oct(26)), log(oct(30)))

        val result = calculate(monWedFri, logs, oct(30))

        assertEquals(1, result.streak)
        assertEquals(listOf(true, false, false, false, true, false, false), result.days.map { it.completed })
    }

    @Test
    fun daysWithoutScheduleDoNotBreakTheStreak() {
        val logs = listOf(log(oct(26)), log(oct(28)), log(oct(30)))

        assertEquals(3, calculate(monWedFri, logs, oct(30)).streak)
    }

    @Test
    fun todayNotDoneYetDoesNotBreakTheStreak() {
        val result = calculate(monWedFri, listOf(log(oct(26)), log(oct(28))), oct(30))

        assertEquals(2, result.streak)
        assertFalse(result.days.single { it.isToday }.completed)
    }

    @Test
    fun streakContinuesAcrossTheWeekBoundary() {
        val schedule = listOf(entry("s1", MON, 600, "r1"), entry("s2", FRI, 600, "r1"))
        val logs = listOf(log(oct(23)), log(oct(26)))

        // Selasa 27 Okt: Senin 26 dan Jumat 23 (pekan lalu) selesai; Senin 19 tidak ada, jadi berhenti.
        val tuesday = calculate(schedule, logs, oct(27))
        assertEquals(2, tuesday.streak)
        assertEquals(listOf(true, false, false, false, false, false, false), tuesday.days.map { it.completed })

        // Senin 2 Nov (pekan baru): lingkaran kosong, streak dari Jumat 30 Okt tetap hidup.
        val newWeek = calculate(schedule, logs + log(LocalDate(2026, 10, 30)), LocalDate(2026, 11, 2))
        assertEquals(3, newWeek.streak)
        assertTrue(newWeek.days.none { it.completed })
        assertEquals(1, newWeek.days.single { it.isToday }.dayOfWeek)
    }

    @Test
    fun onlyCompletedSessionsCount() {
        val logs = listOf(
            log(oct(26), status = SessionStatus.PARTIAL),
            log(oct(28), status = SessionStatus.ABANDONED),
            log(oct(30)),
        )

        val result = calculate(monWedFri, logs, oct(30))

        assertEquals(1, result.streak)
        assertEquals(listOf(5), result.days.filter { it.completed }.map { it.dayOfWeek })
    }

    @Test
    fun completedTodayCountsAndStillMarksToday() {
        val result = calculate(monWedFri, listOf(log(oct(30))), oct(30))

        assertEquals(1, result.streak)
        assertTrue(result.days.single { it.isToday }.completed)
    }
}

class RecommendTemplatesUseCaseTest {
    private val recommend = RecommendTemplatesUseCase()
    private val exercises = listOf(
        exercise("e", Difficulty.EASY),
        exercise("m", Difficulty.MEDIUM, Mode.PUSHUP_BOARD),
        exercise("h", Difficulty.HARD, Mode.BOARD_ROPE),
    ).associateBy { it.id }
    private val templates = listOf(
        Template("t_e1", "Mudah 1", Difficulty.EASY, listOf("e", "e")),
        Template("t_e2", "Mudah 2", Difficulty.EASY, listOf("e")),
        Template("t_m1", "Sedang 1", Difficulty.MEDIUM, listOf("m", "m", "h")),
        Template("t_m2", "Sedang 2", Difficulty.MEDIUM, listOf("m")),
        Template("t_h1", "Sulit 1", Difficulty.HARD, listOf("h", "h")),
    )
    private val routines = listOf(routine("easy", "e"), routine("medium", "e", "m"), routine("hard", "m", "h"))

    private fun ids(logs: List<SessionLog>, routines: List<Routine> = this.routines) =
        recommend(templates, routines, logs, exercises).map { it.id }

    @Test
    fun withoutHistoryDefaultsToEasyAndOneStepUp() {
        assertEquals(listOf("t_e1", "t_m1"), ids(emptyList()))
    }

    @Test
    fun usesHighestExerciseLevelOfTheLastCompletedRoutine() {
        assertEquals(listOf("t_m1", "t_h1"), ids(listOf(log(oct(25), "medium"))))
    }

    @Test
    fun topLevelOnlyOffersWhatExists() {
        assertEquals(listOf("t_h1"), ids(listOf(log(oct(25), "hard"))))
    }

    @Test
    fun latestCompletedLogWinsAndOtherStatusesAreIgnored() {
        val logs = listOf(
            log(oct(25), "medium"),
            log(oct(24), "hard"),
            log(oct(26), "hard", SessionStatus.PARTIAL),
        )

        assertEquals(listOf("t_m1", "t_h1"), ids(logs))
    }

    @Test
    fun sameDateUsesTheLaterLog() {
        assertEquals(listOf("t_m1", "t_h1"), ids(listOf(log(oct(25), "easy"), log(oct(25), "medium"))))
    }

    @Test
    fun deletedRoutineFallsBackToEarlierCompletedOne() {
        val logs = listOf(log(oct(24), "medium"), log(oct(25), "dihapus"))

        assertEquals(listOf("t_m1", "t_h1"), ids(logs))
    }

    @Test
    fun cardCarriesCountAndMostFrequentMode() {
        val card = recommend(templates, routines, emptyList(), exercises).single { it.id == "t_m1" }

        assertEquals(3, card.exerciseCount)
        assertEquals(Difficulty.MEDIUM, card.difficulty)
        assertEquals(Mode.PUSHUP_BOARD, card.primaryMode)
        assertEquals("Sedang 1", card.name)
    }

    @Test
    fun noTemplatesGivesNoRecommendations() {
        assertEquals(emptyList(), recommend(emptyList(), routines, emptyList(), exercises))
    }
}

class HomeFormattingTest {
    @Test
    fun greetingFollowsTheHour() {
        assertEquals(GreetingPeriod.EVENING, GreetingPeriod.at(3))
        assertEquals(GreetingPeriod.MORNING, GreetingPeriod.at(4))
        assertEquals(GreetingPeriod.MORNING, GreetingPeriod.at(10))
        assertEquals(GreetingPeriod.NOON, GreetingPeriod.at(11))
        assertEquals(GreetingPeriod.AFTERNOON, GreetingPeriod.at(16))
        assertEquals(GreetingPeriod.EVENING, GreetingPeriod.at(18))
        assertEquals(GreetingPeriod.EVENING, GreetingPeriod.at(0))
    }

    @Test
    fun clockUsesDotAndTwoDigits() {
        assertEquals("16.30", formatClock(990))
        assertEquals("06.05", formatClock(6 * 60 + 5))
        assertEquals("00.00", formatClock(0))
    }
}

class RecommendTemplatesOnboardingTest {
    private val recommend = RecommendTemplatesUseCase()
    private val exercises = listOf(
        exercise("e", Difficulty.EASY),
        exercise("m", Difficulty.MEDIUM, Mode.PUSHUP_BOARD),
        exercise("h", Difficulty.HARD, Mode.BOARD_ROPE),
    ).associateBy { it.id }
    private val templates = listOf(
        Template("t_e1", "Mudah 1", Difficulty.EASY, listOf("e", "e")),
        Template("t_e2", "Mudah 2", Difficulty.EASY, listOf("e")),
        Template("t_m1", "Sedang 1", Difficulty.MEDIUM, listOf("m", "m", "h")),
        Template("t_m2", "Sedang 2", Difficulty.MEDIUM, listOf("m")),
        Template("t_h1", "Sulit 1", Difficulty.HARD, listOf("h", "h")),
    )
    private val routines = listOf(routine("medium", "e", "m"))

    private fun ids(
        startLevel: Difficulty = Difficulty.EASY,
        ownedModes: Set<Mode> = Mode.entries.toSet(),
        logs: List<SessionLog> = emptyList(),
    ) = recommend(templates, routines, logs, exercises, startLevel, ownedModes).map { it.id }

    @Test
    fun startLevelReplacesEasyDefaultWhenNoSessionIsCompleted() {
        assertEquals(listOf("t_m1", "t_h1"), ids(startLevel = Difficulty.MEDIUM))
    }

    @Test
    fun aCompletedSessionOverridesStartLevel() {
        assertEquals(listOf("t_m1", "t_h1"), ids(startLevel = Difficulty.HARD, logs = listOf(log(oct(25), "medium"))))
    }

    @Test
    fun onlyPartialSessionsStillUseStartLevel() {
        val partial = log(oct(25), "medium", SessionStatus.PARTIAL)

        assertEquals(listOf("t_m1", "t_h1"), ids(startLevel = Difficulty.MEDIUM, logs = listOf(partial)))
    }

    @Test
    fun hidesTemplatesWhosePrimaryModeIsNotOwned() {
        // Pemilik Tanpa Alat saja tidak melihat template board (Sedang/Sulit), hanya yang Mudah.
        assertEquals(listOf("t_e1", "t_e2"), ids(ownedModes = setOf(Mode.NO_EQUIPMENT)))
    }

    @Test
    fun boardOwnerKeepsBoardTemplateEvenWithAFewRopeExercises() {
        // t_m1 berisi satu latihan tali tetapi mode utamanya Push-up Board.
        assertEquals(listOf("t_m1", "t_m2"), ids(ownedModes = setOf(Mode.PUSHUP_BOARD)))
    }

    @Test
    fun ownerOfNothingMatchingGetsNoRecommendation() {
        assertEquals(emptyList(), ids(startLevel = Difficulty.HARD, ownedModes = setOf(Mode.NO_EQUIPMENT)))
    }
}
