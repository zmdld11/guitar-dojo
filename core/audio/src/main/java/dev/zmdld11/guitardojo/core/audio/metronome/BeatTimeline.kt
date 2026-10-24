package dev.zmdld11.guitardojo.core.audio.metronome

import kotlin.math.roundToLong

/**
 * 拍点时间轴：把配置换算成帧号序列。
 *
 * 纯计算、无 Android 依赖，可直接单测。帧号从构造时的 [anchorFrame] 起算，
 * 所有拍点都落在采样点上，因此不会随时间累积浮点误差。
 */
class BeatTimeline(
    private val sampleRate: Int,
    initialConfig: MetronomeConfig,
    anchorFrame: Long = 0L,
    anchorBeatInBar: Int = 0,
) {
    var config: MetronomeConfig = initialConfig
        private set

    private var framesPerSubdivision: Double = framesPerSubdivision(initialConfig)
    private var anchorFrame: Long = anchorFrame
    private var anchorBeatInBar: Int = anchorBeatInBar
    private var nextIndex: Long = 0L

    /** 下一个尚未取出的拍点。 */
    fun peek(): BeatPulse = pulseAt(nextIndex)

    /** 取出并推进到下一个拍点。 */
    fun advance(): BeatPulse {
        val pulse = pulseAt(nextIndex)
        nextIndex++
        return pulse
    }

    /** 取出所有帧号早于 [endFrameExclusive] 的拍点。 */
    fun drainsBefore(endFrameExclusive: Long): List<BeatPulse> {
        if (peek().frame >= endFrameExclusive) return emptyList()
        val result = ArrayList<BeatPulse>(4)
        while (true) {
            val pulse = peek()
            if (pulse.frame >= endFrameExclusive) break
            result += advance()
        }
        return result
    }

    /**
     * 应用新配置，但保持下一个拍点的时刻不变，避免拖动 BPM 时连续重触发。
     * 新速度从下一个拍点之后开始生效。
     */
    fun reconfigure(newConfig: MetronomeConfig) {
        val next = peek()
        config = newConfig
        framesPerSubdivision = framesPerSubdivision(newConfig)
        anchorFrame = next.frame
        anchorBeatInBar = next.beatInBar.coerceIn(0, newConfig.beatsPerBar - 1)
        nextIndex = 0L
    }

    /** 第 [index] 个拍点的帧号，供测试与校验使用。 */
    fun frameFor(index: Long): Long = anchorFrame + (index * framesPerSubdivision).roundToLong()

    private fun pulseAt(index: Long): BeatPulse {
        val mainBeatOffset = index / config.subdivisions
        val subIndex = (index % config.subdivisions).toInt()
        val beatInBar = ((anchorBeatInBar + mainBeatOffset) % config.beatsPerBar).toInt()
        return BeatPulse(
            frame = frameFor(index),
            beatInBar = beatInBar,
            subIndex = subIndex,
            isDownbeat = beatInBar == 0 && subIndex == 0,
        )
    }

    private fun framesPerSubdivision(config: MetronomeConfig): Double = sampleRate * SECONDS_PER_MINUTE / config.bpm / config.subdivisions

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
    }
}
