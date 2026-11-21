package dev.zmdld11.guitardojo

import dev.zmdld11.guitardojo.core.phrase.PhraseCodec
import dev.zmdld11.guitardojo.core.plan.CurriculumCodec
import dev.zmdld11.guitardojo.data.cards.BuiltInCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CurriculumTest {
    @Test
    fun `内置课程通过校验`() {
        val curriculum = CurriculumCodec.decode(asset().readText())

        assertEquals("builtin.curriculum.starter", curriculum.id)
        assertEquals(4, curriculum.weeks.size)
        assertEquals(listOf(1, 2, 3, 4), curriculum.weeks.map { it.index })
    }

    @Test
    fun `课程引用的乐句都在内置乐句库`() {
        val curriculum = CurriculumCodec.decode(asset().readText())
        val phraseIds = builtinPhraseIds()

        curriculum.weeks.forEach { week ->
            week.phraseIds.forEach { id ->
                assertTrue("课程第 ${week.index} 周引用的 $id 不在内置乐句库", id in phraseIds)
            }
        }
    }

    @Test
    fun `课程引用的乐理卡都在内置牌组`() {
        val curriculum = CurriculumCodec.decode(asset().readText())
        val cardIds = BuiltInCards.all.map { it.id }.toSet()

        curriculum.weeks.forEach { week ->
            week.theoryCardIds.forEach { id ->
                assertTrue("课程第 ${week.index} 周引用的 $id 不在乐理牌组", id in cardIds)
            }
        }
    }

    private fun asset(): File {
        val file = File("src/main/assets/curriculum.json")
        assertTrue("找不到内置课程：${file.absolutePath}", file.isFile)
        return file
    }

    private fun builtinPhraseIds(): Set<String> {
        val dir = File("src/main/assets/phrases")
        assertTrue("找不到内置乐句目录：${dir.absolutePath}", dir.isDirectory)
        return dir
            .listFiles { file -> file.extension == "json" }
            .orEmpty()
            .map { PhraseCodec.decode(it.readText()).id }
            .toSet()
    }
}
