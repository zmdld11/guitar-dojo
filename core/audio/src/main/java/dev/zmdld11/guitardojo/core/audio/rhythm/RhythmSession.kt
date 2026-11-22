package dev.zmdld11.guitardojo.core.audio.rhythm

import kotlin.math.abs

/** 引擎发给 UI 的对局事件。 */
sealed interface RhythmEvent {
    /** 播放头越过了没点的 tile：球滑过去，记一次 miss。 */
    data class TileMissed(
        val tileIndex: Int,
    ) : RhythmEvent

    /** 全部 tile 点完，正常走完。 */
    data object Finished : RhythmEvent

    /** 连续 miss 达到上限，对局失败。 */
    data object Failed : RhythmEvent
}

/** 一局结算。accuracy = (PERFECT + GOOD) / totalTiles。 */
data class RhythmStats(
    val totalTiles: Int,
    val perfectCount: Int,
    val goodCount: Int,
    val missCount: Int,
    val accuracy: Double,
    /** 命中偏差的代数平均（正 = 整体偏晚，负 = 整体偏早）。 */
    val meanDeviationMs: Double,
    /** 命中偏差的绝对平均（手感松紧）。 */
    val meanAbsDeviationMs: Double,
)

/**
 * 一局节奏游戏的可测内核：判定推进、连击 miss、结束判定。
 * 不碰任何平台 API；[RhythmEngine] 的音频线程与点击线程都是它的胶水。
 */
class RhythmSession(
    val timeline: RhythmTimeline,
    private val sampleRate: Int,
    private val windows: JudgmentWindows = JudgmentWindows(),
    private val maxConsecutiveMisses: Int = MAX_CONSECUTIVE_MISSES,
) {
    private val lock = Any()

    /** 下一块待点的 tile；命中推进，miss（点空或越过）也推进（球滑过去）。 */
    var nextTileIndex: Int = 0
        private set

    var failed: Boolean = false
        private set
    var finished: Boolean = false
        private set

    private var perfectCount = 0
    private var goodCount = 0
    private var missCount = 0
    private var consecutiveMisses = 0
    private var deviationSumMs = 0.0
    private var deviationAbsSumMs = 0.0
    private var hitCount = 0

    /** hit() 在点击线程触发的失败事件，等轮询线程取走。 */
    private val pendingEvents = ArrayDeque<RhythmEvent>()

    /** 玩家点击：以播放头判定下一块 tile。命中推进并返回判定；点空不推进。 */
    fun hit(headFrame: Long): HitVerdict? {
        if (finished || failed) return null
        if (nextTileIndex >= timeline.tileFrames.size) return null
        val verdict = judge(headFrame, timeline.frameFor(nextTileIndex), sampleRate, windows)
        synchronized(lock) {
            when (verdict.grade) {
                HitGrade.PERFECT -> perfectCount++
                HitGrade.GOOD -> goodCount++
                HitGrade.MISS -> {
                    missCount++
                    consecutiveMisses++
                    if (consecutiveMisses >= maxConsecutiveMisses) {
                        failed = true
                        pendingEvents += RhythmEvent.Failed
                    }
                }
            }
            if (verdict.grade != HitGrade.MISS) {
                consecutiveMisses = 0
                deviationSumMs += verdict.deviationMs
                deviationAbsSumMs += abs(verdict.deviationMs)
                hitCount++
                nextTileIndex++
            }
        }
        return verdict
    }

    /** 取走 hit() 侧排队的事件（点击线程触发的失败等）。 */
    fun drainEvents(): List<RhythmEvent> =
        synchronized(lock) {
            val events = pendingEvents.toList()
            pendingEvents.clear()
            events
        }

    /**
     * 播放头推进（轮询线程调用）：越过未点的 tile 记 miss；
     * 连续 miss 达上限发 [RhythmEvent.Failed]，全部点完且越过分隔余韵发 [RhythmEvent.Finished]。
     */
    fun advance(headFrame: Long): List<RhythmEvent> {
        if (finished || failed) return emptyList()
        val events = mutableListOf<RhythmEvent>()
        events += drainEvents()
        while (nextTileIndex < timeline.tileFrames.size &&
            headFrame > timeline.frameFor(nextTileIndex) + missThresholdFrames()
        ) {
            synchronized(lock) {
                missCount++
                consecutiveMisses++
                if (consecutiveMisses >= maxConsecutiveMisses) failed = true
            }
            events += RhythmEvent.TileMissed(nextTileIndex)
            nextTileIndex++
        }
        if (failed) {
            events += RhythmEvent.Failed
            return events
        }
        if (nextTileIndex >= timeline.tileFrames.size && headFrame >= timeline.totalFrames) {
            finished = true
            events += RhythmEvent.Finished
        }
        return events
    }

    fun stats(): RhythmStats =
        synchronized(lock) {
            val total = timeline.tileFrames.size
            RhythmStats(
                totalTiles = total,
                perfectCount = perfectCount,
                goodCount = goodCount,
                missCount = missCount,
                accuracy = if (total == 0) 0.0 else (perfectCount + goodCount).toDouble() / total,
                meanDeviationMs = if (hitCount == 0) 0.0 else deviationSumMs / hitCount,
                meanAbsDeviationMs = if (hitCount == 0) 0.0 else deviationAbsSumMs / hitCount,
            )
        }

    private fun missThresholdFrames(): Long = (windows.goodMs * sampleRate / 1000.0).toLong()

    companion object {
        const val MAX_CONSECUTIVE_MISSES = 3
    }
}
