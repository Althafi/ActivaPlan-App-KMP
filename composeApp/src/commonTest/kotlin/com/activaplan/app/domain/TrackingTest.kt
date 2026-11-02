package com.activaplan.app.domain

import com.activaplan.app.domain.cardio.HeartRateZones
import com.activaplan.app.domain.cardio.PaceGuide
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.i18n.Strings
import com.activaplan.app.domain.model.Motion
import com.activaplan.app.domain.tracking.TrackAccumulator
import com.activaplan.app.domain.tracking.TrackPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackingTest {
    /** 1 derajat bujur di khatulistiwa. */
    private val metersPerDegree = 111_194.93

    /** Titik di khatulistiwa [metersEast] meter dari bujur 0, pada detik [second]. */
    private fun pt(second: Int, metersEast: Double, accuracy: Float? = 5f) =
        TrackPoint(0.0, metersEast / metersPerDegree, second * 1000L, accuracy)

    private fun run(acc: TrackAccumulator = TrackAccumulator(), seconds: IntRange, speed: Double): TrackAccumulator =
        seconds.fold(acc) { a, s -> a.add(pt(s, s * speed)) }

    @Test
    fun steadyRunAccumulatesDistanceAndPace() {
        val acc = run(seconds = 0..30, speed = 3.0)
        assertTrue(abs(acc.stats.distanceMeters - 90.0) < 2.0, "jarak ${acc.stats.distanceMeters}")
        assertEquals(333, acc.stats.currentPaceSecPerKm)
        assertEquals(333, acc.stats.averagePaceSecPerKm)
        assertEquals(30_000L, acc.stats.movingMillis)
    }

    @Test
    fun standingJitterAddsNoDistance() {
        var acc = TrackAccumulator()
        for (s in 0..60) acc = acc.add(pt(s, if (s % 2 == 0) 0.0 else 1.5))
        assertEquals(0.0, acc.stats.distanceMeters)
        assertNull(acc.stats.averagePaceSecPerKm)
    }

    @Test
    fun slowWalkIsStillCounted() {
        val acc = run(seconds = 0..60, speed = 1.4)
        assertTrue(abs(acc.stats.distanceMeters - 84.0) < 6.0, "jarak ${acc.stats.distanceMeters}")
    }

    @Test
    fun gpsSpikeIsIgnoredAndTrackContinues() {
        var acc = run(seconds = 0..9, speed = 3.0)
        acc = acc.add(pt(10, 10 * 3.0 + 500.0))
        for (s in 11..20) acc = acc.add(pt(s, s * 3.0))
        // Titik acuan terakhir ada di detik 19 (57 m); loncatan 500 m tidak ikut terhitung.
        assertTrue(acc.stats.distanceMeters in 54.0..60.0, "jarak ${acc.stats.distanceMeters}")
    }

    @Test
    fun inaccuratePointsAreDropped() {
        var acc = TrackAccumulator().add(pt(0, 0.0))
        acc = acc.add(pt(1, 50.0, accuracy = 80f))
        acc = acc.add(pt(2, 6.0))
        assertTrue(abs(acc.stats.distanceMeters - 6.0) < 0.5)
    }

    @Test
    fun longGapReanchorsWithoutAddingDistance() {
        val acc = TrackAccumulator().add(pt(0, 0.0)).add(pt(60, 1000.0))
        assertEquals(0.0, acc.stats.distanceMeters)
    }

    @Test
    fun detachKeepsDistanceButDropsPaceAndAnchor() {
        val moving = run(seconds = 0..20, speed = 3.0)
        val detached = moving.detach()
        assertEquals(moving.stats.distanceMeters, detached.stats.distanceMeters)
        assertNull(detached.stats.currentPaceSecPerKm)
        // Titik pertama setelah lepas hanya menjadi jangkar, jadi lompatan selama jeda tidak dihitung.
        val resumed = detached.add(pt(500, 5_000.0))
        assertEquals(moving.stats.distanceMeters, resumed.stats.distanceMeters)
    }

    @Test
    fun haversineMatchesKnownDistance() {
        val a = TrackPoint(0.0, 0.0, 0)
        val b = TrackPoint(1.0, 0.0, 1000)
        assertTrue(abs(TrackAccumulator.haversineMeters(a, b) - 111_195.0) < 100.0)
    }

    @Test
    fun heartRateZonesFollowRpeAndAge() {
        assertEquals(187, HeartRateZones.maxHeartRate(30))
        assertEquals(50..55, HeartRateZones.percentRange(1))
        assertEquals(80..85, HeartRateZones.percentRange(7))
        assertEquals(95..100, HeartRateZones.percentRange(10))
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5), (1..10).map { HeartRateZones.zone(it) })
        // 187 x 80% = 149,6 dan x 85% = 158,95.
        assertEquals(150..159, HeartRateZones.bpmRange(7, 30))
        assertEquals(168, HeartRateZones.cautionBpm(30))
    }

    @Test
    fun paceGuideAndFormatting() {
        assertEquals("6:15", PaceGuide.format(375))
        assertEquals("5:05", PaceGuide.format(305))
        val ends = listOf(Motion.WALK, Motion.JOG, Motion.RUN, Motion.FAST).map { PaceGuide.range(it).last }
        assertEquals(ends.sortedDescending(), ends)
        val id = Strings(Language.ID)
        val en = Strings(Language.EN)
        assertEquals("850 m", id.distance(850))
        assertEquals("1,25 km", id.distance(1250))
        assertEquals("1.25 km", en.distance(1250))
        assertEquals("6:15", id.pace(375))
        assertEquals("–", id.pace(null))
        assertEquals("Usaha 7 · zona 4 · ≈150-159 bpm", id.heartRateLine(7, 30))
        assertEquals("Effort 7 · zone 4 · 80-85% max HR", en.heartRateLine(7, null))
        assertNotNull(id.heartRateLine(1, 90))
    }
}
