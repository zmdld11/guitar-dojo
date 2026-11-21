package dev.zmdld11.guitardojo.core.plan

/**
 * 间隔复习调度：新掌握 +1 天，之后成功复习依次 +3、+7、+14、+30 天；
 * 失败回 +1 天并降回巩固；30 天复习成功后进入维护队列（不占主练时间，按维护间隔抽查）。
 * 依据 docs/训练计划设计.md §4.3。
 */
object ReviewScheduler {
    /** 新掌握：明天复习，阶段从 0 开始。 */
    fun onMastered(todayEpochDay: Long): Pair<Long, Int> = (todayEpochDay + 1) to 0

    /**
     * 复习成功后的下一次复习日与阶段。
     * 阶段超过间隔表长度表示进入维护队列，返回维护间隔。
     */
    fun nextAfterPass(
        todayEpochDay: Long,
        stage: Int,
        intervals: List<Long> = DEFAULT_INTERVALS,
        maintenanceIntervalDays: Long = DEFAULT_MAINTENANCE_INTERVAL_DAYS,
    ): Pair<Long, Int> {
        val nextStage = stage + 1
        return if (nextStage >= intervals.size) {
            (todayEpochDay + maintenanceIntervalDays) to intervals.size
        } else {
            (todayEpochDay + intervals[nextStage]) to nextStage
        }
    }

    /** 复习失败：明天再复习，掌握度降回巩固。 */
    fun nextAfterFail(
        todayEpochDay: Long,
        intervals: List<Long> = DEFAULT_INTERVALS,
    ): Long = todayEpochDay + intervals.first()

    const val DEFAULT_MAINTENANCE_INTERVAL_DAYS: Long = 45L
    val DEFAULT_INTERVALS: List<Long> = listOf(1L, 3L, 7L, 14L, 30L)
}
