package dev.zmdld11.guitardojo.core.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressEngineTest {
    private val engine = ProgressEngine()

    @Test
    fun `初始进度从目标速度的 50 percent 起步`() {
        val progress = engine.initialProgress("a", 80.0)

        assertEquals(40.0, progress.currentBpm, 1e-9)
        assertEquals(Mastery.LEARNING, progress.mastery)
    }

    @Test
    fun `一次不干净记为失败并清空连续干净计数`() {
        val base = progress(currentBpm = 60.0, cleanStreak = 2)

        val next = engine.apply(base, attempt(epochDay = 100, clean = false))

        assertEquals(0, next.cleanStreak)
        assertEquals(1, next.consecutiveFails)
        assertEquals(1, next.failDays)
        assertEquals(100L, next.lastFailEpochDay)
    }

    @Test
    fun `连续 2 次失败降 10 BPM`() {
        var next = progress(currentBpm = 60.0)
        next = engine.apply(next, attempt(epochDay = 100, clean = false))
        next = engine.apply(next, attempt(epochDay = 100, clean = false))

        assertEquals(50.0, next.currentBpm, 1e-9)
    }

    @Test
    fun `降速下限为目标速度的 50 percent`() {
        var next = progress(currentBpm = 42.0, targetBpm = 80.0)
        next = engine.apply(next, attempt(epochDay = 100, clean = false))
        next = engine.apply(next, attempt(epochDay = 100, clean = false))

        assertEquals(40.0, next.currentBpm, 1e-9)
    }

    @Test
    fun `第 3 个训练日失败额外降 10 percent 并标记换句`() {
        var next = progress(currentBpm = 70.0)
        next = engine.apply(next, attempt(epochDay = 100, clean = false))
        next = engine.apply(next, attempt(epochDay = 101, clean = false))
        val before = next.currentBpm

        next = engine.apply(next, attempt(epochDay = 102, clean = false))

        assertEquals(3, next.failDays)
        assertTrue(next.switchPending)
        // 第 3 个失败日：先走常规降速再乘 0.9，且只乘这一次
        assertEquals((before - 10.0) * 0.9, next.currentBpm, 1e-9)
        val again = engine.apply(next, attempt(epochDay = 103, clean = false))
        // 第 4 个失败日只走常规降速，触到目标速度 50% 的下限
        assertEquals(40.0, again.currentBpm, 1e-9)
    }

    @Test
    fun `3 次干净完成当日达标，同一天不重复计`() {
        val day = 100L
        var next = progress()
        repeat(3) { next = engine.apply(next, attempt(epochDay = day, clean = true, rating = SelfRating.EASY)) }

        assertEquals(1, next.passDays)
        assertEquals(0, next.cleanStreak)

        repeat(3) { next = engine.apply(next, attempt(epochDay = day, clean = true, rating = SelfRating.EASY)) }
        assertEquals(1, next.passDays)
    }

    @Test
    fun `自评 HARD 且未达速度按失败处理`() {
        val next = engine.apply(progress(), attempt(epochDay = 100, clean = true, rating = SelfRating.HARD, reached = false))

        assertEquals(1, next.consecutiveFails)
        assertEquals(0, next.cleanStreak)
    }

    @Test
    fun `干净但自评 HARD 不完成当日达标`() {
        val day = 100L
        var next = progress()
        repeat(3) {
            next = engine.apply(next, attempt(epochDay = day, clean = true, rating = SelfRating.HARD, reached = true))
        }

        assertEquals(0, next.passDays)
        assertEquals(0, next.cleanStreak)
        assertEquals(0, next.consecutiveFails)
    }

    @Test
    fun `连续 2 个训练日达标提速 5 BPM`() {
        var next = progress(currentBpm = 45.0)
        repeat(3) { next = engine.apply(next, attempt(epochDay = 100, clean = true, rating = SelfRating.EASY)) }
        repeat(3) { next = engine.apply(next, attempt(epochDay = 101, clean = true, rating = SelfRating.EASY)) }

        assertEquals(50.0, next.currentBpm, 1e-9)
        assertEquals(2, next.passDays)
    }

    @Test
    fun `提速上限为目标速度的 110 percent`() {
        var next = progress(currentBpm = 86.0, targetBpm = 80.0)
        repeat(3) { next = engine.apply(next, attempt(epochDay = 100, clean = true, rating = SelfRating.EASY)) }
        repeat(3) { next = engine.apply(next, attempt(epochDay = 101, clean = true, rating = SelfRating.EASY)) }

        assertEquals(88.0, next.currentBpm, 1e-9)
    }

    @Test
    fun `失败中断提速连击`() {
        var next = progress(currentBpm = 45.0)
        repeat(3) { next = engine.apply(next, attempt(epochDay = 100, clean = true, rating = SelfRating.EASY)) }
        next = engine.apply(next, attempt(epochDay = 101, clean = false))
        repeat(3) { next = engine.apply(next, attempt(epochDay = 102, clean = true, rating = SelfRating.EASY)) }
        next = engine.apply(next, attempt(epochDay = 102, clean = false))
        repeat(3) { next = engine.apply(next, attempt(epochDay = 103, clean = true, rating = SelfRating.EASY)) }
        repeat(3) { next = engine.apply(next, attempt(epochDay = 104, clean = true, rating = SelfRating.EASY)) }

        // 达标日 4 个，但中间隔了失败日，只提速一次
        assertEquals(4, next.passDays)
        assertEquals(50.0, next.currentBpm, 1e-9)
    }

    @Test
    fun `3 个不同训练日达标进入掌握并次日复习`() {
        var next = progress()
        repeat(3) { next = engine.apply(next, attempt(epochDay = 100, clean = true, rating = SelfRating.EASY)) }
        repeat(3) { next = engine.apply(next, attempt(epochDay = 101, clean = true, rating = SelfRating.EASY)) }
        repeat(3) { next = engine.apply(next, attempt(epochDay = 103, clean = true, rating = SelfRating.EASY)) }

        assertEquals(Mastery.MASTERED, next.mastery)
        assertEquals(104L, next.nextReviewEpochDay)
        assertEquals(0, next.reviewStage)
        assertFalse(next.inMaintenance)
    }

    @Test
    fun `复习成功按 1、3、7、14、30 推进后进入维护`() {
        var next = progress(mastery = Mastery.MASTERED, nextReviewEpochDay = 101, reviewStage = 0)
        // +1 天的复习成功 → +3
        next = engine.apply(next, review(epochDay = 101, clean = true))
        assertEquals(104L, next.nextReviewEpochDay)
        assertEquals(1, next.reviewStage)
        // +3 → +7
        next = engine.apply(next, review(epochDay = 104, clean = true))
        assertEquals(111L, next.nextReviewEpochDay)
        // +7 → +14
        next = engine.apply(next, review(epochDay = 111, clean = true))
        assertEquals(125L, next.nextReviewEpochDay)
        // +14 → +30
        next = engine.apply(next, review(epochDay = 125, clean = true))
        assertEquals(155L, next.nextReviewEpochDay)
        assertEquals(4, next.reviewStage)
        // +30 → 维护队列
        next = engine.apply(next, review(epochDay = 155, clean = true))
        assertTrue(next.inMaintenance)
        assertEquals(200L, next.nextReviewEpochDay)
    }

    @Test
    fun `复习失败回 1 天并降回巩固`() {
        var next = progress(mastery = Mastery.MASTERED, nextReviewEpochDay = 101, reviewStage = 2)

        next = engine.apply(next, review(epochDay = 101, clean = false))

        assertEquals(Mastery.CONSOLIDATING, next.mastery)
        assertEquals(102L, next.nextReviewEpochDay)
        assertEquals(0, next.reviewStage)
    }

    @Test
    fun `attempt 与 progress 的乐句不一致时报错`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.apply(progress(phraseId = "a"), attempt(epochDay = 100, clean = true).copy(phraseId = "b"))
        }
    }

    // ---- fixtures ----

    private fun progress(
        phraseId: String = "a",
        currentBpm: Double = 40.0,
        targetBpm: Double = 80.0,
        mastery: Mastery = Mastery.LEARNING,
        nextReviewEpochDay: Long? = null,
        reviewStage: Int = 0,
        cleanStreak: Int = 0,
    ): PhraseProgress =
        PhraseProgress(
            phraseId = phraseId,
            currentBpm = currentBpm,
            targetBpm = targetBpm,
            mastery = mastery,
            nextReviewEpochDay = nextReviewEpochDay,
            reviewStage = reviewStage,
            cleanStreak = cleanStreak,
        )

    private fun attempt(
        epochDay: Long,
        clean: Boolean,
        rating: SelfRating = SelfRating.OK,
        reached: Boolean = false,
    ): AttemptRecord =
        AttemptRecord(
            phraseId = "a",
            epochDay = epochDay,
            bpm = 60.0,
            clean = clean,
            selfRating = rating,
            reachedTargetSpeed = reached,
        )

    private fun review(
        epochDay: Long,
        clean: Boolean,
    ): AttemptRecord = attempt(epochDay, clean).copy(kind = AttemptKind.REVIEW)
}
