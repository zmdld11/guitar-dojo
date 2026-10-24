package dev.zmdld11.guitardojo.core.audio.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClickSynthTest {
    @Test
    fun `时长换算为正确的采样点数量`() {
        val samples = ClickSynth.generate(sampleRate = 48_000, frequencyHz = 1_000.0, durationMillis = 50, amplitude = 0.8)
        assertEquals(2_400, samples.size)
    }

    @Test
    fun `峰值不超过设定振幅`() {
        val amplitude = 0.5
        val samples = ClickSynth.generate(48_000, 1_176.0, 40, amplitude)
        val peak = samples.maxOf { abs(it.toInt()) }
        assertTrue("峰值 $peak 应不超过 ${(amplitude * Short.MAX_VALUE).toInt()}", peak <= (amplitude * Short.MAX_VALUE).toInt() + 1)
        assertTrue("峰值应明显大于静音", peak > 1_000)
    }

    @Test
    fun `起音从零开始，避免爆音`() {
        val samples = ClickSynth.generate(48_000, 1_176.0, 40, 0.9)
        assertTrue("首样本应接近零", abs(samples.first().toInt()) < 200)
    }

    @Test
    fun `振幅越大峰值越大`() {
        val quiet = ClickSynth.generate(48_000, 1_176.0, 40, 0.2).maxOf { abs(it.toInt()) }
        val loud = ClickSynth.generate(48_000, 1_176.0, 40, 0.8).maxOf { abs(it.toInt()) }
        assertTrue("$loud 应大于 $quiet", loud > quiet * 3)
    }
}
