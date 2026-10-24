package dev.zmdld11.guitardojo.core.audio.pitch

/**
 * 一次音高检测结果。
 *
 * @property frequencyHz 基频，单位 Hz。
 * @property clarity 置信度，0-1。越高越可信；低于阈值的结果不应展示给用户。
 */
data class PitchResult(
    val frequencyHz: Double,
    val clarity: Double,
)

/** 单帧音高检测器。实现必须是纯计算，方便用合成信号做回归测试。 */
interface PitchDetector {
    /** 帧长（采样点）。每帧必须是这个长度。 */
    val windowSize: Int

    /** 检测不到有效基频时返回 null。 */
    fun detect(samples: FloatArray): PitchResult?
}
