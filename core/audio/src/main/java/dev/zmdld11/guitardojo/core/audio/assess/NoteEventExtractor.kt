package dev.zmdld11.guitardojo.core.audio.assess

import dev.zmdld11.guitardojo.core.audio.pitch.PitchDetector
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 从录音里提出的一个音符事件：一段音高稳定的连续区域。 */
data class NoteEvent(
    val startSample: Long,
    val endSample: Long,
    /** 段内中位频率，供判定音准与计算音分偏差。 */
    val frequencyHz: Double,
    val midi: Int,
    val clarity: Double,
) {
    val durationSamples: Long
        get() = endSample - startSample
}

/**
 * 录音 → 音符事件序列（离线分析，不做流式判定）。
 *
 * 滑窗复用调音器的音高检测（窗口一致），对窗口 midi 做三点中值平滑压掉单窗误检，
 * 再把相邻同音高的窗口聚合成事件；RMS 低于门限视为静音并切断事件。
 * 事件最短时长过滤掉拨弦瞬态与单窗噪声。
 */
class NoteEventExtractor(
    private val detector: PitchDetector,
    private val sampleRate: Int,
    private val hopSize: Int = 1024,
    /** 低于该 RMS 视为静音；高于检测器内部静音线，避免把底噪当音。 */
    private val minRms: Double = 0.01,
    private val minDurationMs: Int = 80,
) {
    private class WindowPitch(
        val midi: Int,
        val frequencyHz: Double,
        val clarity: Double,
    )

    fun extract(samples: FloatArray): List<NoteEvent> {
        if (samples.size < detector.windowSize) return emptyList()
        val pitches = ArrayList<WindowPitch?>()
        var offset = 0
        while (offset + detector.windowSize <= samples.size) {
            val window = samples.copyOfRange(offset, offset + detector.windowSize)
            pitches += analyzeWindow(window)
            offset += hopSize
        }
        return groupEvents(medianSmooth(pitches))
    }

    private fun analyzeWindow(window: FloatArray): WindowPitch? {
        if (rms(window) < minRms) return null
        val result = detector.detect(window) ?: return null
        return WindowPitch(
            midi = midiOf(result.frequencyHz),
            frequencyHz = result.frequencyHz,
            clarity = result.clarity,
        )
    }

    /** 相邻三窗中值；邻域里有静音窗时保持原值，避免把音符边缘拖平。 */
    private fun medianSmooth(pitches: List<WindowPitch?>): List<WindowPitch?> =
        pitches.indices.map { index ->
            val left = pitches.getOrNull(index - 1)
            val right = pitches.getOrNull(index + 1)
            val current = pitches[index] ?: return@map null
            if (left == null || right == null) {
                current
            } else {
                listOf(left.midi, current.midi, right.midi).sorted()[1].let { median ->
                    if (median == current.midi) current else WindowPitch(median, current.frequencyHz, current.clarity)
                }
            }
        }

    private fun groupEvents(pitches: List<WindowPitch?>): List<NoteEvent> {
        val events = ArrayList<NoteEvent>()
        var startIndex = -1
        var midi = 0
        val frequencies = ArrayList<Double>()
        val clarities = ArrayList<Double>()

        fun close(endIndexExclusive: Int) {
            if (startIndex >= 0 && frequencies.isNotEmpty()) {
                val durationMs = (endIndexExclusive - startIndex) * hopSize * 1000.0 / sampleRate
                if (durationMs >= minDurationMs) {
                    events +=
                        NoteEvent(
                            startSample = startIndex.toLong() * hopSize,
                            endSample = endIndexExclusive.toLong() * hopSize,
                            frequencyHz = frequencies.sorted()[frequencies.size / 2],
                            midi = midi,
                            clarity = clarities.sorted()[clarities.size / 2],
                        )
                }
            }
            startIndex = -1
            frequencies.clear()
            clarities.clear()
        }

        pitches.forEachIndexed { index, pitch ->
            if (pitch == null || (startIndex >= 0 && pitch.midi != midi)) {
                close(index)
            }
            if (pitch != null) {
                if (startIndex < 0) {
                    startIndex = index
                    midi = pitch.midi
                }
                frequencies += pitch.frequencyHz
                clarities += pitch.clarity
            }
        }
        close(pitches.size)
        return events
    }

    private fun rms(samples: FloatArray): Double {
        var sum = 0.0
        samples.forEach { value -> sum += value.toDouble() * value }
        return sqrt(sum / samples.size)
    }

    private fun midiOf(frequencyHz: Double): Int = (69.0 + 12.0 * log2(frequencyHz / 440.0)).roundToInt()
}
