package dev.zmdld11.guitardojo

import dev.zmdld11.guitardojo.core.phrase.PhraseCodec
import dev.zmdld11.guitardojo.core.phrase.PhraseOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BuiltInPhrasesTest {
    @Test
    fun `内置乐句全部通过 schema 校验`() {
        val files = assetFiles()
        assertTrue("至少应有 8 条内置乐句，实际 ${files.size}", files.size >= 8)

        val ids = mutableSetOf<String>()
        files.forEach { file ->
            val phrase = PhraseCodec.decode(file.readText())
            assertTrue("${file.name} 的 id 应以 builtin. 开头", phrase.id.startsWith("builtin."))
            assertEquals("${file.name} 应标记为内置", PhraseOrigin.BUILTIN, phrase.origin)
            assertTrue("${file.name} 的 id 重复：${phrase.id}", ids.add(phrase.id))
            assertTrue("${file.name} 没有音符", phrase.notes.isNotEmpty())
        }
    }

    @Test
    fun `内置乐句的时长与显示信息完整`() {
        assetFiles().forEach { file ->
            val phrase = PhraseCodec.decode(file.readText())
            assertTrue("${file.name} 缺少标题", phrase.title.isNotBlank())
            assertTrue("${file.name} 缺少描述", phrase.description.isNotBlank())
            assertTrue("${file.name} 的难度应在 1-5", phrase.difficulty in 1..5)
            assertTrue("${file.name} 应有标签", phrase.tags.isNotEmpty())
            assertTrue("${file.name} 的总时长应为正", phrase.totalTicks > 0)
        }
    }

    private fun assetFiles(): List<File> {
        val dir = File("src/main/assets/phrases")
        assertTrue("找不到内置乐句目录：${dir.absolutePath}", dir.isDirectory)
        return dir.listFiles { file -> file.extension == "json" }?.sorted().orEmpty()
    }
}
