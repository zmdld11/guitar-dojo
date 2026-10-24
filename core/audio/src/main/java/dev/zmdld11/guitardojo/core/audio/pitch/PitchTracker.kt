package dev.zmdld11.guitardojo.core.audio.pitch

/**
 * 时间维度的平滑：中值滤波 + 静音复位。
 *
 * 单帧检测难免抖一两个半音；调音器要的是「稳住不跳」，
 * 所以在展示前把最近若干帧的中值作为结果。
 */
class PitchTracker(
    // 电吉他失真音色的 NSDF 峰值常在 0.75-0.9，阈值过高会长时间不显示读数
    private val minClarity: Double = 0.75,
    private val windowSize: Int = 5,
    private val minFramesForResult: Int = 3,
    private val silentFramesToReset: Int = 5,
) {
    private val history = ArrayDeque<PitchResult>()
    private var silentFrames = 0

    fun reset() {
        history.clear()
        silentFrames = 0
    }

    /** 喂入一帧检测结果，返回平滑后的可展示结果；尚不稳定时返回 null。 */
    fun accept(result: PitchResult?): PitchResult? {
        if (result == null || result.clarity < minClarity) {
            silentFrames++
            if (silentFrames >= silentFramesToReset) history.clear()
            return null
        }
        silentFrames = 0
        history.addLast(result)
        while (history.size > windowSize) history.removeFirst()

        if (history.size < minFramesForResult) return null

        val frequencies = history.map { it.frequencyHz }.sorted()
        val clarities = history.map { it.clarity }.sorted()
        return PitchResult(
            frequencyHz = frequencies[frequencies.size / 2],
            clarity = clarities[clarities.size / 2],
        )
    }
}
