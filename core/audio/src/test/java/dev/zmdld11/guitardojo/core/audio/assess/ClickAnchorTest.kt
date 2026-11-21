package dev.zmdld11.guitardojo.core.audio.assess

import dev.zmdld11.guitardojo.core.audio.metronome.ClickSynth
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ClickAnchorTest {
    @Test
    fun `从含琴声底噪的录音里恢复拍点偏移`() {
        val preRoll = 14_400
        val expected = listOf(0L, 36_000L, 72_000L, 108_000L)
        val samples = FloatArray(SAMPLE_RATE * 3)
        // 吉他似的低频持续音：高通后应低于瞬态门限
        for (index in samples.indices) {
            samples[index] += (0.08 * sin(2.0 * PI * 110.0 * index / SAMPLE_RATE)).toFloat()
        }
        expected.forEach { frame ->
            placeClick(samples, frame + preRoll, accent = frame == 0L)
        }

        val offset = ClickAnchor(SAMPLE_RATE).findOffset(samples, expected)

        // 起音按块量化（128 样本），允许一个块的误差
        assertTrue("偏移误差过大：$offset", offset != null && abs(offset - preRoll) <= 128)
    }

    @Test
    fun `没有拍点时返回 null`() {
        val samples = FloatArray(SAMPLE_RATE * 2)
        for (index in samples.indices) {
            samples[index] = (0.05 * sin(2.0 * PI * 220.0 * index / SAMPLE_RATE)).toFloat()
        }

        val offset = ClickAnchor(SAMPLE_RATE).findOffset(samples, listOf(0L, 36_000L, 72_000L))

        assertNull(offset)
    }

    @Test
    fun `期望拍点为空直接返回 null`() {
        val offset = ClickAnchor(SAMPLE_RATE).findOffset(FloatArray(SAMPLE_RATE), emptyList())

        assertNull(offset)
    }

    /** 把合成拍点叠加进缓冲。 */
    private fun placeClick(
        samples: FloatArray,
        atSample: Long,
        accent: Boolean,
    ) {
        val click =
            ClickSynth.generate(
                SAMPLE_RATE,
                if (accent) 1760.0 else 1174.0,
                36,
                0.45,
                decayRatio = 0.2,
            )
        val start = atSample.toInt()
        for (index in click.indices) {
            val target = start + index
            if (target >= samples.size) break
            samples[target] += click[index] / 32768f
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
