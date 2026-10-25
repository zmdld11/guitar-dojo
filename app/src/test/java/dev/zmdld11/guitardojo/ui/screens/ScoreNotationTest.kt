package dev.zmdld11.guitardojo.ui.screens

import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.phrase.PhraseBar
import dev.zmdld11.guitardojo.core.phrase.PhraseNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreNotationTest {
    private val ppq = 960

    @Test
    fun `八分音符一条符尾，四分音符没有符尾`() {
        assertEquals(1, noteValueOf(480, ppq).flags)
        assertEquals(0, noteValueOf(960, ppq).flags)
        assertEquals(2, noteValueOf(240, ppq).flags)
    }

    @Test
    fun `附点按实际时值识别`() {
        val dottedEighth = noteValueOf(720, ppq)
        assertEquals(1, dottedEighth.flags)
        assertEquals(1, dottedEighth.dots)

        val dottedHalf = noteValueOf(2880, ppq)
        assertEquals(0, dottedHalf.flags)
        assertEquals(1, dottedHalf.dots)
    }

    @Test
    fun `三连音带 tuplet 标记`() {
        val triplet = noteValueOf(320, ppq)
        assertEquals(1, triplet.flags)
        assertEquals(3, triplet.tuplet)
    }

    @Test
    fun `全音符没有符干`() {
        assertFalse(noteValueOf(3840, ppq).stem)
        assertTrue(noteValueOf(1920, ppq).stem)
    }

    @Test
    fun `略短于标准值的发声长度按较长的标准音值记谱`() {
        val riffNote = noteValueOf(384, ppq)
        assertEquals(480, riffNote.ticks)
        assertEquals(1, riffNote.flags)
        assertEquals(0, riffNote.dots)
    }

    @Test
    fun `五拍长音拆成全音符与四分音符并标记连音`() {
        val phrase = phrase(listOf(note(tick = 0, duration = 4_800)))
        val display = displayNotesOf(phrase)

        assertEquals(2, display.size)
        assertEquals(3_840, display[0].durationTicks)
        assertEquals(960, display[1].durationTicks)
        assertFalse(display[0].tiedFromPrevious)
        assertTrue(display[1].tiedFromPrevious)
        assertEquals(1, tieSegmentsOf(display).size)
    }

    @Test
    fun `跨小节的长音在小节线处拆开`() {
        val phrase = phrase(listOf(note(tick = 0, duration = 9_600)))
        val display = displayNotesOf(phrase)

        assertEquals(3, display.size)
        assertEquals(0, display[0].tick)
        assertEquals(3_840, display[0].durationTicks)
        assertEquals(3_840, display[1].tick)
        assertEquals(1_920, display[2].durationTicks)
    }

    @Test
    fun `小节内五拍长音拆成半音符与八分音符`() {
        val phrase = phrase(listOf(note(tick = 0, duration = 2_400)))
        val display = displayNotesOf(phrase)

        assertEquals(2, display.size)
        assertEquals(1_920, display[0].durationTicks)
        assertEquals(480, display[1].durationTicks)
        assertTrue(display[1].tiedFromPrevious)
    }

    @Test
    fun `八分音符跑动按拍上下交替拨弦`() {
        val notes = (0 until 8).map { note(tick = it * 480L, duration = 480) }
        val directions = pickDirectionsOf(phrase(notes))

        assertEquals(PickDirection.DOWN, directions[0])
        assertEquals(PickDirection.UP, directions[480])
        assertEquals(PickDirection.DOWN, directions[960])
        assertEquals(PickDirection.UP, directions[1_440])
    }

    @Test
    fun `每拍一个四分音符时全部是下拨`() {
        val notes = (0 until 4).map { note(tick = it * 960L, duration = 960) }
        val directions = pickDirectionsOf(phrase(notes))

        notes.forEach { assertEquals(PickDirection.DOWN, directions[it.tick]) }
    }

    @Test
    fun `三连音按上下上排列`() {
        val notes = (0 until 3).map { note(tick = it * 320L, duration = 320) }
        val directions = pickDirectionsOf(phrase(notes))

        assertEquals(PickDirection.DOWN, directions[0])
        assertEquals(PickDirection.UP, directions[320])
        assertEquals(PickDirection.DOWN, directions[640])
    }

    private fun note(
        tick: Long,
        duration: Long,
        string: Int = 6,
        fret: Int = 0,
    ) = PhraseNote(
        bar = if (tick >= 3_840) 1 else 0,
        tick = tick,
        durationTicks = duration,
        midi = 40,
        string = string,
        fret = fret,
    )

    private fun phrase(notes: List<PhraseNote>) =
        Phrase(
            id = "test",
            title = "test",
            ppq = ppq,
            bars =
                listOf(
                    PhraseBar(index = 0, startTick = 0, lengthTicks = 3_840, tempoBpm = 100.0),
                    PhraseBar(index = 1, startTick = 3_840, lengthTicks = 3_840, tempoBpm = 100.0),
                    PhraseBar(index = 2, startTick = 7_680, lengthTicks = 3_840, tempoBpm = 100.0),
                ),
            notes = notes,
        )
}
