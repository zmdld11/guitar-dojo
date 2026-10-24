package dev.zmdld11.guitardojo.core.audio.phrase

import dev.zmdld11.guitardojo.core.audio.pitch.MpmPitchDetector
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2

class PluckedStringVoiceTest {
    private val sampleRate = 48_000

    @Test
    fun `合成音在 E2 到 E6 内音准误差小于 8 音分`() {
        val detector = MpmPitchDetector(sampleRate, 2048)
        val midiNotes = listOf(40, 45, 50, 55, 59, 64, 76, 88)

        midiNotes.forEach { midi ->
            val rendered = renderNote(midi)
            val window = rendered.copyOfRange(6_000, 6_000 + 2048)
            val result = detector.detect(window)
            assertTrue("midi=$midi 没有检测到音高", result != null)
            val expected = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
            val cents = 1200.0 * log2(result!!.frequencyHz / expected)
            assertTrue("midi=$midi 误差 ${"%.1f".format(cents)} 音分", abs(cents) < 8.0)
        }
    }

    @Test
    fun `声音会自然衰减`() {
        val rendered = renderNote(40, durationSamples = sampleRate)
        val early = energy(rendered, 5_000, 9_000)
        val late = energy(rendered, 35_000, 39_000)

        assertTrue("early=$early late=$late", late < early * 0.6)
    }

    @Test
    fun `闷音比开放音衰减更快`() {
        val open = renderNote(40, muted = false)
        val muted = renderNote(40, muted = true)

        val openTail = energy(open, 20_000, 24_000)
        val mutedTail = energy(muted, 20_000, 24_000)

        assertTrue("open=$openTail muted=$mutedTail", mutedTail < openTail * 0.5)
    }

    @Test
    fun `停止后不再输出`() {
        val voice = PluckedStringVoice(sampleRate)
        val buffer = FloatArray(4096)
        voice.start(52, 0.8f, muted = false, dead = false)
        voice.render(buffer, 1024)
        voice.stop()
        buffer.fill(0f)
        voice.render(buffer, 1024)

        assertTrue(buffer.all { it == 0f })
    }

    private fun renderNote(
        midi: Int,
        muted: Boolean = false,
        durationSamples: Int = sampleRate / 2,
    ): FloatArray {
        val voice = PluckedStringVoice(sampleRate)
        val buffer = FloatArray(durationSamples)
        voice.start(midi, 0.9f, muted = muted, dead = false)
        voice.render(buffer, buffer.size)
        return buffer
    }

    private fun energy(
        samples: FloatArray,
        from: Int,
        to: Int,
    ): Double {
        var sum = 0.0
        for (index in from until to) {
            sum += samples[index] * samples[index]
        }
        return sum
    }
}
