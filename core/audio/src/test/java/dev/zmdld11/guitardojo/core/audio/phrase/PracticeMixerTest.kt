package dev.zmdld11.guitardojo.core.audio.phrase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeMixerTest {
    @Test
    fun `音符在块内按帧偏移起音`() {
        val mixer = PracticeMixer(48_000)
        mixer.startPlan(plan(notes = listOf(Planned(100L))))
        val out = ShortArray(256)

        mixer.render(out, ArrayList())

        assertTrue("前 100 帧应当是静音", out.take(100).all { it.toInt() == 0 })
        assertTrue("起音后应当有信号", out.drop(100).any { it.toInt() != 0 })
        assertEquals(256L, mixer.currentPlayhead)
    }

    @Test
    fun `拍点脉冲带循环内帧号与流帧号`() {
        val mixer = PracticeMixer(48_000)
        mixer.startPlan(plan(clicks = listOf(PlanClick(0, true))))
        val out = ShortArray(256)
        val pulses = ArrayList<PracticePulse>()

        mixer.render(out, pulses)

        assertEquals(1, pulses.size)
        assertEquals(0L, pulses[0].loopFrame)
        assertEquals(0L, pulses[0].streamFrame)
        assertTrue(pulses[0].barIndex == 0)
    }

    @Test
    fun `循环回绕后重新触发拍点`() {
        val mixer = PracticeMixer(48_000)
        mixer.startPlan(plan(loopFrames = 300, clicks = listOf(PlanClick(0, true))))
        val out = ShortArray(256)

        mixer.render(out, ArrayList())
        val pulses = ArrayList<PracticePulse>()
        mixer.render(out, pulses)

        assertEquals(1, pulses.size)
        assertEquals(300L, pulses[0].streamFrame)
        assertEquals(212L, mixer.currentPlayhead)
    }

    @Test
    fun `淡出后输出静音`() {
        val mixer = PracticeMixer(48_000)
        mixer.startPlan(plan(notes = listOf(Planned(0L))))
        val out = ShortArray(256)
        mixer.render(out, ArrayList())

        mixer.requestFadeOut()
        mixer.render(out, ArrayList())

        assertTrue(mixer.isFadeFinished)
        assertEquals(0, out.last().toInt())
    }

    @Test
    fun `没有计划时不崩溃且输出静音`() {
        val mixer = PracticeMixer(48_000)
        val out = ShortArray(256) { 1 }

        mixer.render(out, ArrayList())

        assertTrue(out.all { it.toInt() == 0 })
    }

    private data class Planned(
        val startFrame: Long,
        val midi: Int = 40,
    )

    private fun plan(
        loopFrames: Long = 1000,
        notes: List<Planned> = emptyList(),
        clicks: List<PlanClick> = emptyList(),
    ): PracticePlan =
        PracticePlan(
            loopFrames = loopFrames,
            notes =
                notes.map {
                    PlanNote(
                        startFrame = it.startFrame,
                        endFrame = it.startFrame + 200,
                        midi = it.midi,
                        velocity = 0.8f,
                        muted = false,
                        dead = false,
                    )
                },
            clicks = clicks,
            barStartFrames = longArrayOf(0),
            barStartTicks = longArrayOf(0),
            barIndices = intArrayOf(0),
            beatFrames = longArrayOf(0),
            beatBarIndices = intArrayOf(0),
            beatInBar = intArrayOf(0),
            barFramesPerTick = doubleArrayOf(37.5),
            loopStartTick = 0,
            loopEndTick = 3840,
            sampleRate = 48_000,
            speed = 1.0,
        )
}
