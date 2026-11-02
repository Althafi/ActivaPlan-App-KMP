package com.activaplan.app.domain

import com.activaplan.app.domain.reminder.nextOccurrence
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Jadwal mingguan itu berulang per hari dalam pekan (Senin-Minggu), tanpa tanggal mulai atau akhir. Dipakai di pertengahan
 * pekan, hari yang tersisa jalan pekan ini dan hari yang sudah lewat mulai pekan depan; tidak ada "pekan 7 hari dari tanggal mulai".
 * 26 Oktober 2026 adalah hari Senin.
 */
class PlanCalendarTest {
    private val time = 16 * 60 + 30
    private fun at(month: Int, day: Int, hour: Int = 16, minute: Int = 30) = LocalDateTime(2026, month, day, hour, minute)

    private val planDays = listOf(1, 3, 5) // Senin, Rabu, Jumat

    private fun first(now: LocalDateTime) = planDays.minOf { nextOccurrence(it, time, now) }

    @Test
    fun startingBeforeTheSessionTimeTodayRunsTodayFirst() {
        assertEquals(at(10, 28), first(at(10, 28, 10, 0)))
    }

    @Test
    fun startingAfterTodaySessionTimeSkipsToTheNextPlannedDay() {
        assertEquals(at(10, 30), first(at(10, 28, 17, 0)))
    }

    @Test
    fun dayAlreadyPastThisWeekStartsNextWeekAndRepeatsWithoutEndDate() {
        val now = at(10, 28, 17, 0) // Rabu sore
        assertEquals(at(11, 2), nextOccurrence(1, time, now), "Senin sudah lewat: Senin depan")
        assertEquals(at(11, 4), nextOccurrence(3, time, now), "Rabu hari ini sudah lewat jamnya: Rabu depan")
        // Setelah sesi Jumat 30 Okt lewat, Jumat berikutnya tepat 7 hari kemudian, begitu seterusnya.
        assertEquals(at(11, 6), nextOccurrence(5, time, at(10, 30, 17, 0)))
        assertEquals(at(11, 13), nextOccurrence(5, time, at(11, 6, 17, 0)))
    }
}
