package dev.zmdld11.guitardojo.data.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardSchedulerTest {
    private val scheduler = CardScheduler()
    private val cards =
        listOf(
            card("a", "A"),
            card("b", "B"),
            card("c", "C"),
        )

    @Test
    fun `新卡保持输入顺序，今日卡取第一张`() {
        assertEquals(listOf("a", "b", "c"), scheduler.order(cards, emptyMap(), todayEpochDay = 100).map { it.id })
        assertEquals("a", scheduler.daily(cards, emptyMap(), todayEpochDay = 100)?.id)
    }

    @Test
    fun `到期卡排在新卡与未到期卡之前`() {
        val progress =
            mapOf(
                "a" to CardProgress("a", box = 2, nextReviewEpochDay = 90, lastReviewedEpochDay = 80),
                "b" to CardProgress("b", box = 1, nextReviewEpochDay = 120, lastReviewedEpochDay = 110),
            )

        assertEquals(listOf("a", "c", "b"), scheduler.order(cards, progress, todayEpochDay = 100).map { it.id })
    }

    @Test
    fun `记住了按 1 3 7 14 30 天推进`() {
        var progress: CardProgress? = null
        val expectedDays = listOf(101L, 104L, 111L, 125L, 155L, 185L)
        var todayEpochDay = 100L

        expectedDays.forEachIndexed { index, expectedNext ->
            progress = scheduler.review(progress, "a", todayEpochDay, remembered = true)
            assertEquals("第 ${index + 1} 次", expectedNext, progress!!.nextReviewEpochDay)
            assertEquals((index + 1).coerceAtMost(5), progress!!.box)
            todayEpochDay = expectedNext
        }
    }

    @Test
    fun `没记住回到一天后并重置盒子`() {
        val previous = CardProgress("a", box = 3, nextReviewEpochDay = 200, lastReviewedEpochDay = 190)
        val updated = scheduler.review(previous, "a", todayEpochDay = 150, remembered = false)

        assertEquals(0, updated.box)
        assertEquals(151L, updated.nextReviewEpochDay)
        assertTrue(updated.reviewed)
    }

    @Test
    fun `同一天重复自评不推进间隔`() {
        val previous = CardProgress("a", box = 2, nextReviewEpochDay = 104, lastReviewedEpochDay = 100)

        assertEquals(previous, scheduler.review(previous, "a", todayEpochDay = 100, remembered = true))
        assertEquals(previous, scheduler.review(previous, "a", todayEpochDay = 100, remembered = false))
    }

    @Test
    fun `五次成功复习后视为掌握`() {
        assertFalse(scheduler.isMastered(CardProgress("a", box = 4)))
        assertTrue(scheduler.isMastered(CardProgress("a", box = 5)))
        assertFalse(scheduler.isMastered(null))
    }

    @Test
    fun `进度编解码可往返并忽略坏行`() {
        val progress =
            listOf(
                CardProgress("a", box = 2, nextReviewEpochDay = 104, lastReviewedEpochDay = 100),
                CardProgress("b", box = 0, nextReviewEpochDay = 101, lastReviewedEpochDay = 100),
            )

        val encoded = CardProgressCodec.encode(progress)
        val decoded = CardProgressCodec.decode("$encoded\n坏行\nc|not-a-box|1|2")

        assertEquals(2, decoded.size)
        assertEquals(progress[0], decoded["a"])
        assertEquals(progress[1], decoded["b"])
        assertNull(decoded["c"])
    }

    private fun card(
        id: String,
        title: String,
    ): TrainingCard =
        TrainingCard(
            id = id,
            deck = CardDeck.THEORY,
            title = title,
            prompt = "问题 $title",
            answer = "答案 $title",
        )
}
