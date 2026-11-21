package dev.zmdld11.guitardojo.core.audio.assess

import dev.zmdld11.guitardojo.core.audio.pitch.MpmPitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class NoteEventExtractorTest {
    private val detector = MpmPitchDetector(SAMPLE_RATE)
    private val extractor = NoteEventExtractor(detector, SAMPLE_RATE)

    @Test
    fun `两段音被切成两个事件且音高正确`() {
        val samples =
            render(
                note(midi = 57, start = 0, duration = 24_000),
                note(midi = 60, start = 28_800, duration = 24_000),
                totalSamples = 40_000,
            )

        val events = extractor.extract(samples)

        assertEquals(2, events.size)
        assertEquals(57, events[0].midi)
        assertEquals(60, events[1].midi)
        // 起音允许 2 个跳步（约 43ms）的误差
        assertTrue("起音偏差过大：${events[0].startSample}", events[0].startSample <= 2_048)
        assertTrue("起音偏差过大：${events[1].startSample}", events[1].startSample in 26_752..30_848)
    }

    @Test
    fun `纯静音不产生事件`() {
        val events = extractor.extract(FloatArray(SAMPLE_RATE * 3))

        assertTrue(events.isEmpty())
    }

    @Test
    fun `短于最小时长的脉冲被过滤`() {
        val samples =
            render(
                note(midi = 64, start = 0, duration = 1_400),
                totalSamples = 40_000,
            )

        val events = extractor.extract(samples)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `升半音反映到 midi`() {
        val samples =
            render(
                note(midi = 58, start = 0, duration = 24_000),
                totalSamples = 28_000,
            )

        val events = extractor.extract(samples)

        assertEquals(1, events.size)
        assertEquals(58, events[0].midi)
    }

    @Test
    fun `泛音丰富的拨弦音色仍然正确`() {
        val samples =
            render(
                note(midi = 52, start = 0, duration = 30_000),
                totalSamples = 34_000,
                harmonics = listOf(1.0, 0.5, 0.25, 0.12),
            )

        val events = extractor.extract(samples)

        assertEquals(1, events.size)
        assertEquals(52, events[0].midi)
    }

    /** 采样点上直接叠加正弦（含谐波），段间保持真实静音。 */
    private class NoteSpec(
        val midi: Int,
        val start: Int,
        val duration: Int,
    )

    private fun note(
        midi: Int,
        start: Int,
        duration: Int,
    ): NoteSpec = NoteSpec(midi, start, duration)

    private fun render(
        vararg specs: NoteSpec,
        totalSamples: Int,
        harmonics: List<Double> = listOf(1.0),
    ): FloatArray {
        val out = FloatArray(totalSamples)
        specs.forEach { spec ->
            val frequency = 440.0 * 2.0.pow((spec.midi - 69) / 12.0)
            for (i in 0 until spec.duration) {
                val index = spec.start + i
                if (index >= totalSamples) break
                val t = i.toDouble() / SAMPLE_RATE
                var value = 0.0
                harmonics.forEachIndexed { harmonicIndex, amplitude ->
                    val harmonic = harmonicIndex + 1
                    if (frequency * harmonic < SAMPLE_RATE / 2) {
                        value += amplitude * sin(2.0 * PI * frequency * harmonic * t)
                    }
                }
                out[index] = (out[index] + 0.4 * value).toFloat()
            }
        }
        return out
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
