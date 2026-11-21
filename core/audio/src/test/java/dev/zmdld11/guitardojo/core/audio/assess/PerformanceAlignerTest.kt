package dev.zmdld11.guitardojo.core.audio.assess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceAlignerTest {
    private val aligner = PerformanceAligner(SAMPLE_RATE)
    private val expected =
        listOf(60, 64, 67, 72, 60).mapIndexed { index, midi ->
            ExpectedNote(
                phraseIndex = index,
                barIndex = index / 4,
                midi = midi,
                startSample = index * 24_000L,
                endSample = index * 24_000L + 20_000L,
            )
        }

    @Test
    fun `完美演奏全部判定正确`() {
        val anchorOffset = 1_000L
        val events =
            expected.map { note ->
                event(note.midi, note.startSample + anchorOffset)
            }

        val scores = aligner.align(expected, events, anchorOffset)

        assertEquals(1.0, scores.accuracy, 1e-9)
        assertEquals(1.0, scores.rhythm, 1e-9)
        assertEquals(1.0, scores.total, 1e-9)
        assertEquals(expected.size, scores.verdicts.count { it.kind == VerdictKind.CORRECT })
    }

    @Test
    fun `弹错音高的位置标 WRONG_PITCH`() {
        val events =
            expected.mapIndexed { index, note ->
                event(if (index == 2) note.midi + 2 else note.midi, note.startSample)
            }

        val scores = aligner.align(expected, events, 0L)

        assertEquals(0.8, scores.accuracy, 1e-9)
        assertEquals(VerdictKind.WRONG_PITCH, scores.verdicts[2].kind)
        assertEquals(2, scores.verdicts[2].phraseIndex)
    }

    @Test
    fun `起音超出节奏容差标 WRONG_TIMING`() {
        val events =
            expected.mapIndexed { index, note ->
                event(note.midi, note.startSample + if (index == 1) 8_000L else 0L)
            }

        val scores = aligner.align(expected, events, 0L)

        assertEquals(1.0, scores.accuracy, 1e-9)
        assertEquals(0.8, scores.rhythm, 1e-9)
        assertEquals(VerdictKind.WRONG_TIMING, scores.verdicts[1].kind)
        assertEquals(166.66, scores.verdicts[1].onsetDeviationMs!!, 0.01)
    }

    @Test
    fun `漏弹的音标 MISSING`() {
        // 音符间距 1 秒，窗口够不到相邻音；只有中间的音被弹出
        val spaced =
            listOf(60, 64, 67).mapIndexed { index, midi ->
                ExpectedNote(index, index / 4, midi, index * 48_000L, index * 48_000L + 20_000L)
            }
        val events = listOf(event(64, 48_000))

        val scores = aligner.align(spaced, events, 0L)

        assertEquals(1.0 / 3, scores.accuracy, 1e-9)
        assertEquals(2, scores.verdicts.count { it.kind == VerdictKind.MISSING })
        assertEquals(VerdictKind.CORRECT, scores.verdicts[1].kind)
    }

    @Test
    fun `多弹的音标 EXTRA 且不影响准确率`() {
        val events =
            expected.map { event(it.midi, it.startSample) } +
                event(50, 150_000L)

        val scores = aligner.align(expected, events, 0L)

        assertEquals(1.0, scores.accuracy, 1e-9)
        assertEquals(1, scores.verdicts.count { it.kind == VerdictKind.EXTRA })
    }

    @Test
    fun `评分随演奏质量单调下降`() {
        fun accuracyOf(wrongCount: Int): Double {
            val events =
                expected.mapIndexed { index, note ->
                    event(if (index < wrongCount) note.midi + 3 else note.midi, note.startSample)
                }
            return aligner.align(expected, events, 0L).accuracy
        }

        val clean = accuracyOf(0)
        val oneWrong = accuracyOf(1)
        val threeWrong = accuracyOf(3)

        assertTrue(clean > oneWrong)
        assertTrue(oneWrong > threeWrong)
        assertEquals(listOf(1.0, 0.8, 0.4), listOf(clean, oneWrong, threeWrong).map { it })
    }

    @Test
    fun `延音覆盖同音高的连续期望音`() {
        val repeated =
            listOf(60, 60).mapIndexed { index, midi ->
                ExpectedNote(index, 0, midi, index * 12_000L, index * 12_000L + 10_000L)
            }
        // 一个从第一个音起持续到第二个音之后的事件
        val events = listOf(NoteEvent(0, 30_000, frequencyOf(60), 60, 0.9))

        val scores = aligner.align(repeated, events, 0L)

        assertEquals(1.0, scores.accuracy, 1e-9)
        assertEquals(2, scores.verdicts.count { it.kind == VerdictKind.CORRECT })
    }

    @Test
    fun `期望音为空返回零分`() {
        val scores = aligner.align(emptyList(), listOf(event(60, 0)), 0L)

        assertEquals(0.0, scores.total, 1e-9)
    }

    private fun event(
        midi: Int,
        startSample: Long,
    ): NoteEvent = NoteEvent(startSample, startSample + 2_000, frequencyOf(midi), midi, 0.9)

    private fun frequencyOf(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
