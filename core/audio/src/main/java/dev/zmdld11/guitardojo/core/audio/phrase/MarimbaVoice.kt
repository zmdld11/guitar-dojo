package dev.zmdld11.guitardojo.core.audio.phrase

import kotlin.math.exp

/** 马林巴/木琴：基频 + 4 倍与 10 倍泛音，衰减快，音高轮廓非常清楚。 */
class MarimbaVoice(
    private val sampleRate: Int,
) : PhraseVoice {
    private var phase1 = 0.0
    private var phase4 = 0.0
    private var phase10 = 0.0
    private var step1 = 0.0
    private var step4 = 0.0
    private var step10 = 0.0
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
        step1 = frequency / sampleRate
        step4 = (frequency * 4.0).coerceAtMost(sampleRate * 0.45) / sampleRate
        step10 = (frequency * 10.0).coerceAtMost(sampleRate * 0.45) / sampleRate
        phase1 = 0.0
        phase4 = 0.0
        phase10 = 0.0
        elapsed = 0
        amplitude = velocity.coerceIn(0.05f, 1f) * AMPLITUDE
        val seconds =
            when {
                dead -> 0.07
                muted -> 0.18
                else -> 1.1
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
            val attack = (seconds / 0.002).coerceAtMost(1.0)
            val body = exp(-seconds / 0.42)
            val value =
                SineTable.sinTurns(phase1) * body +
                    SineTable.sinTurns(phase4) * 0.32 * exp(-seconds / 0.14) +
                    SineTable.sinTurns(phase10) * 0.10 * exp(-seconds / 0.035)
            out[index] += (value * attack * amplitude).toFloat()

            phase1 += step1
            if (phase1 >= 1.0) phase1 -= 1.0
            phase4 += step4
            if (phase4 >= 1.0) phase4 -= 1.0
            phase10 += step10
            if (phase10 >= 1.0) phase10 -= 1.0
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
        const val AMPLITUDE = 0.30f
    }
}
