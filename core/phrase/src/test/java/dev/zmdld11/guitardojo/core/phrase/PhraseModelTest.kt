package dev.zmdld11.guitardojo.core.phrase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseModelTest {
    @Test
    fun `同一时刻只有一个音的是单音乐句`() {
        val phrase =
            phrase(
                note(tick = 0, midi = 60),
                note(tick = 480, midi = 62),
            )

        assertTrue(phrase.isMonophonic)
    }

    @Test
    fun `同 tick 双音不是单音乐句`() {
        val phrase =
            phrase(
                note(tick = 0, midi = 40),
                note(tick = 0, midi = 47),
                note(tick = 480, midi = 45),
            )

        assertFalse(phrase.isMonophonic)
    }

    @Test
    fun `没有音符按单音处理`() {
        assertTrue(phrase().isMonophonic)
    }

    private fun phrase(vararg notes: PhraseNote): Phrase =
        Phrase(
            id = "test.phrase",
            title = "测试",
            notes = notes.toList(),
        )

    private fun note(
        tick: Long,
        midi: Int,
    ): PhraseNote = PhraseNote(bar = 0, tick = tick, durationTicks = 240, midi = midi, string = 1, fret = 0)
}
