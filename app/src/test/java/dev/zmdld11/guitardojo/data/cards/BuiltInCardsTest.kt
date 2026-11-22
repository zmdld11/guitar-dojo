package dev.zmdld11.guitardojo.data.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BuiltInCardsTest {
    @Test
    fun `卡片 id 唯一且各牌组数量符合设计`() {
        val ids = BuiltInCards.all.map(TrainingCard::id)
        assertEquals("卡片 id 不能重复", ids.size, ids.toSet().size)

        assertEquals(16, BuiltInCards.byDeck(CardDeck.THEORY).size)
        assertEquals(13, BuiltInCards.byDeck(CardDeck.PRACTICE).size)
        assertEquals(8, BuiltInCards.byDeck(CardDeck.SETUP).size)
        assertEquals(6, BuiltInCards.byDeck(CardDeck.MAINTENANCE).size)
    }

    @Test
    fun `卡片文案保持短小`() {
        BuiltInCards.all.forEach { card ->
            assertTrue("${card.id} 缺少标题", card.title.isNotBlank())
            assertTrue("${card.id} 缺少问题", card.prompt.isNotBlank())
            assertTrue("${card.id} 缺少答案", card.answer.isNotBlank())
            assertTrue("${card.id} 的问题过长", card.prompt.length <= 80)
            assertTrue("${card.id} 的答案过长", card.answer.length <= 80)
            assertTrue("${card.id} 的补充过长", card.detail.length <= 80)
        }
    }

    @Test
    fun `引用的插图都存在`() {
        BuiltInCards.all.mapNotNull(TrainingCard::figure).forEach { path ->
            assertTrue("找不到插图 $path", File("src/main/assets/$path").isFile)
        }
    }
}
