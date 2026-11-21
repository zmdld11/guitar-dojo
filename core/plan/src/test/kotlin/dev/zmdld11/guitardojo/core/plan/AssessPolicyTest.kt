package dev.zmdld11.guitardojo.core.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssessPolicyTest {
    @Test
    fun `满分干净演奏判轻松且通过`() {
        val outcome = AssessPolicy.evaluate(accuracy = 1.0, rhythm = 1.0, stopped = false, bpm = 80.0, targetBpm = 80.0)

        assertTrue(outcome.clean)
        assertTrue(outcome.reachedTargetSpeed)
        assertEquals(SelfRating.EASY, outcome.selfRating)
    }

    @Test
    fun `达标区间判刚好`() {
        val outcome = AssessPolicy.evaluate(accuracy = 0.92, rhythm = 0.88, stopped = false, bpm = 80.0, targetBpm = 80.0)

        assertTrue(outcome.clean)
        assertEquals(SelfRating.OK, outcome.selfRating)
    }

    @Test
    fun `低于阈值判不干净`() {
        val outcome = AssessPolicy.evaluate(accuracy = 0.80, rhythm = 0.80, stopped = false, bpm = 80.0, targetBpm = 80.0)

        assertFalse(outcome.clean)
        assertEquals(SelfRating.HARD, outcome.selfRating)
    }

    @Test
    fun `音准够但节奏不够仍不干净`() {
        val outcome = AssessPolicy.evaluate(accuracy = 0.95, rhythm = 0.60, stopped = false, bpm = 80.0, targetBpm = 80.0)

        assertFalse(outcome.clean)
    }

    @Test
    fun `中途停过一票否决`() {
        val outcome = AssessPolicy.evaluate(accuracy = 1.0, rhythm = 1.0, stopped = true, bpm = 80.0, targetBpm = 80.0)

        assertFalse(outcome.clean)
        assertFalse(outcome.reachedTargetSpeed)
        assertEquals(SelfRating.HARD, outcome.selfRating)
    }

    @Test
    fun `速度低于目标不计达标`() {
        val outcome = AssessPolicy.evaluate(accuracy = 0.95, rhythm = 0.95, stopped = false, bpm = 72.0, targetBpm = 80.0)

        assertFalse(outcome.reachedTargetSpeed)
        assertTrue(outcome.clean)
    }

    @Test
    fun `阈值恰好在边界时判干净`() {
        val outcome = AssessPolicy.evaluate(accuracy = 0.90, rhythm = 0.85, stopped = false, bpm = 80.0, targetBpm = 80.0)

        assertTrue(outcome.clean)
    }

    @Test
    fun `阈值可通过规则调整`() {
        val rules =
            PlanRules(
                assessCleanAccuracyMin = 1.0,
                assessCleanRhythmMin = 1.0,
            )
        val outcome = AssessPolicy.evaluate(accuracy = 0.95, rhythm = 0.95, stopped = false, bpm = 80.0, targetBpm = 80.0, rules = rules)

        // clean 阈值收紧后不再干净；评级阈值独立，仍是「轻松」
        assertFalse(outcome.clean)
        assertEquals(SelfRating.EASY, outcome.selfRating)
    }
}
