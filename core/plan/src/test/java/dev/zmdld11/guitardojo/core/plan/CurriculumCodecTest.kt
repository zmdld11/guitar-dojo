package dev.zmdld11.guitardojo.core.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 课程校验：结构错误要报出来；跨周复用乐句是刻意的（巩固周重弹），只禁止同周重复。 */
class CurriculumCodecTest {
    private fun week(
        index: Int,
        phraseIds: List<String>,
    ): CurriculumWeek =
        CurriculumWeek(
            index = index,
            focus = "技术点 $index",
            phraseIds = phraseIds,
            targetBpm = 80.0,
            theoryCardIds = emptyList(),
            focusNotes = "讲解 $index",
        )

    private fun curriculum(weeks: List<CurriculumWeek>): Curriculum =
        Curriculum(id = "builtin.curriculum.test", title = "测试课程", weeks = weeks)

    @Test
    fun `跨周复用乐句是合法的`() {
        val issues =
            CurriculumValidator.validate(
                curriculum(
                    listOf(
                        week(1, listOf("p.a", "p.b")),
                        week(2, listOf("p.b", "p.c")),
                    ),
                ),
            )
        assertEquals(emptyList<String>(), issues)
    }

    @Test
    fun `同一周内乐句重复要报错`() {
        val issues =
            CurriculumValidator.validate(
                curriculum(listOf(week(1, listOf("p.a", "p.a")))),
            )
        assertTrue(issues.first().contains("同一周内乐句重复"))
    }

    @Test
    fun `curriculumOrder 跨周去重`() {
        val order = curriculumOrder(curriculum(listOf(week(1, listOf("p.a", "p.b")), week(2, listOf("p.b", "p.c")))))
        assertEquals(listOf("p.a", "p.b", "p.c"), order)
    }
}
