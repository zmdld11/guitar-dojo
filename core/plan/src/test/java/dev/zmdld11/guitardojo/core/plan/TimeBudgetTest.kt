package dev.zmdld11.guitardojo.core.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeBudgetTest {
    @Test
    fun `时长映射到最近的一档`() {
        assertEquals(SessionTier.T15, SessionTier.forMinutes(15))
        assertEquals(SessionTier.T15, SessionTier.forMinutes(18))
        assertEquals(SessionTier.T25, SessionTier.forMinutes(20))
        assertEquals(SessionTier.T25, SessionTier.forMinutes(25))
        assertEquals(SessionTier.T30, SessionTier.forMinutes(27))
        assertEquals(SessionTier.T30, SessionTier.forMinutes(30))
        assertEquals(SessionTier.T60, SessionTier.forMinutes(45))
        assertEquals(SessionTier.T60, SessionTier.forMinutes(60))
        assertEquals(SessionTier.T60, SessionTier.forMinutes(120))
    }

    @Test
    fun `60 分钟档按 5 分钟块拆分主练与复习`() {
        assertEquals(3, SessionTier.T60.practiceBlocks)
        assertEquals(3, SessionTier.T60.reviewBlocks)
        assertEquals(2, SessionTier.T60.fullRunBlocks)
        assertEquals(5, SessionTier.T60.blockMinutes)
    }

    @Test
    fun `15 分钟档不排复习与完整跑`() {
        assertEquals(0, SessionTier.T15.reviewBlocks)
        assertEquals(0, SessionTier.T15.fullRunBlocks)
    }
}
