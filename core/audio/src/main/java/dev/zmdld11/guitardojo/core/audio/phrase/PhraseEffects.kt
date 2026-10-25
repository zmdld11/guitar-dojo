package dev.zmdld11.guitardojo.core.audio.phrase

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 乐句总线的轻量效果：高通、临场感 EQ、可选合唱与房间混响。
 *
 * 参数随音色变化；节拍咔哒在效果链之后叠加，保持干脆。全部是固定系数的
 * 轻量实现，不引入采样或第三方 DSP 库。
 */
internal class PhraseEffects(
    sampleRate: Int,
    timbre: PhraseTimbre,
) {
    private val settings = Settings.of(timbre)
    private val highpass = OnePoleHighpass(45.0, sampleRate)
    private val body = if (settings.bodyDb != 0.0) Biquad.peaking(sampleRate, 120.0, settings.bodyDb, 0.9) else null
    private val mid = if (settings.midDb != 0.0) Biquad.peaking(sampleRate, 750.0, settings.midDb, 0.8) else null
    private val presence = Biquad.peaking(sampleRate, 2_800.0, settings.presenceDb, 0.9)
    private val air = Biquad.highShelf(sampleRate, 6_500.0, settings.airDb)
    private val chorus = if (settings.chorusMix > 0f) Chorus(sampleRate, settings.chorusMix) else null
    private val reverb = RoomReverb(sampleRate)

    fun process(
        buffer: FloatArray,
        count: Int,
    ) {
        for (index in 0 until count) {
            var sample = highpass.process(buffer[index])
            body?.let { sample = it.process(sample) }
            mid?.let { sample = it.process(sample) }
            sample = presence.process(sample)
            sample = air.process(sample)
            chorus?.let { sample = it.process(sample) }
            buffer[index] = sample * DRY_GAIN + reverb.process(sample) * settings.reverbMix
        }
    }

    private data class Settings(
        val bodyDb: Double,
        val midDb: Double,
        val presenceDb: Double,
        val airDb: Double,
        val chorusMix: Float,
        val reverbMix: Float,
    ) {
        companion object {
            fun of(timbre: PhraseTimbre): Settings =
                when (timbre) {
                    PhraseTimbre.ELECTRIC_PIANO -> Settings(0.0, 0.0, 1.5, 1.5, 0.12f, 0.22f)
                    PhraseTimbre.PIANO -> Settings(0.0, 0.0, 1.0, 1.2, 0.05f, 0.20f)
                    PhraseTimbre.SYNTH_LEAD -> Settings(0.0, 0.0, 2.0, 1.5, 0.16f, 0.22f)
                    PhraseTimbre.CHIP -> Settings(0.0, 0.0, 0.0, 0.0, 0f, 0.10f)
                    PhraseTimbre.MARIMBA -> Settings(0.0, 0.0, 1.2, 1.0, 0f, 0.18f)
                    PhraseTimbre.CLEAN_GUITAR -> Settings(3.0, -1.0, 1.5, 1.0, 0.18f, 0.20f)
                    PhraseTimbre.CRUNCH_GUITAR -> Settings(2.0, 1.5, 0.5, 0.5, 0f, 0.10f)
                    PhraseTimbre.DISTORTION_GUITAR -> Settings(1.0, 2.5, 0.0, 0.0, 0f, 0.08f)
                }
        }
    }

    private class OnePoleHighpass(
        cutoffHz: Double,
        sampleRate: Int,
    ) {
        private val alpha = (1.0 / (1.0 + 2.0 * PI * cutoffHz / sampleRate)).toFloat()
        private var previousInput = 0f
        private var previousOutput = 0f

        fun process(input: Float): Float {
            val output = alpha * (previousOutput + input - previousInput)
            previousInput = input
            previousOutput = output
            return output
        }
    }

    private class Biquad private constructor(
        private val b0: Float,
        private val b1: Float,
        private val b2: Float,
        private val a1: Float,
        private val a2: Float,
    ) {
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun process(input: Float): Float {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }

        companion object {
            fun peaking(
                sampleRate: Int,
                frequencyHz: Double,
                gainDb: Double,
                q: Double,
            ): Biquad {
                val a = 10.0.pow(gainDb / 40.0)
                val omega = 2.0 * PI * frequencyHz / sampleRate
                val alpha = sin(omega) / (2.0 * q)
                val cosine = cos(omega)
                val a0 = 1.0 + alpha / a
                return Biquad(
                    b0 = ((1.0 + alpha * a) / a0).toFloat(),
                    b1 = (-2.0 * cosine / a0).toFloat(),
                    b2 = ((1.0 - alpha * a) / a0).toFloat(),
                    a1 = (-2.0 * cosine / a0).toFloat(),
                    a2 = ((1.0 - alpha / a) / a0).toFloat(),
                )
            }

            fun highShelf(
                sampleRate: Int,
                frequencyHz: Double,
                gainDb: Double,
            ): Biquad {
                val a = 10.0.pow(gainDb / 40.0)
                val omega = 2.0 * PI * frequencyHz / sampleRate
                val cosine = cos(omega)
                val alpha = sin(omega) / 2.0 * sqrt(2.0)
                val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
                val a0 = (a + 1.0) - (a - 1.0) * cosine + twoSqrtAAlpha
                return Biquad(
                    b0 = (a * ((a + 1.0) + (a - 1.0) * cosine + twoSqrtAAlpha) / a0).toFloat(),
                    b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosine) / a0).toFloat(),
                    b2 = (a * ((a + 1.0) + (a - 1.0) * cosine - twoSqrtAAlpha) / a0).toFloat(),
                    a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosine) / a0).toFloat(),
                    a2 = (((a + 1.0) - (a - 1.0) * cosine - twoSqrtAAlpha) / a0).toFloat(),
                )
            }
        }
    }

    /** 轻微的立体声合唱感；单声道下表现为音色更厚，不会明显走音。 */
    private class Chorus(
        sampleRate: Int,
        private val mix: Float,
    ) {
        private val size = (sampleRate * 0.05).toInt().coerceAtLeast(256)
        private val buffer = FloatArray(size)
        private var writeIndex = 0
        private var phase = 0.0
        private val baseDelay = sampleRate * 0.014
        private val depth = sampleRate * 0.0012
        private val increment = 0.7 / sampleRate

        fun process(input: Float): Float {
            buffer[writeIndex] = input
            val delay = baseDelay + depth * sin(phase * 2.0 * PI)
            val wet = read(writeIndex - delay)
            writeIndex++
            if (writeIndex >= size) writeIndex = 0
            phase += increment
            if (phase >= 1.0) phase -= 1.0
            return input * (1f - mix) + wet * mix
        }

        private fun read(position: Double): Float {
            var wrapped = position
            while (wrapped < 0.0) wrapped += size
            val whole = (wrapped.toInt()) % size
            val fraction = (wrapped - whole).toFloat()
            val next = (whole + 1) % size
            return buffer[whole] * (1f - fraction) + buffer[next] * fraction
        }
    }

    /** Schroeder 结构的小房间混响，只返回湿声。 */
    private class RoomReverb(
        sampleRate: Int,
    ) {
        private val preDelay = DelayLine((sampleRate * 0.012).toInt().coerceAtLeast(1))
        private val combs =
            intArrayOf(1557, 1617, 1491, 1422).map {
                Comb((it * sampleRate / 44_100.0).toInt().coerceAtLeast(1))
            }
        private val allpasses =
            intArrayOf(556, 225).map {
                Allpass((it * sampleRate / 44_100.0).toInt().coerceAtLeast(1))
            }

        fun process(input: Float): Float {
            val excitation = preDelay.process(input) * 0.6f
            var sum = 0f
            for (comb in combs) {
                sum += comb.process(excitation, 0.80f, 0.30f)
            }
            var wet = sum * 0.25f
            for (allpass in allpasses) {
                wet = allpass.process(wet, 0.5f)
            }
            return wet
        }
    }

    private class DelayLine(
        size: Int,
    ) {
        private val buffer = FloatArray(size)
        private var index = 0

        fun process(input: Float): Float {
            val output = buffer[index]
            buffer[index] = input
            index++
            if (index >= buffer.size) index = 0
            return output
        }
    }

    private class Comb(
        size: Int,
    ) {
        private val buffer = FloatArray(size)
        private var index = 0
        private var damping = 0f

        fun process(
            input: Float,
            feedback: Float,
            dampingFactor: Float,
        ): Float {
            val output = buffer[index]
            damping = output * (1f - dampingFactor) + damping * dampingFactor
            buffer[index] = input + damping * feedback
            index++
            if (index >= buffer.size) index = 0
            return output
        }
    }

    private class Allpass(
        size: Int,
    ) {
        private val buffer = FloatArray(size)
        private var index = 0

        fun process(
            input: Float,
            gain: Float,
        ): Float {
            val delayed = buffer[index]
            buffer[index] = input + delayed * gain
            index++
            if (index >= buffer.size) index = 0
            return delayed - input
        }
    }

    private companion object {
        const val DRY_GAIN = 0.95f
    }
}
