package dev.zmdld11.guitardojo.core.audio.pitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MpmPitchDetectorTest {
    private val detector = MpmPitchDetector(Signals.SAMPLE_RATE)

    @Test
    fun `标准调弦六根弦都能识别`() {
        val targets = GuitarTuning.standard.map { it.targetFrequencyHz() }
        targets.forEach { target ->
            val result = detector.detect(Signals.tone(target))
            assertNotNull("未识别出 $target Hz", result)
            assertEquals("$target Hz 偏差过大", target, result!!.frequencyHz, target * 0.005)
        }
    }

    @Test
    fun `基频弱、泛音强时不误判为高八度`() {
        // 真实电吉他：基频被拾音器位置削弱，二次谐波反而更响
        val fundamental = 82.41
        val samples =
            Signals.tone(
                fundamental,
                harmonics = listOf(0.3, 1.0, 0.7, 0.4, 0.2),
            )
        val result = detector.detect(samples)
        assertNotNull(result)
        assertEquals(fundamental, result!!.frequencyHz, fundamental * 0.01)
    }

    @Test
    fun `加入一成白噪仍能识别`() {
        val target = 196.0
        val result = detector.detect(Signals.tone(target, noiseAmount = 0.1))
        assertNotNull(result)
        assertEquals(target, result!!.frequencyHz, target * 0.01)
    }

    @Test
    fun `静音返回空`() {
        assertNull(detector.detect(Signals.silence()))
    }

    @Test
    fun `纯噪声不应给出可信结果`() {
        val random = kotlin.random.Random(42)
        val noise = FloatArray(2048) { random.nextFloat() * 2 - 1 }
        val result = detector.detect(noise)
        if (result != null) {
            assertTrue("噪声的置信度不应过高：${result.clarity}", result.clarity < 0.9)
        }
    }

    @Test
    fun `高把位 E6 也能识别`() {
        val target = 1318.51
        val result = detector.detect(Signals.tone(target))
        assertNotNull(result)
        assertEquals(target, result!!.frequencyHz, target * 0.01)
    }

    @Test
    fun `单帧耗时满足实时`() {
        val samples = Signals.tone(110.0, harmonics = listOf(1.0, 0.8, 0.6))
        detector.detect(samples) // 预热 JIT
        val start = System.nanoTime()
        repeat(20) { detector.detect(samples) }
        val perFrameMillis = (System.nanoTime() - start) / 1e6 / 20
        assertTrue("单帧 $perFrameMillis ms 过慢", perFrameMillis < 50.0)
        println("MPM 单帧平均耗时：${"%.2f".format(perFrameMillis)} ms")
    }

    @Test
    fun `频率偏差在 1 音分以内的精度`() {
        val target = 440.0
        val result = detector.detect(Signals.tone(target))!!
        val cents = abs(NoteNaming.centsBetween(target, result.frequencyHz))
        assertTrue("偏差 $cents 音分", cents < 1.0)
    }
}
