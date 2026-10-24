package dev.zmdld11.guitardojo.core.audio.pitch

import kotlin.math.sqrt

/**
 * MPM 算法（McLeod Pitch Method）。
 *
 * 用归一化平方差函数（NSDF）找「关键极大值」，再取第一个接近全局最大值的峰。
 * 相比 YIN 的固定阈值法，它对基频较弱的信号（电吉他失真、泛音丰富）更稳，
 * 也不容易把二次谐波当成基频。选型对比见 issue #11。
 */
class MpmPitchDetector(
    private val sampleRate: Int,
    override val windowSize: Int = 2048,
    /** 峰值判定系数：取第一个达到 [peakRatio] * 最大峰值的极大值。越接近 1 越保守。 */
    private val peakRatio: Double = 0.9,
    private val minFrequencyHz: Double = 60.0,
    private val maxFrequencyHz: Double = 1400.0,
    private val clarityThreshold: Double = 0.6,
) : PitchDetector {
    // NSDF 需要从很小的延迟算起，否则高把位（E6 以上）的首个周期会被截断；
    // 频率上下限在结果处统一校验。
    private val tauFloor = 2
    private val maxTau = (sampleRate / minFrequencyHz).toInt().coerceAtMost(windowSize / 2)
    private val nsdf = DoubleArray(maxTau + 1)

    override fun detect(samples: FloatArray): PitchResult? {
        require(samples.size >= windowSize) { "帧长不足：需要 $windowSize，实际 ${samples.size}" }
        if (rms(samples) < SILENCE_RMS) return null

        computeNsdf(samples)

        // 只从首次过零之后找峰：零延迟附近的旁瓣会被误当成基频（低音尤其明显）
        val searchStart = firstZeroCrossing().coerceAtLeast(tauFloor + 1)

        // 收集关键极大值
        val maxima = ArrayList<Int>(16)
        var tau = searchStart
        while (tau < maxTau) {
            if (nsdf[tau] > 0.0 && nsdf[tau] > nsdf[tau - 1] && nsdf[tau] >= nsdf[tau + 1]) {
                maxima += tau
            }
            tau++
        }
        if (maxima.isEmpty()) return null

        val highest = maxima.maxOf { nsdf[it] }
        if (highest < clarityThreshold) return null

        val chosen = maxima.firstOrNull { nsdf[it] >= peakRatio * highest } ?: return null
        val refined = parabolicInterpolation(nsdf, chosen)
        val frequency = sampleRate / refined
        if (frequency !in minFrequencyHz..maxFrequencyHz) return null

        return PitchResult(
            frequencyHz = frequency,
            clarity = nsdf[chosen].coerceIn(0.0, 1.0),
        )
    }

    private fun computeNsdf(samples: FloatArray) {
        for (tau in tauFloor..maxTau) {
            var correlation = 0.0
            var energy = 0.0
            val limit = windowSize - tau
            for (j in 0 until limit) {
                val a = samples[j]
                val b = samples[j + tau]
                correlation += a * b
                energy += a * a + b * b
            }
            nsdf[tau] = if (energy == 0.0) 0.0 else 2.0 * correlation / energy
        }
    }

    private fun firstZeroCrossing(): Int {
        for (tau in tauFloor..maxTau) {
            if (nsdf[tau] < 0.0) return tau
        }
        return maxTau
    }

    private fun rms(samples: FloatArray): Double {
        var sum = 0.0
        for (i in 0 until windowSize) sum += samples[i] * samples[i]
        return sqrt(sum / windowSize)
    }

    private fun parabolicInterpolation(
        values: DoubleArray,
        index: Int,
    ): Double {
        if (index <= 0 || index >= values.size - 1) return index.toDouble()
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
