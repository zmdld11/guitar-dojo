package dev.zmdld11.guitardojo.core.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanCodecTest {
    @Test
    fun `计划快照可以编解码往返`() {
        val plan =
            PlanGenerator().generate(
                PlanInput(
                    todayEpochDay = LocalDateFixtures.SATURDAY,
                    curriculum = LocalDateFixtures.curriculum(),
                    phrases = LocalDateFixtures.phrases(),
                ),
            )

        val decoded = PlanCodec.decodePlan(PlanCodec.encodePlan(plan))

        assertEquals(plan, decoded)
    }

    @Test
    fun `带未知字段的旧快照仍可读取`() {
        val text = PlanCodec.encodePlan(PlanCodec.decodePlan(SNAPSHOT))
        val decoded = PlanCodec.decodePlan(SNAPSHOT.replace("\"tasks\":", "\"futureField\": null, \"tasks\":"))

        assertEquals(2, decoded.tasks.size)
        assertTrue(text.contains("\"theory_card\""))
    }

    @Test
    fun `已完成步骤列表往返`() {
        val encoded = PlanCodec.encodeCompletedSteps(listOf(0, 2, 5))

        assertEquals(listOf(0, 2, 5), PlanCodec.decodeCompletedSteps(encoded))
        assertEquals(emptyList<Int>(), PlanCodec.decodeCompletedSteps("not json"))
    }

    private companion object {
        const val SNAPSHOT =
            """
            {
              "dateEpochDay": 20714,
              "kind": "NO_GUITAR",
              "minutesPerDay": 60,
              "tasks": [
                { "type": "theory_card", "cardId": "theory.string-names", "reason": "今日一卡" },
                { "type": "note", "reason": "写一句下次要注意什么" }
              ],
              "estimatedMinutes": 8,
              "warnings": []
            }
            """
    }
}

/** 测试共用的日期与课程夹具。 */
object LocalDateFixtures {
    val SATURDAY: Long = LocalDate.of(2026, 11, 21).toEpochDay()

    fun phrases(): Map<String, PhraseInfo> =
        mapOf(
            "builtin.chromatic.basic" to PhraseInfo("builtin.chromatic.basic", "爬格子", listOf("热身", "爬格子"), 8, 80.0, "爬格子"),
        )

    fun curriculum(): Curriculum =
        Curriculum(
            id = "builtin.curriculum.starter",
            title = "入门 4 周",
            weeks = listOf(CurriculumWeek(1, "左手独立", listOf("builtin.chromatic.basic"), 80.0)),
        )
}
