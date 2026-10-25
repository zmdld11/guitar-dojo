package dev.zmdld11.guitardojo.core.audio.phrase

import java.util.Random
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 加性合成的钢琴：8 个谐波，带轻微非谐性；高次谐波衰减更快，音头另加
 * 一小段槌击噪声。不是采样钢琴，但在手机扬声器上足够区分音高与和声。
 */
class PianoVoice(
    private val sampleRate: Int,
) : PhraseVoice {
    private val phases = DoubleArray(HARMONICS)
    private val steps = DoubleArray(HARMONICS)
    private val gains = FloatArray(HARMONICS)
    private val decayFactors = FloatArray(HARMONICS)
    private val random = Random()
    private var elapsed = 0
    private var durationSamples = 0
    private var pendingSilence = 0
    private var amplitude = 0f
    private var hammerRemaining = 0
    private var hammerGain = 0f
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
        val level = velocity.coerceIn(0.05f, 1f)
        random.setSeed(midi.toLong() * 1_000_003L + noteAge * 7_919L)
        for (harmonic in 1..HARMONICS) {
            val inharmonic = harmonic * frequency * sqrt(1.0 + INHARMONICITY * harmonic * harmonic)
            steps[harmonic - 1] = (inharmonic / sampleRate).coerceAtMost(0.49)
            gains[harmonic - 1] = (1.0 / harmonic.toDouble().pow(1.35)).toFloat()
            val decaySeconds = (0.95 / harmonic.toDouble().pow(0.5)).coerceAtLeast(0.12)
            decayFactors[harmonic - 1] = exp(-1.0 / (sampleRate * decaySeconds)).toFloat()
            phases[harmonic - 1] = 0.0
        }
        elapsed = 0
        amplitude = level * AMPLITUDE
        hammerRemaining = (sampleRate * 0.005).toInt()
        hammerGain = level * 0.02f
        val seconds =
            when {
                dead -> 0.08
                muted -> 0.30
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
        val attackSamples = (sampleRate * ATTACK_SECONDS).toInt().coerceAtLeast(1)
        while (index < count) {
            var value = 0f
            for (harmonic in 0 until HARMONICS) {
                phases[harmonic] += steps[harmonic]
                if (phases[harmonic] >= 1.0) phases[harmonic] -= 1.0
                value += SineTable.sinTurns(phases[harmonic]) * gains[harmonic]
                gains[harmonic] *= decayFactors[harmonic]
            }
            if (hammerRemaining > 0) {
                // 槌击：短促的高频噪声，让音头不全是正弦
                val envelope = hammerRemaining.toFloat() / (sampleRate * 0.005f)
                value += (random.nextFloat() * 2f - 1f) * hammerGain * envelope
                hammerRemaining--
            }
            val attack = (elapsed.toFloat() / attackSamples).coerceAtMost(1f)
            out[index] += value * amplitude * attack
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
        hammerRemaining = 0
    }

    private companion object {
        const val HARMONICS = 8
        const val INHARMONICITY = 0.0004
        const val AMPLITUDE = 0.22f
        const val ATTACK_SECONDS = 0.003
    }
}
