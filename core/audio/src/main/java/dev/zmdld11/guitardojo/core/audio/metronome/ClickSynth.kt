package dev.zmdld11.guitardojo.core.audio.metronome

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 合成节拍器的咔哒声：正弦波 + 指数衰减包络，带极短起音避免直流爆音。
 * 不使用音频素材文件，省掉 assets 与版权问题。
 */
object ClickSynth {
    /**
     * @param sampleRate 采样率。
     * @param frequencyHz 基频，越高越「尖」。
     * @param durationMillis 时长，单位毫秒。
     * @param amplitude 峰值，0-1。
     * @param decayRatio 衰减速度，越小衰减越快。
     */
    fun generate(
        sampleRate: Int,
        frequencyHz: Double,
        durationMillis: Int,
        amplitude: Double,
        decayRatio: Double = 0.22,
    ): ShortArray {
        require(sampleRate > 0) { "采样率必须为正" }
        require(durationMillis > 0) { "时长必须为正" }
        require(amplitude in 0.0..1.0) { "振幅必须在 0-1 之间" }

        val frameCount = sampleRate * durationMillis / 1000
        val samples = ShortArray(frameCount)
        val attackFrames = (sampleRate * ATTACK_MILLIS / 1000.0).coerceAtLeast(1.0)
        val decayTau = frameCount * decayRatio

        for (i in 0 until frameCount) {
            val attack = (i / attackFrames).coerceAtMost(1.0)
            val envelope = attack * exp(-i / decayTau)
            val wave = sin(2.0 * PI * frequencyHz * i / sampleRate)
            samples[i] = (wave * envelope * amplitude * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private const val ATTACK_MILLIS = 1.0
}
