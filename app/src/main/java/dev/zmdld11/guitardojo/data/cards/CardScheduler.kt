package dev.zmdld11.guitardojo.data.cards

/** 一张卡的复习进度。`box` 是成功复习次数，决定下一次间隔。 */
data class CardProgress(
    val cardId: String,
    val box: Int = 0,
    val nextReviewEpochDay: Long = 0L,
    val lastReviewedEpochDay: Long = 0L,
) {
    val reviewed: Boolean get() = lastReviewedEpochDay > 0L
}

/**
 * 卡片间隔调度：新卡先看，记住后按 1/3/7/14/30 天复习，没记住回到 1 天后。
 *
 * 与 M4 计划引擎共用同一套间隔思想，但卡片不参与乐句掌握度计算。
 */
class CardScheduler(
    private val intervalDays: List<Int> = DEFAULT_INTERVALS,
) {
    init {
        require(intervalDays.isNotEmpty()) { "复习间隔不能为空" }
        require(intervalDays.all { it > 0 }) { "复习间隔必须为正数" }
    }

    fun review(
        progress: CardProgress?,
        cardId: String,
        todayEpochDay: Long,
        remembered: Boolean,
    ): CardProgress {
        // 同一天重复自评不推进间隔，避免「再看一遍」把复习计划刷到很远的未来。
        if (progress != null && progress.lastReviewedEpochDay == todayEpochDay) return progress
        if (!remembered) {
            return CardProgress(
                cardId = cardId,
                box = 0,
                nextReviewEpochDay = todayEpochDay + 1,
                lastReviewedEpochDay = todayEpochDay,
            )
        }

        val currentBox = progress?.box ?: 0
        val nextBox = (currentBox + 1).coerceAtMost(intervalDays.size)
        return CardProgress(
            cardId = cardId,
            box = nextBox,
            nextReviewEpochDay = todayEpochDay + intervalDays[nextBox - 1],
            lastReviewedEpochDay = todayEpochDay,
        )
    }

    fun isNew(progress: CardProgress?): Boolean = progress == null || !progress.reviewed

    fun isDue(
        progress: CardProgress?,
        todayEpochDay: Long,
    ): Boolean = progress != null && progress.reviewed && progress.nextReviewEpochDay <= todayEpochDay

    fun isMastered(progress: CardProgress?): Boolean = progress != null && progress.box >= intervalDays.size

    /**
     * 今日队列：到期卡 → 新卡 → 未到期卡（按下次复习日排序）。
     *
     * 输入顺序作为同组内的稳定顺序，保证同一个牌组每次打开顺序一致。
     */
    fun order(
        cards: List<TrainingCard>,
        progress: Map<String, CardProgress>,
        todayEpochDay: Long,
    ): List<TrainingCard> =
        cards.sortedWith(
            compareBy<TrainingCard> { card ->
                val item = progress[card.id]
                when {
                    isDue(item, todayEpochDay) -> 0
                    isNew(item) -> 1
                    else -> 2
                }
            }.thenBy { card -> progress[card.id]?.nextReviewEpochDay ?: 0L },
        )

    fun daily(
        cards: List<TrainingCard>,
        progress: Map<String, CardProgress>,
        todayEpochDay: Long,
    ): TrainingCard? = order(cards, progress, todayEpochDay).firstOrNull()

    companion object {
        val DEFAULT_INTERVALS: List<Int> = listOf(1, 3, 7, 14, 30)
    }
}
