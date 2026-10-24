package dev.zmdld11.guitardojo.core.audio.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NoteNamingTest {
    @Test
    fun `A4 440Hz 无偏差`() {
        val note = NoteNaming.fromFrequency(440.0)!!
        assertEquals("A", note.name)
        assertEquals(4, note.octave)
        assertEquals(0.0, note.cents, 0.01)
    }

    @Test
    fun `偏高偏低给出正确方向的音分`() {
        assertTrue(NoteNaming.fromFrequency(445.0)!!.cents > 0)
        assertTrue(NoteNaming.fromFrequency(435.0)!!.cents < 0)
    }

    @Test
    fun `吉他六根弦的音名正确`() {
        val expected =
            mapOf(
                82.41 to "E2",
                110.0 to "A2",
                146.83 to "D3",
                196.0 to "G3",
                246.94 to "B3",
                329.63 to "E4",
            )
        expected.forEach { (frequency, label) ->
            val note = NoteNaming.fromFrequency(frequency)!!
            assertEquals("$frequency Hz", label, note.label)
        }
    }

    @Test
    fun `音名与频率可以往返换算`() {
        for (midi in 40..64) {
            val frequency = NoteNaming.frequencyOf(midi)
            assertEquals(midi, NoteNaming.fromFrequency(frequency)!!.midi)
        }
    }

    @Test
    fun `音分换算对称`() {
        val cents = NoteNaming.centsBetween(440.0, 466.16)
        assertEquals(-cents, NoteNaming.centsBetween(466.16, 440.0), 1e-9)
        assertTrue("C#5 应比 A4 高约 100 音分", abs(cents - 100.0) < 2.0)
    }

    @Test
    fun `容差判定`() {
        val note = NoteNaming.fromFrequency(441.5)!!
        assertTrue(note.cents in 1.0..10.0)
        assertTrue("±5 音分内算准", NoteNaming.fromFrequency(440.5)!!.isInTune())
        assertFalse(NoteNaming.fromFrequency(450.0)!!.isInTune())
    }

    @Test
    fun `非法频率返回空`() {
        assertNull(NoteNaming.fromFrequency(0.0))
        assertNull(NoteNaming.fromFrequency(-1.0))
    }
}

class GuitarTuningTest {
    @Test
    fun `标准调弦的频率符合 A440`() {
        val frequencies = GuitarTuning.standard.map { it.targetFrequencyHz() }
        val expected = listOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)
        expected.forEachIndexed { index, value ->
            assertEquals(value, frequencies[index], 0.02)
        }
    }

    @Test
    fun `按频率找最近的弦`() {
        assertEquals(6, GuitarTuning.nearestString(83.0)!!.number)
        assertEquals(1, GuitarTuning.nearestString(330.0)!!.number)
    }

    @Test
    fun `超出三半音不算命中`() {
        // 500 Hz 比 1 弦 E4(329.6) 高约 720 音分，不属于标准调弦
        assertNull(GuitarTuning.nearestString(500.0))
        // 60 Hz 比 6 弦 E2(82.4) 低约 548 音分
        assertNull(GuitarTuning.nearestString(60.0))
    }

    @Test
    fun `略微偏低的音仍能匹配到对应弦`() {
        assertNotNull(GuitarTuning.nearestString(108.0))
    }
}
