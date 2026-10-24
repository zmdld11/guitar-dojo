package dev.zmdld11.guitardojo.core.audio.pitch

/**
 * YIN 算法（de Cheveigné & Kawahara, 2002）。
 *
 * 用累积均值归一化差分函数（CMNDF）找第一个低于阈值的谷，
 * 对泛音较多的信号比裸自相关稳，但比 MPM 更容易在高把位出现倍频错误。
 * 保留它主要用于与 MPM 做对比测试。
 */
class YinPitchDetector(
    private val sampleRate: Int,
    override val windowSize: Int = 2048,
    private val threshold: Double = 0.15,
    private val minFrequencyHz: Double = 60.0,
    private val maxFrequencyHz: Double = 1400.0,
) : PitchDetector {
    private val minTau = (sampleRate / maxFrequencyHz).toInt().coerceAtLeast(2)
    private val maxTau = (sampleRate / minFrequencyHz).toInt().coerceAtMost(windowSize / 2)
    private val difference = DoubleArray(maxTau + 1)
    private val cmndf = DoubleArray(maxTau + 1)

    override fun detect(samples: FloatArray): PitchResult? {
        require(samples.size >= windowSize) { "帧长不足：需要 $windowSize，实际 ${samples.size}" }
        if (rms(samples) < SILENCE_RMS) return null

        computeDifference(samples)
        normalizeDifference()

        var tau = minTau
        while (tau <= maxTau) {
            if (cmndf[tau] < threshold) {
                while (tau + 1 <= maxTau && cmndf[tau + 1] < cmndf[tau]) tau++
                val refinedTau = parabolicInterpolation(cmndf, tau)
                if (refinedTau <= 0.0) return null
                val frequency = sampleRate / refinedTau
                if (frequency !in minFrequencyHz..maxFrequencyHz) return null
                return PitchResult(frequencyHz = frequency, clarity = 1.0 - cmndf[tau])
            }
            tau++
        }
        return null
    }

    private fun computeDifference(samples: FloatArray) {
        for (tau in 0..maxTau) {
            var sum = 0.0
            val limit = windowSize - tau
            for (j in 0 until limit) {
                val delta = samples[j] - samples[j + tau]
                sum += delta * delta
            }
            difference[tau] = sum
        }
    }

    private fun normalizeDifference() {
        cmndf[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..maxTau) {
            runningSum += difference[tau]
            cmndf[tau] = if (runningSum == 0.0) 1.0 else difference[tau] * tau / runningSum
        }
    }

    private fun rms(samples: FloatArray): Double {
        var sum = 0.0
        for (i in 0 until windowSize) sum += samples[i] * samples[i]
        return kotlin.math.sqrt(sum / windowSize)
    }

    private fun parabolicInterpolation(
        values: DoubleArray,
        index: Int,
    ): Double {
        if (index <= 0) return index.toDouble()
        if (index >= values.size - 1) return index.toDouble()
        val left = values[index - 1]
        val center = values[index]
        val right = values[index + 1]
        val denominator = 2 * (2 * center - right - left)
        if (denominator == 0.0) return index.toDouble()
        return index + (right - left) / denominator
    }

    private companion object {
        const val SILENCE_RMS = 1e-4
    }
}
