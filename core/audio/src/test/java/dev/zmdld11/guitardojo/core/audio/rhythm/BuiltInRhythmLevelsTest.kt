package dev.zmdld11.guitardojo.core.audio.rhythm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInRhythmLevelsTest {
    @Test
    fun `关卡 id 唯一且拍间隔合法`() {
        val ids = BuiltInRhythmLevels.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        BuiltInRhythmLevels.all.forEach { level ->
            assertTrue("${level.id} 缺拍序列", level.beats.isNotEmpty())
            assertTrue("${level.id} 拍间隔必须为正", level.beats.all { it > 0.0 })
            assertTrue("${level.id} 单个间隔不应超过两拍", level.beats.all { it <= 2.0 })
            assertTrue("${level.id} repeats 至少 1", level.repeats >= 1)
        }
    }

    @Test
    fun `按周挑关：精确命中，否则就近回退`() {
        assertEquals("rhythm.w1-quarters", BuiltInRhythmLevels.forWeek(1).id)
        assertEquals("rhythm.w5-triplets", BuiltInRhythmLevels.forWeek(5).id)
        // 第 6/7/8 周没有专属关卡，回退到最近的 w5
        assertEquals("rhythm.w5-triplets", BuiltInRhythmLevels.forWeek(6).id)
        // 第 11/12 周在 w10 之后，回退到 w10
        assertEquals("rhythm.w10-syncopation", BuiltInRhythmLevels.forWeek(11).id)
        assertEquals("rhythm.w1-quarters", BuiltInRhythmLevels.forWeek(null).id)
    }

    @Test
    fun `关卡按周递进`() {
        val hints = BuiltInRhythmLevels.all.mapNotNull { it.weekHint }
        assertEquals(hints.sorted(), hints)
    }
}
