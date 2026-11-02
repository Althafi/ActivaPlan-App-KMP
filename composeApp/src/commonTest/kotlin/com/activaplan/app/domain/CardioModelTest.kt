package com.activaplan.app.domain

import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.i18n.Strings
import com.activaplan.app.domain.model.CardioBlock
import com.activaplan.app.domain.model.CardioSegment
import com.activaplan.app.domain.model.CardioType
import com.activaplan.app.domain.model.CardioWorkout
import com.activaplan.app.domain.model.Difficulty
import com.activaplan.app.domain.model.Motion
import com.activaplan.app.domain.model.SegmentKind
import com.activaplan.app.domain.validation.CardioCatalogValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardioModelTest {
    private fun seg(kind: SegmentKind, sec: Int, rpe: Int = 5) = CardioSegment(kind, Motion.JOG, sec, rpe)

    private fun workout(id: String = "w", blocks: List<CardioBlock>) = CardioWorkout(
        id = id, name = "n", type = CardioType.INTERVAL, difficulty = Difficulty.HARD, summary = "s",
        blocks = blocks, tips = emptyList(), commonMistakes = emptyList(),
    )

    @Test
    fun expandRepeatsBlocksInOrder() {
        val w = workout(
            blocks = listOf(
                CardioBlock(segments = listOf(seg(SegmentKind.WARMUP, 60))),
                CardioBlock(repeat = 3, segments = listOf(seg(SegmentKind.WORK, 30), seg(SegmentKind.RECOVERY, 20))),
                CardioBlock(segments = listOf(seg(SegmentKind.COOLDOWN, 60))),
            ),
        )
        val kinds = w.expand().map { it.kind }
        assertEquals(1 + 6 + 1, kinds.size)
        assertEquals(SegmentKind.WARMUP, kinds.first())
        assertEquals(SegmentKind.COOLDOWN, kinds.last())
        assertEquals(60 + 3 * 50 + 60, w.totalSeconds)
        assertEquals(3, w.workSegments)
    }

    @Test
    fun totalMinutesRoundsUp() {
        assertEquals(2, workout(blocks = listOf(CardioBlock(segments = listOf(seg(SegmentKind.STEADY, 61))))).totalMinutes)
        assertEquals(1, workout(blocks = listOf(CardioBlock(segments = listOf(seg(SegmentKind.STEADY, 5))))).totalMinutes)
    }

    @Test
    fun validatorFlagsProblems() {
        val bad = workout(
            id = "ne_x",
            blocks = listOf(CardioBlock(repeat = 0, segments = listOf(seg(SegmentKind.WARMUP, 0, rpe = 11)))),
        ).copy(progressionId = "missing")
        val messages = CardioCatalogValidator.validate(listOf(bad), strengthIds = setOf("ne_x")).map { it.message }
        assertTrue(messages.any { "bentrok" in it })
        assertTrue(messages.any { "repeat" in it })
        assertTrue(messages.any { "durationSeconds" in it })
        assertTrue(messages.any { "rpe" in it })
        assertTrue(messages.any { "WORK atau STEADY" in it })
        assertTrue(messages.any { "progressionId" in it })
    }

    @Test
    fun validatorFlagsDuplicateIds() {
        val ok = workout(blocks = listOf(CardioBlock(segments = listOf(seg(SegmentKind.STEADY, 60)))))
        assertTrue(CardioCatalogValidator.validate(listOf(ok), emptySet()).isEmpty())
        assertTrue(CardioCatalogValidator.validate(listOf(ok, ok), emptySet()).any { "duplikat" in it.message })
    }

    @Test
    fun durationAndLabelsFollowLanguage() {
        val id = Strings(Language.ID)
        val en = Strings(Language.EN)
        assertEquals("30 dtk", id.duration(30))
        assertEquals("5 mnt", id.duration(300))
        assertEquals("1 mnt 30 dtk", id.duration(90))
        assertEquals("1 min 30 sec", en.duration(90))
        assertEquals("Pemanasan", id.segmentName(CardioSegment(SegmentKind.WARMUP, Motion.JOG, 60, 3)))
        assertEquals("Lari cepat", id.segmentName(CardioSegment(SegmentKind.WORK, Motion.FAST, 30, 8)))
        assertEquals("Fast run", en.segmentName(CardioSegment(SegmentKind.WORK, Motion.FAST, 30, 8)))
    }

    @Test
    fun cardioMetaShowsSingleOrRangeEffort() {
        val steady = workout(blocks = listOf(CardioBlock(segments = listOf(seg(SegmentKind.STEADY, 1200, rpe = 5)))))
        assertEquals("20 menit \u00b7 usaha 5/10", Strings(Language.ID).cardioMeta(steady))
        val mixed = workout(
            blocks = listOf(CardioBlock(repeat = 2, segments = listOf(seg(SegmentKind.WORK, 60, 6), seg(SegmentKind.WORK, 60, 8)))),
        )
        assertEquals("4 min \u00b7 effort 6-8/10", Strings(Language.EN).cardioMeta(mixed))
    }
}
