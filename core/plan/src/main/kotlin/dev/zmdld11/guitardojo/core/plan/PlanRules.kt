package dev.zmdld11.guitardojo.core.plan

/**
 * 规则引擎可调参数。默认值来自 docs/训练计划设计.md §2.8（教学惯例与运动学习原则的启发式，
 * 不是实验阈值），实际体验不满意时在这里调整，不动规则逻辑。
 */
data class PlanRules(
    /** 当日达标需要的一次连续干净组长度。 */
    val cleanPerPass: Int = 3,
    /** 连续多少个训练日达标后提速。 */
    val passDaysPerSpeedUp: Int = 2,
    val speedUpBpm: Double = 5.0,
    /** 提速上限 = 目标速度 × 该倍率，避免把练习变成赛跑。 */
    val speedUpCapRatio: Double = 1.10,
    /** 连续多少次失败后降速。 */
    val failAttemptsPerSpeedDown: Int = 2,
    val speedDownBpm: Double = 10.0,
    /** 降速下限 = 目标速度 × 该倍率。 */
    val speedDownFloorRatio: Double = 0.50,
    /** 连续多少个训练日失败后换句/拆句。 */
    val failDaysPerSwitch: Int = 3,
    /** 换句/拆句触发当日的额外降速比例（引擎在第 3 个失败日落地，避免生成器重复乘）。 */
    val switchSpeedDownRatio: Double = 0.90,
    /** 昨天首次失败（引擎尚未降速）时，今日计划的额外降速比例。 */
    val failSessionSpeedRatio: Double = 0.90,
    /** 掌握需要的达标训练日数（不同天）。 */
    val passDaysToMaster: Int = 3,
    val reviewIntervals: List<Long> = ReviewScheduler.DEFAULT_INTERVALS,
    val maintenanceIntervalDays: Long = ReviewScheduler.DEFAULT_MAINTENANCE_INTERVAL_DAYS,
    /** 首次练习从目标速度的该比例起步。 */
    val startSpeedRatio: Double = 0.50,
    /** 热身/主练默认循环小节长度。 */
    val loopBarCount: Int = 2,
    /** 考核判「干净」需要的最低音准得分（0-1）。 */
    val assessCleanAccuracyMin: Double = 0.90,
    /** 考核判「干净」需要的最低节奏得分（0-1）。 */
    val assessCleanRhythmMin: Double = 0.85,
    /** 考核判「轻松」需要的最低音准得分。 */
    val assessEasyAccuracyMin: Double = 0.95,
    /** 考核判「轻松」需要的最低节奏得分。 */
    val assessEasyRhythmMin: Double = 0.90,
)
