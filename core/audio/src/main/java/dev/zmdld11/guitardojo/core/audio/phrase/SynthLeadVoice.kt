package dev.zmdld11.guitardojo.core.audio.phrase

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min

/**
 * 双振荡器合成器主音：两个轻微失谐的 PolyBLEP 锯齿波，经一阶低通，
 * 音头带一点滤波扫频。音色明亮、颗粒感强，适合听清每个音。
 */
class SynthLeadVoice(
    private val sampleRate: Int,
) : PhraseVoice {
    private var phaseA = 0.0
    private var phaseB = 0.0
    private var stepA = 0.0
    private var stepB = 0.0
    private var filterState = 0f
    private var filterAlpha = 0.3f
    private var elapsed = 0
    private var durationSamples = 0
    private var pendingSilence = 0
    private var amplitude = 0f
    private var age = 0L

    override var isActive: Boolean = false
        private set

    override fun noteAge(): Long = age

    override fun start(
        midi: Int,
        velocity: Float,
        muted: Boolean,
        dead: Boolean,
        startOffsetSamples: Int,
        noteAge: Long,
    ) {
        val frequency = midiFrequency(midi)
        if (frequency <= 0.0 || frequency >= sampleRate / 2.0) return
        stepA = frequency * 0.9965 / sampleRate
        stepB = frequency * 1.0035 / sampleRate
        phaseA = 0.0
        phaseB = 0.37
        filterState = 0f
        val cutoff = min(sampleRate * 0.45, frequency * 9.0 + 900.0)
        filterAlpha = (1.0 - exp(-2.0 * PI * cutoff / sampleRate)).toFloat()
        elapsed = 0
        amplitude = velocity.coerceIn(0.05f, 1f) * AMPLITUDE
        val seconds =
            when {
                dead -> 0.08
                muted -> 0.28
                else -> 3.0
            }
        durationSamples = (sampleRate * seconds).toInt()
        pendingSilence = startOffsetSamples.coerceAtLeast(0)
        age = noteAge
        isActive = true
    }

    override fun render(
        out: FloatArray,
        count: Int,
    ) {
        if (!isActive) return
        var index = 0
        if (pendingSilence > 0) {
            val skip = minOf(pendingSilence, count)
            pendingSilence -= skip
            index = skip
        }
        while (index < count) {
            val seconds = elapsed.toDouble() / sampleRate
            val attack = (seconds / 0.006).coerceAtMost(1.0)
            val envelope = attack * (0.62 + 0.38 * exp(-seconds / 0.35)) * exp(-seconds / 1.8)
            val raw = 0.5f * (saw(phaseA, stepA) + saw(phaseB, stepB))
            filterState += filterAlpha * (raw - filterState)
            out[index] += (filterState * envelope * amplitude).toFloat()

            phaseA += stepA
            if (phaseA >= 1.0) phaseA -= 1.0
            phaseB += stepB
            if (phaseB >= 1.0) phaseB -= 1.0
            elapsed++
            if (elapsed >= durationSamples) {
                isActive = false
                return
            }
            index++
        }
    }

    override fun stop() {
        isActive = false
        pendingSilence = 0
    }

    /** PolyBLEP 锯齿波，抑制高频混叠。 */
    private fun saw(
        phase: Double,
        step: Double,
    ): Float {
        var value = 2.0 * phase - 1.0
        if (phase < step) {
            val t = phase / step
            value -= t + t - t * t - 1.0
        } else if (phase > 1.0 - step) {
            val t = (phase - 1.0) / step
            value -= t * t + t + t + 1.0
        }
        return value.toFloat()
    }

    private companion object {
        const val AMPLITUDE = 0.36f
    }
}
