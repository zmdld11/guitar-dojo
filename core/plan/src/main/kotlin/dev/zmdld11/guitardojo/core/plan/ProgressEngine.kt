package dev.zmdld11.guitardojo.core.plan

/**
 * 速度与掌握度状态机，纯函数：输入当前状态与一次尝试，输出新状态，不写库。
 * 判据见 docs/M4-每日训练流设计.md §3.4：
 * - 失败 = 一次不干净，或自评 HARD 且未达目标速度
 * - 当日达标 = 同一速度连续 3 次干净完成且自评非 HARD，跨训练日各记 1 次
 * - 提速 = 连续 2 个训练日达标 +5 BPM（上限目标 ×110%）
 * - 降速 = 连续 2 次失败 −10 BPM（下限目标 ×50%）
 * - 换句/拆句 = 连续 3 个训练日失败（当日引擎额外降 10%，生成器据此换句或拆小节）
 * - 掌握 = 3 个不同训练日达标 → 进入间隔复习
 */
class ProgressEngine(
    private val rules: PlanRules = PlanRules(),
) {
    /** 首次练习前的初始状态：从目标速度 50% 起步。 */
    fun initialProgress(
        phraseId: String,
        targetBpm: Double,
    ): PhraseProgress =
        PhraseProgress(
            phraseId = phraseId,
            currentBpm = (targetBpm * rules.startSpeedRatio),
            targetBpm = targetBpm,
        )

    fun apply(
        progress: PhraseProgress,
        attempt: AttemptRecord,
    ): PhraseProgress {
        require(attempt.phraseId == progress.phraseId) {
            "attempt.phraseId(${attempt.phraseId}) != progress.phraseId(${progress.phraseId})"
        }
        return when (attempt.kind) {
            AttemptKind.REVIEW -> applyReview(progress, attempt)
            AttemptKind.PRACTICE -> applyPractice(progress, attempt)
        }
    }

    private fun applyPractice(
        progress: PhraseProgress,
        attempt: AttemptRecord,
    ): PhraseProgress {
        val failed = !attempt.clean || (attempt.selfRating == SelfRating.HARD && !attempt.reachedTargetSpeed)
        val practiced = progress.copy(lastPracticedEpochDay = attempt.epochDay)
        if (failed) return onFail(practiced, attempt.epochDay)
        return onClean(practiced, attempt)
    }

    private fun onFail(
        progress: PhraseProgress,
        epochDay: Long,
    ): PhraseProgress {
        val fails = progress.consecutiveFails + 1
        val failDays = if (progress.lastFailEpochDay == epochDay) progress.failDays else progress.failDays + 1
        var bpm = progress.currentBpm
        if (fails >= rules.failAttemptsPerSpeedDown) {
            bpm = (bpm - rules.speedDownBpm).coerceAtLeast(progress.targetBpm * rules.speedDownFloorRatio)
        }
        if (failDays == rules.failDaysPerSwitch) {
            // 换句/拆句当日的额外降速在这里落地，生成器不再乘，避免每天重复降
            bpm = (bpm * rules.switchSpeedDownRatio).coerceAtLeast(progress.targetBpm * rules.speedDownFloorRatio)
        }
        return progress.copy(
            cleanStreak = 0,
            consecutiveFails = fails,
            failDays = failDays,
            lastFailEpochDay = epochDay,
            // 失败中断「连续训练日达标」的计数
            speedUpMarker = progress.passDays,
            currentBpm = bpm,
        )
    }

    private fun onClean(
        progress: PhraseProgress,
        attempt: AttemptRecord,
    ): PhraseProgress {
        val streak = progress.cleanStreak + 1
        val cleaned = progress.copy(cleanStreak = streak, consecutiveFails = 0)
        if (streak < rules.cleanPerPass) return cleaned
        // 自评 HARD 不能完成当日达标，重开一组
        if (attempt.selfRating == SelfRating.HARD) return cleaned.copy(cleanStreak = 0)
        // 当日达标同一天只记一次；之后重新计一组
        if (progress.lastPassEpochDay == attempt.epochDay) return cleaned.copy(cleanStreak = 0)
        return onPassDay(cleaned, attempt.epochDay)
    }

    private fun onPassDay(
        progress: PhraseProgress,
        epochDay: Long,
    ): PhraseProgress {
        val passDays = progress.passDays + 1
        var next =
            progress.copy(
                passDays = passDays,
                lastPassEpochDay = epochDay,
                failDays = 0,
                cleanStreak = 0,
            )
        if (next.mastery != Mastery.MASTERED && passDays >= rules.passDaysToMaster) {
            val (reviewDay, stage) = ReviewScheduler.onMastered(epochDay)
            next =
                next.copy(
                    mastery = Mastery.MASTERED,
                    nextReviewEpochDay = reviewDay,
                    reviewStage = stage,
                )
        }
        // 提速：连续 2 个训练日达标（期间无失败）
        if (passDays - progress.speedUpMarker >= rules.passDaysPerSpeedUp) {
            val cap = progress.targetBpm * rules.speedUpCapRatio
            next =
                next.copy(
                    currentBpm = (next.currentBpm + rules.speedUpBpm).coerceAtMost(cap),
                    speedUpMarker = passDays,
                )
        }
        return next
    }

    private fun applyReview(
        progress: PhraseProgress,
        attempt: AttemptRecord,
    ): PhraseProgress {
        val practiced = progress.copy(lastPracticedEpochDay = attempt.epochDay)
        val passed = attempt.clean && attempt.selfRating != SelfRating.HARD
        return if (passed) {
            val (nextReview, stage) =
                ReviewScheduler.nextAfterPass(
                    attempt.epochDay,
                    practiced.reviewStage,
                    rules.reviewIntervals,
                    rules.maintenanceIntervalDays,
                )
            practiced.copy(
                nextReviewEpochDay = nextReview,
                reviewStage = stage,
                inMaintenance = stage >= rules.reviewIntervals.size,
            )
        } else {
            practiced.copy(
                nextReviewEpochDay = ReviewScheduler.nextAfterFail(attempt.epochDay, rules.reviewIntervals),
                reviewStage = 0,
                mastery = Mastery.CONSOLIDATING,
            )
        }
    }
}
