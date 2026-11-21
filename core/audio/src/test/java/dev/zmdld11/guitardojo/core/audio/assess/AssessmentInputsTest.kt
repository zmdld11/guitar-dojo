package dev.zmdld11.guitardojo.core.audio.assess

import dev.zmdld11.guitardojo.core.audio.phrase.PhraseScheduler
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.phrase.PhraseBar
import dev.zmdld11.guitardojo.core.phrase.PhraseNote
import org.junit.Assert.assertEquals
import org.junit.Test

class AssessmentInputsTest {
    @Test
    fun `期望音符与拍点来自练习计划的时间轴`() {
        val phrase = twoNotePhrase()
        val plan = PhraseScheduler.plan(phrase, sampleRate = SAMPLE_RATE, speed = 1.0)

        val targets = assessmentTargets(phrase, plan)

        assertEquals(2, targets.notes.size)
        assertEquals(plan.frameForTick(0), targets.notes[0].startSample)
        assertEquals(plan.frameForTick(4800), targets.notes[1].startSample)
        assertEquals(0, targets.notes[0].barIndex)
        // 60 BPM 4/4：每拍 1 秒，两小节共 8 拍
        assertEquals(8, targets.clickSamples.size)
        assertEquals(listOf(0L, SAMPLE_RATE.toLong(), (2 * SAMPLE_RATE).toLong(), (3 * SAMPLE_RATE).toLong()), targets.clickSamples.take(4))
    }

    @Test
    fun `循环区间之外的音不参与判定`() {
        val phrase = twoNotePhrase()
        val plan = PhraseScheduler.plan(phrase, sampleRate = SAMPLE_RATE, speed = 1.0, loopStartBar = 0, loopEndBar = 0)

        val targets = assessmentTargets(phrase, plan)

        assertEquals(1, targets.notes.size)
    }

    private fun twoNotePhrase(): Phrase =
        Phrase(
            id = "test.phrase",
            title = "测试",
            bars =
                listOf(
                    PhraseBar(index = 0, startTick = 0, lengthTicks = 3840, tempoBpm = 60.0),
                    PhraseBar(index = 1, startTick = 3840, lengthTicks = 3840, tempoBpm = 60.0),
                ),
            notes =
                listOf(
                    PhraseNote(bar = 0, tick = 0, durationTicks = 480, midi = 60, string = 1, fret = 0),
                    PhraseNote(bar = 1, tick = 3840 + 960, durationTicks = 480, midi = 64, string = 1, fret = 2),
                ),
        )

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
