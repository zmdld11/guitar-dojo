package dev.zmdld11.guitardojo.core.audio.assess

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 拍点锚定：在录音里找拍点瞬态，与期望拍点序列比对，解出「录音流相对播放流」的样本偏移。
 *
 * 考核播放只留拍点，而合成咔哒声集中在 1.2/1.8 kHz，先高通滤掉吉他的低频能量，
 * 再用块 RMS 包络找瞬态起音。对每个候选起音假设一个偏移并数期望拍点的命中数，
 * 取命中最多、最先出现者。命中数不足说明录音里没有可用的拍点（比如没开拍点），
 * 返回 null，调用方退化为 0 偏移。
 */
class ClickAnchor(
    private val sampleRate: Int,
    private val blockSize: Int = 128,
    /** 瞬态判定的块 RMS 绝对下限。 */
    private val minBlockRms: Double = 0.02,
    /** 瞬态判定的相对门限：块 RMS 需超过前窗中值的倍数。 */
    private val contrastRatio: Double = 3.0,
    /** 期望拍点与候选起音的匹配容差（样本）。 */
    private val matchToleranceSamples: Long = 512,
) {
    private val hpCoefficient = exp(-2.0 * PI * HIGH_PASS_HZ / sampleRate)

    fun findOffset(
        samples: FloatArray,
        expectedClickSamples: List<Long>,
    ): Long? {
        if (expectedClickSamples.isEmpty() || samples.size < blockSize * 4) return null
        val onsets = detectOnsets(samples)
        if (onsets.isEmpty()) return null

        var bestOffset: Long? = null
        var bestHits = 0
        for (onset in onsets) {
            val candidateOffset = onset - expectedClickSamples.first()
            val hits =
                expectedClickSamples.count { expected ->
                    val target = expected + candidateOffset
                    onsets.any { abs(it - target) <= matchToleranceSamples }
                }
            if (hits > bestHits) {
                bestHits = hits
                bestOffset = candidateOffset
            }
        }
        return bestOffset?.takeIf { bestHits >= requiredHits(expectedClickSamples.size) }
    }

    private fun requiredHits(expectedCount: Int): Int = maxOf(3, expectedCount / 3)

    private fun detectOnsets(samples: FloatArray): List<Long> {
        val filtered = highPass(samples)
        val blockCount = filtered.size / blockSize
        val onsets = ArrayList<Long>()
        val history = ArrayDeque<Double>()
        var refractoryBlocks = 0

        for (block in 0 until blockCount) {
            if (refractoryBlocks > 0) {
                refractoryBlocks--
                continue
            }
            val current = blockRms(filtered, block)
            val baseline = if (history.size >= 4) median(history) else 0.0
            if (current >= minBlockRms && current >= baseline * contrastRatio) {
                onsets += block.toLong() * blockSize
                refractoryBlocks = REFRACTORY_BLOCKS
                history.clear()
            } else {
                history.addLast(current)
                if (history.size > HISTORY_BLOCKS) history.removeFirst()
            }
        }
        return onsets
    }

    /** 一阶高通：y[n] = x[n] − x[n−1] + a·y[n−1]，a 由截止频率推出。 */
    private fun highPass(samples: FloatArray): FloatArray {
        val out = FloatArray(samples.size)
        var previousInput = 0f
        var previousOutput = 0f
        for (index in samples.indices) {
            val input = samples[index]
            val output = input - previousInput + (hpCoefficient * previousOutput).toFloat()
            out[index] = output
            previousInput = input
            previousOutput = output
        }
        return out
    }

    private fun blockRms(
        samples: FloatArray,
        block: Int,
    ): Double {
        var sum = 0.0
        val start = block * blockSize
        for (index in start until start + blockSize) {
            val value = samples[index].toDouble()
            sum += value * value
        }
        return sqrt(sum / blockSize)
    }

    private fun median(values: Collection<Double>): Double {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private companion object {
        const val HIGH_PASS_HZ = 700.0
        const val HISTORY_BLOCKS = 16
        const val REFRACTORY_BLOCKS = 20
    }
}
