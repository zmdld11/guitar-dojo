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

        assertEquals(0, out.last().toInt())
    }

    @Test
    fun `音符脉冲带乐句内的音高下标`() {
        val mixer = PracticeMixer(48_000)
        mixer.startPlan(plan(notes = listOf(Planned(0L, phraseIndex = 7))))
        val out = ShortArray(256)
        val notes = ArrayList<PracticeNotePulse>()

        mixer.render(out, ArrayList(), notes)

        assertEquals(1, notes.size)
        assertEquals(0L, notes[0].streamFrame)
        assertEquals(7, notes[0].phraseIndex)
    }

    @Test
    fun `热切换音色不重置播放头且新音色继续出声`() {
        val mixer = PracticeMixer(48_000, PhraseTimbre.CLEAN_GUITAR)
        mixer.startPlan(plan(loopFrames = 10_000, notes = listOf(Planned(0L), Planned(600L))))
        val out = ShortArray(256)
        mixer.render(out, ArrayList())

        mixer.requestTimbre(PhraseTimbre.MARIMBA)
        mixer.render(out, ArrayList())
        mixer.render(out, ArrayList())

        assertEquals(PhraseTimbre.MARIMBA, mixer.timbre)
        assertEquals(768L, mixer.currentPlayhead)
        assertTrue("切换后新音色应当继续出声", out.any { it.toInt() != 0 })
    }

    @Test
    fun `没有计划时不崩溃且输出静音`() {
        val mixer = PracticeMixer(48_000)
        val out = ShortArray(256) { 1 }

        mixer.render(out, ArrayList())

        assertTrue(out.all { it.toInt() == 0 })
    }

    @Test
    fun `静音乐句时不渲染音符与音符脉冲`() {
        val muted = PracticeMixer(48_000)
        muted.phraseMuted = true
        muted.startPlan(plan(notes = listOf(Planned(startFrame = 10))))
        val mutedOut = ShortArray(256)
        val mutedPulses = ArrayList<PracticeNotePulse>()
        muted.render(mutedOut, ArrayList(), mutedPulses)

        assertTrue(mutedPulses.isEmpty())
        assertTrue(mutedOut.all { it == 0.toShort() })

        val audible = PracticeMixer(48_000)
        audible.startPlan(plan(notes = listOf(Planned(startFrame = 10, phraseIndex = 0))))
        val audibleOut = ShortArray(256)
        val audiblePulses = ArrayList<PracticeNotePulse>()
        audible.render(audibleOut, ArrayList(), audiblePulses)

        assertTrue(audiblePulses.isNotEmpty())
        assertTrue(audibleOut.any { it != 0.toShort() })
    }

    private data class Planned(
        val startFrame: Long,
        val midi: Int = 40,
        val phraseIndex: Int = -1,
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
                        phraseIndex = it.phraseIndex,
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
