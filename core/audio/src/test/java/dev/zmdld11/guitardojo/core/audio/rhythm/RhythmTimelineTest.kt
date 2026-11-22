package dev.zmdld11.guitardojo.core.audio.rhythm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmTimelineTest {
    private val sampleRate = 48_000

    @Test
    fun `80 BPM 下四分一拍 36000 帧`() {
        val timeline =
            RhythmTimeline(
                sampleRate = sampleRate,
                bpm = 80.0,
                speed = 1.0,
                beats = List(4) { 1.0 },
                repeats = 1,
            )
        // tile[0] = 一拍准备时间之后；相邻 tile 间隔一拍
        assertEquals(36_000L, timeline.frameFor(0))
        assertEquals(72_000L, timeline.frameFor(1))
        assertEquals(36_000L, timeline.tileFrames[1] - timeline.tileFrames[0])
    }

    @Test
    fun `变速只改帧间隔不改序列结构`() {
        val full =
            RhythmTimeline(sampleRate, bpm = 100.0, speed = 1.0, beats = List(4) { 1.0 }, repeats = 1)
        val half =
            RhythmTimeline(sampleRate, bpm = 100.0, speed = 0.5, beats = List(4) { 1.0 }, repeats = 1)
        assertEquals(full.tileFrames.size, half.tileFrames.size)
        assertEquals(full.framesPerQuarter * 2, half.framesPerQuarter, 1e-6)
    }

    @Test
    fun `拍点帧号严格递增且跨 400 拍无累积漂移`() {
        val timeline =
            RhythmTimeline(
                sampleRate,
                bpm = 96.0,
                speed = 1.0,
                beats = List(8) { if (it % 2 == 0) 1.0 else 0.5 },
                repeats = 100,
            )
        var last = -1L
        timeline.tileFrames.forEach { frame ->
            assertTrue("帧号必须递增", frame > last)
            last = frame
        }
        // 第 400 个 tile = 准备一拍 + 前 399 个间隔的累计，允许 ±2 帧舍入误差
        val sum = List(399) { if (it % 2 == 0) 1.0 else 0.5 }.sum()
        val expected = (timeline.framesPerQuarter * (1 + sum)).toLong()
        val actual = timeline.tileFrames[399]
        assertTrue("累积漂移超限：expected≈$expected actual=$actual", kotlin.math.abs(actual - expected) <= 2)
    }

    @Test
    fun `准备拍期间球就开始转，越过结尾返回 null`() {
        val timeline =
            RhythmTimeline(sampleRate, bpm = 60.0, speed = 1.0, beats = listOf(1.0, 1.0), repeats = 1)
        val inLeadIn = timeline.progressAt(10_000L)!!
        assertEquals(0, inLeadIn.tileIndex)
        assertEquals(10_000.0 / 48_000.0, inLeadIn.fraction, 1e-9)
        val mid = timeline.progressAt(48_000L + 24_000L)
        assertEquals(1, mid!!.tileIndex)
        assertEquals(0.5, mid.fraction, 1e-9)
        assertNull(timeline.progressAt(timeline.totalFrames + 1))
    }

    @Test
    fun `三连间隔的转角占比是三分之一`() {
        val timeline =
            RhythmTimeline(sampleRate, bpm = 72.0, speed = 1.0, beats = listOf(1.0, 1.0 / 3), repeats = 1)
        // gaps = [1.0, 1/3]：tile1 在 tile0 后一拍，tile2 在 tile1 后三分之一拍
        val mid = timeline.progressAt(timeline.tileFrames[2] - (timeline.framesPerQuarter / 3).toLong() / 2)
        assertEquals(2, mid!!.tileIndex)
        assertEquals(0.5, mid.fraction, 0.01)
        assertEquals((1.0 / 3) * 0.5, mid.turnFraction, 0.01)
    }
}
