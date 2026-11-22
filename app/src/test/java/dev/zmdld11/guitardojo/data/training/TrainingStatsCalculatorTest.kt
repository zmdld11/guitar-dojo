package dev.zmdld11.guitardojo.data.training

import dev.zmdld11.guitardojo.core.plan.DayKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 打卡口径：琴上日 ≥5 分钟算有效练习，无琴日完成即算。 */
class TrainingStatsCalculatorTest {
    private fun entity(
        kind: DayKind,
        practiceSeconds: Long,
    ): TrainingDayEntity =
        TrainingDayEntity(
            epochDay = 0L,
            kind = kind.name,
            planJson = "[]",
            currentStep = 0,
            completedSteps = "",
            practiceSeconds = practiceSeconds,
            tuningPassed = true,
            note = "",
            startedAtMillis = null,
            finishedAtMillis = null,
        )

    @Test
    fun `琴上日不足5分钟不算有效练习`() {
        assertFalse(isValidSession(entity(DayKind.GUITAR, practiceSeconds = 0L)))
        assertFalse(isValidSession(entity(DayKind.GUITAR, practiceSeconds = VALID_SESSION_SECONDS - 1)))
    }

    @Test
    fun `琴上日满5分钟算有效练习`() {
        assertTrue(isValidSession(entity(DayKind.GUITAR, practiceSeconds = VALID_SESSION_SECONDS)))
        assertTrue(isValidSession(entity(DayKind.GUITAR, practiceSeconds = 3600L)))
    }

    @Test
    fun `无琴日完成即算有效练习`() {
        assertTrue(isValidSession(entity(DayKind.NO_GUITAR, practiceSeconds = 0L)))
    }
}
