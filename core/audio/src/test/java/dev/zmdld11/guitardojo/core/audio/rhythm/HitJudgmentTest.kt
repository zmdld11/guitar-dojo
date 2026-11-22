package dev.zmdld11.guitardojo.core.audio.rhythm

import org.junit.Assert.assertEquals
import org.junit.Test

class HitJudgmentTest {
    private val sampleRate = 48_000

    @Test
    fun `80ms 内算 PERFECT`() {
        val onTime = judge(hitFrame = 36_000L, targetFrame = 36_000L, sampleRate = sampleRate)
        assertEquals(HitGrade.PERFECT, onTime.grade)
        assertEquals(0.0, onTime.deviationMs, 1e-9)

        val edge = judge(hitFrame = 36_000L + 3_840L, targetFrame = 36_000L, sampleRate = sampleRate)
        assertEquals(HitGrade.PERFECT, edge.grade)
        assertEquals(80.0, edge.deviationMs, 1e-6)
    }

    @Test
    fun `160ms 内算 GOOD，再往外是 MISS`() {
        assertEquals(HitGrade.GOOD, judge(36_000L + 7_000L, 36_000L, sampleRate).grade)
        assertEquals(HitGrade.GOOD, judge(36_000L - 7_680L, 36_000L, sampleRate).grade)
        assertEquals(HitGrade.MISS, judge(36_000L + 8_000L, 36_000L, sampleRate).grade)
        assertEquals(HitGrade.MISS, judge(36_000L - 9_000L, 36_000L, sampleRate).grade)
    }

    @Test
    fun `偏差带方向，正晚负早`() {
        val late = judge(36_000L + 3_840L, 36_000L, sampleRate)
        assertEquals(80.0, late.deviationMs, 1e-6)
        val early = judge(36_000L - 3_840L, 36_000L, sampleRate)
        assertEquals(-80.0, early.deviationMs, 1e-6)
    }

    @Test
    fun `窗口可调`() {
        val lenient = JudgmentWindows(perfectMs = 120.0, goodMs = 220.0)
        assertEquals(HitGrade.PERFECT, judge(36_000L + 5_760L, 36_000L, sampleRate, lenient).grade)
        assertEquals(HitGrade.GOOD, judge(36_000L + 10_000L, 36_000L, sampleRate, lenient).grade)
    }
}
