package dev.zmdld11.guitardojo.core.audio.phrase

import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow

/**
 * Karplus-Strong 拨弦合成的一个复音，按清音电吉他的频谱特性调过。
 *
 * 相对最初版本：
 * - 激励噪声先经高通和拨弦位置梳状滤波，音头不再发闷；
 * - 环路里的低通只负责高频衰减，整体衰减由独立反馈系数控制，两者不再互相牵制；
 * - 额外叠加一段约 6ms 的拨片瞬态，接近真实拨弦的「啪」声。
 *
 * 音准在 E2-E6 内实测误差 <2 音分（见单元测试），不需要采样或 SoundFont。
 */
class PluckedStringVoice(
    private val sampleRate: Int,
) : PhraseVoice {
    private val delay = FloatArray(MAX_DELAY_SAMPLES)
    private val excitation = FloatArray(MAX_DELAY_SAMPLES)
    private val random = Random()

    private var delayLength = 0
    private var writeIndex = 0
    private var allpassCoefficient = 0f
    private var allpassX1 = 0f
    private var allpassY1 = 0f
    private var lastFiltered = 0f
    private var dcInput = 0f
    private var dcOutput = 0f
    private var loopFeedback = 0f
    private var gain = 0f
    private var pendingSilence = 0
    private var remainingSamples = 0

    private var pickRemaining = 0
    private var pickIndex = 0
    private var pickGain = 0f
    private var pickHpInput = 0f
    private var pickHpOutput = 0f

    override var isActive: Boolean = false
        private set

    /** 预留给未来的抢占策略：越新的音优先级越高。 */
    var age: Long = 0L
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
        val frequency = 440.0 * 2.0.pow((midi - 69) / 12.0)
        if (frequency <= 0.0) return
        // 环路里的两点平均低通会带来半个采样的相位延迟，这里一并补偿
        val totalDelay = sampleRate / frequency - LOOP_DELAY_COMPENSATION
        val length = floor(totalDelay).toInt()
        if (length < MIN_DELAY_SAMPLES || length > MAX_DELAY_SAMPLES - 2) return

        delayLength = length
        val fraction = (totalDelay - length).coerceIn(0.0, 1.0)
        allpassCoefficient = ((1.0 - fraction) / (1.0 + fraction)).toFloat()

        // 同一次演奏里每个音的噪声都不同，但给定 midi/序号时结果可复现，单测不会随机失败
        random.setSeed(midi.toLong() * 1_000_003L + noteAge * 7_919L)
        fillExcitation()

        writeIndex = 0
        allpassX1 = 0f
        allpassY1 = 0f
        lastFiltered = 0f
        dcInput = 0f
        dcOutput = 0f
        val level = velocity.coerceIn(0.05f, 1f)
        gain = if (dead) level * BASE_GAIN * 0.5f else level * BASE_GAIN
        val decaySeconds =
            when {
                dead -> DEAD_DECAY_SECONDS
                muted -> MUTED_DECAY_SECONDS
                else -> NORMAL_DECAY_SECONDS
            }
        loopFeedback = exp(-1.0 / (frequency * decaySeconds)).toFloat()
        remainingSamples =
            if (dead) {
                (sampleRate * DEAD_SECONDS).toInt()
            } else {
                (sampleRate * MAX_SECONDS).toInt()
            }
        pendingSilence = startOffsetSamples.coerceAtLeast(0)
        pickGain = PICK_LEVEL * level
        pickRemaining = if (dead) 0 else PICK_SAMPLES
        pickIndex = 0
        pickHpInput = 0f
        pickHpOutput = 0f
        age = noteAge
        isActive = true
    }

    /**
     * 生成一段带拨弦位置梳状滤波的噪声。
     *
     * 在靠近琴桥处拨弦会削弱某些谐波，音色更「电」；直接喂白噪声则偏木吉他、也偏闷。
     */
    private fun fillExcitation() {
        var hpInput = 0f
        var hpOutput = 0f
        for (index in 0 until delayLength) {
            val white = random.nextFloat() * 2f - 1f
            val highpassed = EXCITATION_HP_ALPHA * (hpOutput + white - hpInput)
            hpInput = white
            hpOutput = highpassed
            excitation[index] = highpassed
        }
        val pickDelay = ((delayLength - 1) * PICK_POSITION).toInt().coerceIn(1, delayLength - 1)
        var peak = 1e-6f
        for (index in delayLength - 1 downTo 0) {
            val delayed = if (index >= pickDelay) excitation[index - pickDelay] * PICK_COMB_GAIN else 0f
            val value = excitation[index] - delayed
            delay[index] = value
            peak = maxOf(peak, abs(value))
        }
        val scale = EXCITATION_PEAK / peak
        for (index in 0 until delayLength) delay[index] *= scale
    }

    /** 把本音的样本叠加进 [out]，最多 [count] 个采样。 */
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
            val x = delay[writeIndex]
            val y = allpassCoefficient * x + allpassX1 - allpassCoefficient * allpassY1
            allpassX1 = x
            allpassY1 = y

            // 一阶高通（约 4Hz）阻止直流在循环里永久驻留
            val blocked = y - dcInput + DC_BLOCK_COEFFICIENT * dcOutput
            dcInput = y
            dcOutput = blocked

            // 两点平均低通：只让高频比低频衰减快一点，基频几乎不受影响
            val filtered = 0.5f * (blocked + lastFiltered)
            lastFiltered = blocked

            var sample = blocked
            if (pickRemaining > 0) {
                val white = random.nextFloat() * 2f - 1f
                val hp = PICK_HP_ALPHA * (pickHpOutput + white - pickHpInput)
                pickHpInput = white
                pickHpOutput = hp
                val envelope = exp(-pickIndex.toFloat() / PICK_DECAY_SAMPLES)
                sample += hp * envelope * pickGain
                pickIndex++
                pickRemaining--
            }
            out[index] += sample * gain

            delay[writeIndex] = filtered * loopFeedback
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

    override fun stop() {
        isActive = false
        pendingSilence = 0
        pickRemaining = 0
    }

    private companion object {
        const val MAX_DELAY_SAMPLES = 4096
        const val MIN_DELAY_SAMPLES = 8
        const val BASE_GAIN = 0.34f
        const val NORMAL_DECAY_SECONDS = 2.2
        const val MUTED_DECAY_SECONDS = 0.22
        const val DEAD_DECAY_SECONDS = 0.07
        const val MAX_SECONDS = 3.2
        const val DEAD_SECONDS = 0.09
        const val DC_BLOCK_COEFFICIENT = 0.9995f
        const val LOOP_DELAY_COMPENSATION = 0.5
        const val EXCITATION_HP_ALPHA = 0.985f
        const val EXCITATION_PEAK = 0.9f
        const val PICK_POSITION = 0.13f
        const val PICK_COMB_GAIN = 0.85f

        const val PICK_LEVEL = 0.16f
        const val PICK_DECAY_SAMPLES = 180f
        const val PICK_SAMPLES = 288
        const val PICK_HP_ALPHA = 0.80f
    }
}
