package dev.zmdld11.guitardojo.core.audio.phrase

import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.phrase.PhraseBar
import dev.zmdld11.guitardojo.core.phrase.PhraseNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticePlanTest {
    private val phrase =
        Phrase(
            id = "builtin.test",
            title = "测试",
            ppq = 960,
            bars =
                listOf(
                    PhraseBar(0, 0, 3840, listOf(4, 4), 80.0),
                    PhraseBar(1, 3840, 3840, listOf(4, 4), 80.0),
                ),
            notes =
                listOf(
                    PhraseNote(bar = 0, tick = 0, durationTicks = 960, midi = 40, string = 6, fret = 0),
                    PhraseNote(bar = 0, tick = 960, durationTicks = 960, midi = 42, string = 6, fret = 2),
                    PhraseNote(bar = 1, tick = 3840, durationTicks = 960, midi = 45, string = 5, fret = 0),
                ),
        )

    @Test
    fun `80 BPM 四四拍下每拍 36000 帧`() {
        val plan = PhraseScheduler.plan(phrase, sampleRate = 48_000)

        assertEquals(288_000L, plan.loopFrames)
        assertEquals(0L, plan.barStartFrames[0])
        assertEquals(144_000L, plan.barStartFrames[1])
        assertEquals(8, plan.beatFrames.size)
        assertEquals(0L, plan.beatFrames[0])
        assertEquals(36_000L, plan.beatFrames[1])
        assertEquals(144_000L, plan.beatFrames[4])
        assertTrue(plan.clicks[0].accent)
        assertTrue(plan.clicks[4].accent)
    }

    @Test
    fun `音符被换算到正确的帧号`() {
        val plan = PhraseScheduler.plan(phrase, sampleRate = 48_000)

        assertEquals(listOf(0L, 36_000L, 144_000L), plan.notes.map { it.startFrame })
        assertEquals(listOf(36_000L, 72_000L, 180_000L), plan.notes.map { it.endFrame })
    }

    @Test
    fun `速度减半时时间轴加倍且音高数据不变`() {
        val plan = PhraseScheduler.plan(phrase, sampleRate = 48_000, speed = 0.5)

        assertEquals(576_000L, plan.loopFrames)
        assertEquals(listOf(0L, 72_000L, 288_000L), plan.notes.map { it.startFrame })
        assertEquals(listOf(40, 42, 45), plan.notes.map { it.midi })
    }

    @Test
    fun `只选第二小节时循环从该小节开始`() {
        val plan = PhraseScheduler.plan(phrase, sampleRate = 48_000, loopStartBar = 1, loopEndBar = 1)

        assertEquals(144_000L, plan.loopFrames)
        assertEquals(listOf(1), plan.barIndices.toList())
        assertEquals(listOf(3840L), plan.barStartTicks.toList())
        assertEquals(1, plan.notes.size)
        assertEquals(0L, plan.notes.single().startFrame)
        assertEquals(45, plan.notes.single().midi)
    }

    @Test
    fun `tick 与帧号可以互相换算`() {
        val plan = PhraseScheduler.plan(phrase, sampleRate = 48_000, speed = 0.75)

        val frame = plan.frameForTick(3840)
        assertEquals(plan.loopFrames / 2, frame)

        val tick = plan.tickForFrame(frame)
        assertTrue("tick=$tick", kotlin.math.abs(tick - 3840) <= 1)
    }

    @Test
    fun `关闭节拍器时没有 click 事件`() {
        val plan = PhraseScheduler.plan(phrase, sampleRate = 48_000, clickEnabled = false)

        assertTrue(plan.clicks.isEmpty())
        assertEquals(8, plan.beatFrames.size)
    }

    @Test
    fun `速度会被限制在 0_25 到 1_5`() {
        assertEquals(0.25, PhraseScheduler.plan(phrase, 48_000, speed = 0.01).speed, 1e-9)
        assertEquals(1.5, PhraseScheduler.plan(phrase, 48_000, speed = 9.0).speed, 1e-9)
    }
}
