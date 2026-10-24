package dev.zmdld11.guitardojo.core.audio.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class YinPitchDetectorTest {
    private val detector = YinPitchDetector(Signals.SAMPLE_RATE)

    @Test
    fun `标准调弦六根弦都能识别`() {
        GuitarTuning.standard.forEach { string ->
            val target = string.targetFrequencyHz()
            val result = detector.detect(Signals.tone(target))
            assertNotNull("${string.noteLabel} 未识别", result)
            assertEquals(target, result!!.frequencyHz, target * 0.005)
        }
    }

    @Test
    fun `静音返回空`() {
        assertNull(detector.detect(Signals.silence()))
    }

    @Test
    fun `精度优于 1 音分`() {
        val result = detector.detect(Signals.tone(440.0))!!
        assertTrue(abs(NoteNaming.centsBetween(440.0, result.frequencyHz)) < 1.0)
    }

    @Test
    fun `低音 E2 在短窗下仍可识别`() {
        val result = detector.detect(Signals.tone(82.41))
        assertNotNull(result)
        assertEquals(82.41, result!!.frequencyHz, 0.5)
    }
}
