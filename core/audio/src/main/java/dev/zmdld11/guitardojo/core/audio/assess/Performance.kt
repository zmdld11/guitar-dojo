package dev.zmdld11.guitardojo.core.audio.assess

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/** 考核里一个期望弹的音；位置在播放流帧上，经拍点锚偏移后映射进录音。 */
data class ExpectedNote(
    val phraseIndex: Int,
    val barIndex: Int,
    val midi: Int,
    val startSample: Long,
    val endSample: Long,
)

enum class VerdictKind {
    CORRECT,
    WRONG_PITCH,
    WRONG_TIMING,
    MISSING,
    EXTRA,
}

data class NoteVerdict(
    val phraseIndex: Int,
    val barIndex: Int,
    val expectedMidi: Int,
    val kind: VerdictKind,
    /** 与期望起音的偏差（毫秒）；MISSING/EXTRA 无意义。 */
    val onsetDeviationMs: Double? = null,
    /** 实际频率与期望频率的音分偏差；未命中音高时也可能有值。 */
    val centsDeviation: Double? = null,
)

data class PerformanceScores(
    /** 音高命中数 / 期望音数（1.0 = 每个期望音都找到了同音高的演奏）。 */
    val accuracy: Double,
    /** 命中音里起音在节奏容差内的占比。 */
    val rhythm: Double,
    val total: Double,
    val verdicts: List<NoteVerdict>,
)

private fun frequencyOf(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

/**
 * 期望音序列 × 检测到的音符事件 → 逐音判定与双维评分。
 *
 * 对齐按期望顺序贪心匹配：每个期望音在「起音 ± 节奏容差」的窗口里找第一个
 * 未被占用的检测事件；音高按实际频率与期望频率的距离判定。延音可以覆盖
 * 同音高的连续期望音；没被任何期望音认领的事件记 EXTRA。
 */
class PerformanceAligner(
    private val sampleRate: Int,
    private val timingToleranceMs: Double = 150.0,
    private val pitchToleranceSemitones: Double = 0.7,
) {
    fun align(
        expectedNotes: List<ExpectedNote>,
        events: List<NoteEvent>,
        anchorOffset: Long?,
    ): PerformanceScores {
        if (expectedNotes.isEmpty()) return PerformanceScores(0.0, 0.0, 0.0, emptyList())
        val offset = anchorOffset ?: 0L
        val toleranceSamples = (timingToleranceMs / 1000.0 * sampleRate).toLong().coerceAtLeast(1)
        val matcher =
            SequentialMatcher(events, offset, toleranceSamples, sampleRate, timingToleranceMs, pitchToleranceSemitones)
        val verdicts = ArrayList<NoteVerdict>(expectedNotes.size)
        var pitchHits = 0
        var rhythmCandidates = 0
        var onTimeHits = 0

        expectedNotes.sortedBy { it.startSample }.forEach { note ->
            val outcome = matcher.resolve(note)
            verdicts += outcome.verdict
            if (outcome.pitchOk) {
                pitchHits++
                rhythmCandidates++
                if (outcome.countsAsOnTime) onTimeHits++
            }
        }

        events.forEachIndexed { index, event ->
            if (!matcher.isUsed(index)) {
                verdicts += extraVerdict(event, expectedNotes, offset)
            }
        }

        val accuracy = pitchHits.toDouble() / expectedNotes.size
        val rhythm = if (rhythmCandidates == 0) 0.0 else onTimeHits.toDouble() / rhythmCandidates
        val total = 0.5 * accuracy + 0.5 * rhythm
        return PerformanceScores(accuracy, rhythm, total, verdicts)
    }

    private fun extraVerdict(
        event: NoteEvent,
        expectedNotes: List<ExpectedNote>,
        offset: Long,
    ): NoteVerdict {
        val nearest = expectedNotes.minByOrNull { abs(it.startSample + offset - event.startSample) }
        return NoteVerdict(
            phraseIndex = -1,
            barIndex = nearest?.barIndex ?: -1,
            expectedMidi = -1,
            kind = VerdictKind.EXTRA,
        )
    }
}

/** 按期望顺序逐音匹配；内部维护延音覆盖状态。 */
private class SequentialMatcher(
    private val events: List<NoteEvent>,
    private val offset: Long,
    private val toleranceSamples: Long,
    private val sampleRate: Int,
    private val timingToleranceMs: Double,
    private val pitchToleranceSemitones: Double,
) {
    private val used = BooleanArray(events.size)
    private var lastIndex = -1
    private var lastMidi = Int.MIN_VALUE
    private var lastPitchOk = false

    fun isUsed(index: Int): Boolean = used[index]

    fun resolve(note: ExpectedNote): Outcome {
        // 延音覆盖：同音高的连续期望音可共用一个尚未结束的事件（重复音、拖拍延音）；
        // 上一个音音高就不对时，它的延音不能再当正确依据
        val sustained =
            lastIndex >= 0 &&
                lastPitchOk &&
                lastMidi == note.midi &&
                events[lastIndex].endSample >= offset + note.startSample
        val matchedIndex = if (sustained) lastIndex else findEvent(note)
        if (matchedIndex == null) {
            reset()
            return Outcome(NoteVerdict(note.phraseIndex, note.barIndex, note.midi, VerdictKind.MISSING), false, false)
        }
        used[matchedIndex] = true
        lastIndex = matchedIndex
        lastMidi = note.midi

        val cents = 1200.0 * log2(events[matchedIndex].frequencyHz / frequencyOf(note.midi))
        val pitchOk = abs(cents / 100.0) <= pitchToleranceSemitones
        lastPitchOk = pitchOk
        val deviationMs =
            if (sustained) {
                null
            } else {
                (events[matchedIndex].startSample - offset - note.startSample) * 1000.0 / sampleRate
            }
        val onTime = deviationMs != null && abs(deviationMs) <= timingToleranceMs
        val verdict =
            when {
                !pitchOk -> NoteVerdict(note.phraseIndex, note.barIndex, note.midi, VerdictKind.WRONG_PITCH, deviationMs, cents)
                onTime || sustained -> {
                    NoteVerdict(
                        note.phraseIndex,
                        note.barIndex,
                        note.midi,
                        VerdictKind.CORRECT,
                        deviationMs,
                        cents,
                    )
                }
                else -> NoteVerdict(note.phraseIndex, note.barIndex, note.midi, VerdictKind.WRONG_TIMING, deviationMs, cents)
            }
        return Outcome(verdict, pitchOk, onTime || sustained)
    }

    private fun findEvent(note: ExpectedNote): Int? {
        val windowStart = offset + note.startSample - toleranceSamples
        val windowEnd = offset + note.endSample + toleranceSamples
        for (index in events.indices) {
            if (used[index]) continue
            val start = events[index].startSample
            if (start >= windowStart && start <= windowEnd) return index
        }
        return null
    }

    private fun reset() {
        lastIndex = -1
        lastMidi = Int.MIN_VALUE
        lastPitchOk = false
    }
}

private class Outcome(
    val verdict: NoteVerdict,
    val pitchOk: Boolean,
    val countsAsOnTime: Boolean,
)
