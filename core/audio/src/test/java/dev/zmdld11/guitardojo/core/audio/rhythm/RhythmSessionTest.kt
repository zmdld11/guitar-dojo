package dev.zmdld11.guitardojo.core.audio.rhythm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 对局内核：命中推进、连击 miss、结束判定。 */
class RhythmSessionTest {
    private val sampleRate = 48_000

    // fps = 48000（60 BPM）；tiles = [48000, 96000, 144000, 192000]，totalFrames = 240000
    private fun session(): RhythmSession = RhythmSession(RhythmTimeline(sampleRate, 60.0, 1.0, List(4) { 1.0 }, 1), sampleRate)

    @Test
    fun `完美命中推进并计数`() {
        val game = session()
        val verdict = game.hit(headFrame = 48_000L)!!
        assertEquals(HitGrade.PERFECT, verdict.grade)
        assertEquals(1, game.nextTileIndex)
        val stats = game.stats()
        assertEquals(1, stats.perfectCount)
        assertEquals(5, stats.totalTiles)
        assertEquals(0.2, stats.accuracy, 1e-9)
    }

    @Test
    fun `点空记 miss 但不推进`() {
        val game = session()
        val verdict = game.hit(headFrame = 48_000L - 10_000L)!! // 早 208ms，超 GOOD 窗口
        assertEquals(HitGrade.MISS, verdict.grade)
        assertEquals(0, game.nextTileIndex)
        assertEquals(1, game.stats().missCount)
    }

    @Test
    fun `连续点空三次失败`() {
        val game = session()
        repeat(3) {
            assertEquals(HitGrade.MISS, game.hit(0L)!!.grade)
        }
        assertTrue(game.failed)
        assertNull(game.hit(48_000L))
        assertTrue(game.drainEvents().contains(RhythmEvent.Failed))
    }

    @Test
    fun `命中重置连击计数`() {
        val game = session()
        assertEquals(HitGrade.MISS, game.hit(0L)!!.grade)
        assertEquals(HitGrade.MISS, game.hit(0L)!!.grade)
        assertEquals(HitGrade.PERFECT, game.hit(48_000L)!!.grade)
        assertFalse(game.failed)
        // 重置后又可以容忍两次
        assertEquals(HitGrade.MISS, game.hit(0L)!!.grade)
        assertEquals(HitGrade.MISS, game.hit(0L)!!.grade)
        assertFalse(game.failed)
    }

    @Test
    fun `播放头越过未点的 tile 记一次 miss`() {
        val game = session()
        val events = game.advance(headFrame = 48_000L + 8_000L) // 超 GOOD 窗口 167ms
        assertEquals(listOf<RhythmEvent>(RhythmEvent.TileMissed(0)), events)
        assertEquals(1, game.nextTileIndex)
        assertEquals(1, game.stats().missCount)
    }

    @Test
    fun `越过分界只记一次 miss`() {
        val game = session()
        game.advance(48_000L + 8_000L)
        game.advance(48_000L + 20_000L)
        assertEquals(1, game.nextTileIndex)
        assertEquals(1, game.stats().missCount)
    }

    @Test
    fun `全部命中且走完余韵后 Finished`() {
        val game = session()
        game.timeline.tileFrames.forEach { frame -> assertEquals(HitGrade.PERFECT, game.hit(frame)!!.grade) }
        // 余韵（最后 tile 后一拍）没走完之前不该结束
        assertFalse(game.advance(timelineTotal(game) - 1).contains(RhythmEvent.Finished))
        assertTrue(game.advance(timelineTotal(game)).contains(RhythmEvent.Finished))
        assertTrue(game.finished)
        assertEquals(1.0, game.stats().accuracy, 1e-9)
    }

    @Test
    fun `结算统计偏差均值`() {
        val game = session()
        game.hit(48_000L + 3_840L) // 晚 80ms
        game.hit(96_000L - 1_920L) // 早 40ms
        val stats = game.stats()
        assertEquals(2, stats.perfectCount + stats.goodCount)
        assertEquals(20.0, stats.meanDeviationMs, 1e-6)
        assertEquals(60.0, stats.meanAbsDeviationMs, 1e-6)
    }

    private fun timelineTotal(game: RhythmSession): Long = game.timeline.totalFrames
}
