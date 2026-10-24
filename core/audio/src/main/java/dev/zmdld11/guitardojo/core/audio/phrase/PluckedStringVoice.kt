package dev.zmdld11.guitardojo.core.audio.phrase

import java.util.Random
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow

/**
 * Karplus-Strong 拨弦合成的一个复音。
 *
 * 噪声激励 + 延迟线 + 两点平均低通 + 一阶全通分数延迟。音准在 E2-E6 内
 * 实测误差 <2 音分（见单元测试），不需要采样或 SoundFont。
 */
class PluckedStringVoice(
    private val sampleRate: Int,
) {
    private val delay = FloatArray(MAX_DELAY_SAMPLES)
    private val random = Random()

    private var delayLength = 0
    private var writeIndex = 0
    private var allpassCoefficient = 0f
    private var allpassX1 = 0f
    private var allpassY1 = 0f
    private var lastOutput = 0f
    private var dcInput = 0f
    private var dcOutput = 0f
    private var damping = 0f
    private var gain = 0f
    private var pendingSilence = 0
    private var remainingSamples = 0

    var isActive: Boolean = false
        private set

    /** 预留给未来的抢占策略：越新的音优先级越高。 */
    var age: Long = 0L
        private set

    fun start(
        midi: Int,
        velocity: Float,
        muted: Boolean,
        dead: Boolean,
        startOffsetSamples: Int = 0,
        noteAge: Long = 0L,
    ) {
        val frequency = 440.0 * 2.0.pow((midi - 69) / 12.0)
        if (frequency <= 0.0) return
        val totalDelay = sampleRate / frequency - 0.5
        val length = floor(totalDelay).toInt()
        if (length < MIN_DELAY_SAMPLES || length > MAX_DELAY_SAMPLES - 2) return

        delayLength = length
        val fraction = totalDelay - length
        allpassCoefficient = ((1.0 - fraction) / (1.0 + fraction)).toFloat()

        var previous = 0f
        for (i in 0 until delayLength) {
            val noise = random.nextFloat() * 2f - 1f
            previous = 0.5f * (noise + previous)
            delay[i] = previous
        }
        // 去直流：平均滤波对直流增益为 1，不去掉会让短延迟（高音）只剩一个直流振荡
        var sum = 0f
        for (i in 0 until delayLength) sum += delay[i]
        val mean = sum / delayLength
        for (i in 0 until delayLength) delay[i] -= mean

        writeIndex = 0
        allpassX1 = 0f
        allpassY1 = 0f
        lastOutput = 0f
        dcInput = 0f
        dcOutput = 0f
        gain = (velocity.coerceIn(0.05f, 1f) * BASE_GAIN).let { if (dead) it * 0.5f else it }
        // 反馈是每绕一圈乘一次，所以衰减常数要按「周期」算，而不是按采样
        val decaySeconds =
            when {
                dead -> DEAD_DECAY_SECONDS
                muted -> MUTED_DECAY_SECONDS
                else -> NORMAL_DECAY_SECONDS
            }
        damping = exp(-1.0 / (frequency * decaySeconds)).toFloat()
        remainingSamples =
            if (dead) {
                (sampleRate * DEAD_SECONDS).toInt()
            } else {
                (sampleRate * MAX_SECONDS).toInt()
            }
        pendingSilence = startOffsetSamples.coerceAtLeast(0)
        age = noteAge
        isActive = true
    }

    /** 把本音的样本叠加进 [out]，最多 [count] 个采样。 */
    fun render(
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
            val x = delay[writeIndex]
            val y = allpassCoefficient * x + allpassX1 - allpassCoefficient * allpassY1
            allpassX1 = x
            allpassY1 = y

            // 一阶高通（约 4Hz）阻止直流在循环里永久驻留
            val blocked = y - dcInput + DC_BLOCK_COEFFICIENT * dcOutput
            dcInput = y
            dcOutput = blocked

            out[index] += blocked * gain

            delay[writeIndex] = damping * 0.5f * (blocked + lastOutput)
            lastOutput = blocked
            writeIndex++
            if (writeIndex >= delayLength) writeIndex = 0

            remainingSamples--
            if (remainingSamples <= 0) {
                isActive = false
                return
            }
            index++
        }
    }

    fun stop() {
        isActive = false
        pendingSilence = 0
    }

    private companion object {
        const val MAX_DELAY_SAMPLES = 4096
        const val MIN_DELAY_SAMPLES = 8
        const val BASE_GAIN = 0.32f
        const val NORMAL_DECAY_SECONDS = 1.6
        const val MUTED_DECAY_SECONDS = 0.18
        const val DEAD_DECAY_SECONDS = 0.06
        const val MAX_SECONDS = 3.5
        const val DEAD_SECONDS = 0.09
        const val DC_BLOCK_COEFFICIENT = 0.9995f
    }
}
