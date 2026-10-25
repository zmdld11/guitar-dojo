package dev.zmdld11.guitardojo.core.audio.phrase

import kotlin.math.exp

/**
 * FM 电钢琴：载波与调制器同频，调制指数在音头很大、随后快速收敛，形成
 *  Rhodes 式的「叮」；再叠一个 8 倍频的 tine 泛音。力度越大音头越亮。
 */
class ElectricPianoVoice(
    private val sampleRate: Int,
) : PhraseVoice {
    private var carrier = 0.0
    private var modulator = 0.0
    private var tine = 0.0
    private var carrierStep = 0.0
    private var tineStep = 0.0
    private var elapsed = 0
    private var durationSamples = 0
    private var pendingSilence = 0
    private var amplitude = 0f
    private var brightness = 0f
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
        carrierStep = frequency / sampleRate
        tineStep = (frequency * 8.0).coerceAtMost(sampleRate * 0.45) / sampleRate
        carrier = 0.0
        modulator = 0.0
        tine = 0.0
        elapsed = 0
        val level = velocity.coerceIn(0.05f, 1f)
        amplitude = level * AMPLITUDE
        brightness = 0.15f + 0.35f * level
        val seconds =
            when {
                dead -> 0.10
                muted -> 0.35
                else -> 4.0
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
            val attack = (seconds / ATTACK_SECONDS).coerceAtMost(1.0)
            val body = exp(-seconds / 1.7)
            val strike = 0.55 + 0.45 * exp(-seconds / 0.09)
            val modIndex = 0.35 + brightness * exp(-seconds / 0.30)
            val modulatorValue = SineTable.sinTurns(modulator)
            val carrierValue = SineTable.sinTurns(carrier + modIndex * modulatorValue)
            val tineValue = SineTable.sinTurns(tine) * 0.075 * exp(-seconds / 0.055)

            out[index] += ((carrierValue + tineValue) * (attack * body * strike) * amplitude).toFloat()

            carrier += carrierStep
            if (carrier >= 1.0) carrier -= 1.0
            modulator += carrierStep
            if (modulator >= 1.0) modulator -= 1.0
            tine += tineStep
            if (tine >= 1.0) tine -= 1.0

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
        const val ATTACK_SECONDS = 0.002
    }
}
