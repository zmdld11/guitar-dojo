package dev.zmdld11.guitardojo.core.plan

/**
 * 琴上日分档预算。练习按 5 分钟块拆分（块间休 1 分钟，每 20–25 分钟休 3–5 分钟）。
 * 分钟数依据 docs/M4-每日训练流设计.md §3.6；60 分钟档为周末默认。
 */
enum class SessionTier(
    val minutes: Int,
    val warmupMinutes: Int,
    val practiceBlocks: Int,
    val reviewBlocks: Int,
    val fullRunBlocks: Int,
) {
    T15(15, 3, 1, 0, 0),
    T25(25, 4, 2, 1, 0),
    T30(30, 4, 3, 1, 0),
    T60(60, 5, 3, 3, 2),
    ;

    /** 练习块计时提示休息的间隔（分钟）。 */
    val blockMinutes: Int get() = 5

    companion object {
        /** 按每日时长取档，落在两档之间时取较近的一档。 */
        fun forMinutes(minutes: Int): SessionTier =
            when {
                minutes >= T60.midpoint -> T60
                minutes >= T30.midpoint -> T30
                minutes >= T25.midpoint -> T25
                else -> T15
            }

        private val SessionTier.midpoint: Int
            get() =
                when (this) {
                    T15 -> 0
                    T25 -> (T15.minutes + T25.minutes) / 2
                    T30 -> (T25.minutes + T30.minutes) / 2
                    T60 -> (T30.minutes + T60.minutes) / 2
                }
    }
}
