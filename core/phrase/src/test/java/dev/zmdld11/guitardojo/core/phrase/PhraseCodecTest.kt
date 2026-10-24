package dev.zmdld11.guitardojo.core.phrase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseCodecTest {
    @Test
    fun `合法乐句可以解析并往返编码`() {
        val phrase = PhraseCodec.decode(VALID_JSON)

        assertEquals("builtin.test", phrase.id)
        assertEquals(2, phrase.bars.size)
        assertEquals(3, phrase.notes.size)
        assertEquals(PhraseOrigin.BUILTIN, phrase.origin)

        val roundTrip = PhraseCodec.decode(PhraseCodec.encode(phrase))
        assertEquals(phrase, roundTrip)
    }

    @Test
    fun `未知字段被忽略`() {
        val withExtra = VALID_JSON.replace("\"title\"", "\"bpmDisplay\": 88, \"title\"")

        val phrase = PhraseCodec.decode(withExtra)

        assertEquals("测试乐句", phrase.title)
    }

    @Test
    fun `schemaVersion 不对时给出中文错误`() {
        val error = expectInvalid(VALID_JSON.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))

        assertTrue(error.issues.any { it.contains("schemaVersion") })
    }

    @Test
    fun `小节不连续时报错`() {
        val broken = VALID_JSON.replace("\"startTick\": 3840", "\"startTick\": 3960")

        val error = expectInvalid(broken)

        assertTrue(error.issues.any { it.contains("startTick") })
    }

    @Test
    fun `弦品与音高不一致时报错`() {
        val broken = VALID_JSON.replace("\"midi\": 42", "\"midi\": 43")

        val error = expectInvalid(broken)

        assertTrue(error.issues.any { it.contains("不一致") })
    }

    @Test
    fun `同一弦同一 tick 不能重复发声`() {
        val duplicated = VALID_JSON.replaceFirst("\"tick\": 960", "\"tick\": 0")

        val error = expectInvalid(duplicated)

        assertTrue(error.issues.any { it.contains("重复发声") })
    }

    @Test
    fun `未知技巧标记会被拒绝`() {
        val broken = VALID_JSON.replace("\"palm_mute\"", "\"magic_touch\"")

        val error = expectInvalid(broken)

        assertTrue(error.issues.any { it.contains("magic_touch") })
    }

    @Test
    fun `音符乱序时报错`() {
        val broken = VALID_JSON.replaceFirst("\"tick\": 0,", "\"tick\": 1920,")

        val error = expectInvalid(broken)

        assertTrue(error.issues.any { it.contains("升序") })
    }

    @Test
    fun `notesInBars 只取选中小节`() {
        val phrase = PhraseCodec.decode(VALID_JSON)

        val firstBar = phrase.notesInBars(0, 0)
        val secondBar = phrase.notesInBars(1, 1)

        assertEquals(2, firstBar.size)
        assertEquals(1, secondBar.size)
    }

    @Test
    fun `JSON 语法错误不会抛未包装的异常`() {
        val error = expectInvalid("{ not json }")

        assertTrue(error.issues.first().contains("JSON"))
    }

    private fun expectInvalid(json: String): PhraseException =
        try {
            PhraseCodec.decode(json)
            throw AssertionError("应当校验失败")
        } catch (error: PhraseException) {
            error
        }

    private companion object {
        val VALID_JSON =
            """
            {
              "schemaVersion": 1,
              "id": "builtin.test",
              "title": "测试乐句",
              "description": "两个小节",
              "difficulty": 1,
              "tags": ["热身"],
              "origin": "builtin",
              "tuning": [40, 45, 50, 55, 59, 64],
              "capo": 0,
              "ppq": 960,
              "bars": [
                { "index": 0, "startTick": 0, "lengthTicks": 3840, "timeSignature": [4, 4], "tempoBpm": 80.0 },
                { "index": 1, "startTick": 3840, "lengthTicks": 3840, "timeSignature": [4, 4], "tempoBpm": 80.0 }
              ],
              "notes": [
                { "bar": 0, "tick": 0, "durationTicks": 960, "midi": 40, "string": 6, "fret": 0, "velocity": 0.8, "techniques": ["palm_mute"] },
                { "bar": 0, "tick": 960, "durationTicks": 960, "midi": 42, "string": 6, "fret": 2, "velocity": 0.8, "techniques": [] },
                { "bar": 1, "tick": 3840, "durationTicks": 1920, "midi": 45, "string": 5, "fret": 0, "velocity": 0.8, "techniques": [] }
              ]
            }
            """.trimIndent()
    }
}
