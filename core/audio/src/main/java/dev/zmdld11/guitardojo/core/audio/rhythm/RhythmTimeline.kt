package dev.zmdld11.guitardojo.core.audio.rhythm

import kotlin.math.roundToLong

/**
 * 节奏关卡的时间轴：把拍间隔序列换算成音频帧。
 *
 * 时间基准与节拍器/播放引擎一致——全部按音频帧计算，不用系统时钟。
 * 第一拍前预留一拍准备时间（180° 起手转角），序列循环 repeats 遍。
 */
class RhythmTimeline(
    sampleRate: Int,
    bpm: Double,
    speed: Double,
    beats: List<Double>,
    repeats: Int,
) {
    /** 四分音符一拍的帧数（speed 已折算进去）。 */
    val framesPerQuarter: Double = sampleRate * 60.0 / (bpm * speed)

    /** 展开后的完整间隔序列。 */
    val gaps: List<Double> = List(repeats.coerceAtLeast(1)) { beats }.flatten()

    /** 每个拍点的帧号（tile 落点，全部需要点击）。tile[0] 在一拍准备时间之后。 */
    val tileFrames: List<Long> =
        buildList {
            var frame = framesPerQuarter
            add(frame.roundToLong())
            for (gap in gaps) {
                frame += gap * framesPerQuarter
                add(frame.roundToLong())
            }
        }

    /** 结束时刻：最后一个拍点之后再留一拍余韵（球转完最后一转）。 */
    val totalFrames: Long = tileFrames.last() + framesPerQuarter.roundToLong()

    fun frameFor(index: Int): Long = tileFrames[index]

    /**
     * 播放头当前所处区间：返回 (即将到达的 tile 序号, 区间内进度 0..1, 该区间转角占比 0..1)。
     * 球已越过最后一个 tile（余韵段）返回 null。
     */
    fun progressAt(headFrame: Long): TileProgress? {
        val index = tileFrames.indexOfFirst { it >= headFrame }
        if (index < 0) return null // 已越过最后一个 tile
        val previous = if (index == 0) 0L else tileFrames[index - 1]
        val span = tileFrames[index] - previous
        if (span <= 0) return TileProgress(index, 0.0, 0.0)
        val fraction = ((headFrame - previous).toDouble() / span).coerceIn(0.0, 1.0)
        // 区间 [tile[index-1], tile[index]] 的间隔是 gaps[index-1]；index==0 是开局的准备拍（gap 1.0）
        val gapFraction = if (index == 0) 1.0 else gaps[index - 1]
        return TileProgress(index, fraction, fraction * gapFraction)
    }

    data class TileProgress(
        val tileIndex: Int,
        val fraction: Double,
        val turnFraction: Double,
    )
}
