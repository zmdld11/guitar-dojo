package dev.zmdld11.guitardojo.core.audio.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatTimelineTest {
    private val sampleRate = 48_000

    @Test
    fun `120 BPM 四分音符每拍 24000 帧`() {
        val timeline = BeatTimeline(sampleRate, MetronomeConfig(bpm = 120))
        assertEquals(0L, timeline.frameFor(0))
        assertEquals(24_000L, timeline.frameFor(1))
        assertEquals(48_000L, timeline.frameFor(2))
    }

    @Test
    fun `400 拍之后仍无累积误差`() {
        val timeline = BeatTimeline(sampleRate, MetronomeConfig(bpm = 137))
        val framesPerBeat = sampleRate * 60.0 / 137.0
        for (index in 0L..400L) {
            assertEquals(index * framesPerBeat, timeline.frameFor(index).toDouble(), 1.0)
        }
    }

    @Test
    fun `每小节拍数与重拍位置正确`() {
        val timeline = BeatTimeline(sampleRate, MetronomeConfig(bpm = 90, beatsPerBar = 3))
        val bar = (0 until 6).map { timeline.advance() }
        assertEquals(listOf(0, 1, 2, 0, 1, 2), bar.map { it.beatInBar })
        assertEquals(listOf(true, false, false, true, false, false), bar.map { it.isDownbeat })
    }

    @Test
    fun `细分在拍内均分且只在主拍上重读`() {
        val timeline = BeatTimeline(sampleRate, MetronomeConfig(bpm = 60, subdivisions = 3))
        val pulses = (0 until 6).map { timeline.advance() }
        assertEquals(listOf(0, 1, 2, 0, 1, 2), pulses.map { it.subIndex })
        assertEquals(listOf(true, false, false, false, false, false), pulses.map { it.isDownbeat })
        // 60 BPM 一拍 48000 帧，三连音每格 16000 帧
        assertEquals(listOf(0L, 16_000L, 32_000L, 48_000L, 64_000L, 80_000L), pulses.map { it.frame })
    }

    @Test
    fun `drainsBefore 不含边界帧`() {
        val timeline = BeatTimeline(sampleRate, MetronomeConfig(bpm = 120))
        val pulses = timeline.drainsBefore(24_000L)
        assertEquals(1, pulses.size)
        assertEquals(0L, pulses.first().frame)
        assertEquals(24_000L, timeline.peek().frame)
    }

    @Test
    fun `改速度不改变下一个拍点的时刻`() {
        val timeline = BeatTimeline(sampleRate, MetronomeConfig(bpm = 120))
        timeline.drainsBefore(24_000L)
        val nextBefore = timeline.peek()

        timeline.reconfigure(MetronomeConfig(bpm = 60))

        val nextAfter = timeline.peek()
        assertEquals(nextBefore.frame, nextAfter.frame)
        assertEquals(nextBefore.beatInBar, nextAfter.beatInBar)
        // 新速度生效：下一拍间距变为 48000 帧
        timeline.advance()
        assertEquals(nextBefore.frame + 48_000L, timeline.peek().frame)
    }

    @Test
    fun `拍点帧号严格递增`() {
        val timeline = BeatTimeline(sampleRate, MetronomeConfig(bpm = 300, subdivisions = 4))
        var previous = -1L
        repeat(200) {
            val frame = timeline.advance().frame
            assertTrue("帧号必须严格递增：$previous -> $frame", frame > previous)
            previous = frame
        }
    }
}
