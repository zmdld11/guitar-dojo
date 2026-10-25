package dev.zmdld11.guitardojo.core.audio.phrase

import kotlin.math.exp

/** 8-bit 方波主音：50% 占空比、快速衰减，像老游戏机的单声道音源。 */
class ChipVoice(
    private val sampleRate: Int,
) : PhraseVoice {
    private var phase = 0.0
    private var step = 0.0
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
        step = frequency / sampleRate
        phase = 0.0
        elapsed = 0
        amplitude = velocity.coerceIn(0.05f, 1f) * AMPLITUDE
        val seconds =
            when {
                dead -> 0.06
                muted -> 0.16
                else -> 1.2
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
            val envelope =
                (seconds / 0.002).coerceAtMost(1.0) *
                    (0.35 + 0.65 * exp(-seconds / 0.18)) *
                    exp(-seconds / 0.9)
            val value = if (phase < 0.5) 1f else -1f
            out[index] += (value * envelope * amplitude).toFloat()

            phase += step
            if (phase >= 1.0) phase -= 1.0
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

    private companion object {
        const val AMPLITUDE = 0.20f
    }
}
