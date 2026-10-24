package dev.zmdld11.guitardojo.core.audio.pitch

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** 测试用信号发生器。 */
internal object Signals {
    const val SAMPLE_RATE = 48_000

    /**
     * 合成一个音。harmonics 的索引 0 表示基频，依次为 2 次、3 次谐波……
     * noiseAmount 是相对峰值加入的白噪。
     */
    fun tone(
        frequencyHz: Double,
        windowSize: Int = 2048,
        harmonics: List<Double> = listOf(1.0),
        noiseAmount: Double = 0.0,
        seed: Int = 7,
    ): FloatArray {
        val random = Random(seed)
        val samples = FloatArray(windowSize)
        for (i in 0 until windowSize) {
            val t = i.toDouble() / SAMPLE_RATE
            var value = 0.0
            harmonics.forEachIndexed { index, amplitude ->
                val harmonic = index + 1
                if (amplitude > 0.0 && frequencyHz * harmonic < SAMPLE_RATE / 2) {
                    value += amplitude * sin(2.0 * PI * frequencyHz * harmonic * t)
                }
            }
            if (noiseAmount > 0.0) {
                value += noiseAmount * (random.nextDouble() * 2 - 1)
            }
            samples[i] = value.toFloat()
        }
        return samples
    }

    fun silence(windowSize: Int = 2048): FloatArray = FloatArray(windowSize)
}
