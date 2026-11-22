package dev.zmdld11.guitardojo.core.audio.rhythm

import kotlin.math.abs

/** 命中档位。 */
enum class HitGrade {
    PERFECT,
    GOOD,
    MISS,
}

/** 判定窗口（毫秒）。触屏延迟高于键盘，默认比键盘节奏游戏宽；真机验收后可调。 */
data class JudgmentWindows(
    val perfectMs: Double = 80.0,
    val goodMs: Double = 160.0,
)

/** 单次点击的判定结果：档位 + 相对拍点的偏差（正 = 晚，负 = 早）。 */
data class HitVerdict(
    val grade: HitGrade,
    val deviationMs: Double,
)

fun judge(
    hitFrame: Long,
    targetFrame: Long,
    sampleRate: Int,
    windows: JudgmentWindows = JudgmentWindows(),
): HitVerdict {
    val deviationMs = (hitFrame - targetFrame) * 1000.0 / sampleRate
    val grade =
        when {
            abs(deviationMs) <= windows.perfectMs -> HitGrade.PERFECT
            abs(deviationMs) <= windows.goodMs -> HitGrade.GOOD
            else -> HitGrade.MISS
        }
    return HitVerdict(grade, deviationMs)
}
