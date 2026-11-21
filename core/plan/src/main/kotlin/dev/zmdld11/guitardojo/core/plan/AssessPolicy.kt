package dev.zmdld11.guitardojo.core.plan

/** 考核评分落到引擎输入的结果。 */
data class AssessOutcome(
    val clean: Boolean,
    val reachedTargetSpeed: Boolean,
    val selfRating: SelfRating,
)

/**
 * 考核评分 → 引擎输入的映射。
 *
 * M5 起「通过/达标」的判定来源从自评换成跟弹评分；引擎输入 [AttemptRecord]
 * 的字段语义不变，只换来源。和弦乐句等无法自动判定的场景不走这里，
 * 由调用方沿用自评路径。「中途停过」仍然一票否决。
 */
object AssessPolicy {
    fun evaluate(
        accuracy: Double,
        rhythm: Double,
        stopped: Boolean,
        bpm: Double,
        targetBpm: Double,
        rules: PlanRules = PlanRules(),
    ): AssessOutcome {
        val clean = !stopped && accuracy >= rules.assessCleanAccuracyMin && rhythm >= rules.assessCleanRhythmMin
        val reachedTarget = !stopped && bpm >= targetBpm * TARGET_SPEED_TOLERANCE
        val rating =
            when {
                stopped -> SelfRating.HARD
                accuracy >= rules.assessEasyAccuracyMin && rhythm >= rules.assessEasyRhythmMin -> SelfRating.EASY
                clean -> SelfRating.OK
                else -> SelfRating.HARD
            }
        return AssessOutcome(clean = clean, reachedTargetSpeed = reachedTarget, selfRating = rating)
    }

    /** 考核 BPM 与目标的比较允许浮点误差。 */
    private const val TARGET_SPEED_TOLERANCE = 0.99
}
