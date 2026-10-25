package dev.zmdld11.guitardojo.ui.screens

import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.phrase.PhraseNote
import kotlin.math.abs

/** 标准音值：flags 是符尾数，dots 是附点数，tuplet 非空表示连音符（3 = 三连音）。 */
internal data class NoteValue(
    val ticks: Long,
    val flags: Int,
    val dots: Int = 0,
    val tuplet: Int? = null,
    val stem: Boolean = true,
)

/**
 * 六线谱上的一条显示音符。
 *
 * 原始音符跨小节或时值无法用单个标准音值表示时，会拆成多个 segment，
 * 相邻 segment 之间画连音线；[sourceIndex] 指回 [Phrase.notes] 的下标。
 */
internal data class DisplayNote(
    val sourceIndex: Int,
    val source: PhraseNote,
    val tick: Long,
    val durationTicks: Long,
    val value: NoteValue,
    val tiedFromPrevious: Boolean,
) {
    val endTick: Long get() = tick + durationTicks
}

/** 相邻两个 segment 之间的连音线。 */
internal data class TieSegment(
    val first: DisplayNote,
    val second: DisplayNote,
)

internal enum class PickDirection { DOWN, UP }

/** 拨向标记，绑定到具体起音所在的弦，方便在谱面上定位。 */
internal data class PickMark(
    val tick: Long,
    val string: Int,
    val direction: PickDirection,
)

/**
 * 判断时值对应的标准音值；优先精确匹配（含附点与三连音）。
 *
 * 找不到精确值时优先取「略长于实际发声长度」的写法（如 Riff 里 0.4 拍的断奏写成八分音符），
 * 但超出 25% 就退回到最接近的音值，避免把 1.1 拍写成附点四分音符。
 */
internal fun noteValueOf(
    durationTicks: Long,
    ppq: Int,
): NoteValue {
    val values = standardNoteValues(ppq)
    values.firstOrNull { abs(it.ticks - durationTicks) <= tolerance(ppq) }?.let { return it }
    val ceiling = values.filter { it.ticks >= durationTicks }.minByOrNull { it.ticks }
    if (ceiling != null && ceiling.ticks - durationTicks <= durationTicks / 4) return ceiling
    return values.minBy { abs(it.ticks - durationTicks) }
}

/**
 * 把乐句拆成可绘制的音符序列：跨小节的长音按小节切开，
 * 小节内不能用单个标准音值表示的时值拆成最多三段（如 5 拍 = 全音符 + 四分音符）。
 */
internal fun displayNotesOf(phrase: Phrase): List<DisplayNote> {
    if (phrase.bars.isEmpty()) return emptyList()
    val firstBarStart = phrase.bars.first().startTick
    val result = ArrayList<DisplayNote>(phrase.notes.size)
    phrase.notes.forEachIndexed { index, note ->
        var cursor = note.tick.coerceAtLeast(firstBarStart)
        val end = (note.tick + note.durationTicks).coerceAtLeast(cursor + 1)
        var tied = false
        var guard = 0
        while (cursor < end && guard++ < MAX_DISPLAY_SEGMENTS) {
            val bar = phrase.barAt(cursor) ?: break
            val portionEnd = minOf(end, bar.startTick + bar.lengthTicks)
            val portion = portionEnd - cursor
            if (portion <= 0) break
            splitPortion(portion, phrase.ppq).forEach { piece ->
                if (cursor >= end) return@forEach
                val duration = minOf(piece, end - cursor)
                result += DisplayNote(index, note, cursor, duration, noteValueOf(duration, phrase.ppq), tied)
                tied = true
                cursor += duration
            }
        }
    }
    return result.sortedBy { it.tick }
}

/** 收集所有需要画连音线的相邻 segment。 */
internal fun tieSegmentsOf(notes: List<DisplayNote>): List<TieSegment> {
    val result = ArrayList<TieSegment>()
    notes
        .groupBy { it.sourceIndex }
        .values
        .forEach { segments ->
            val sorted = segments.sortedBy { it.tick }
            for (index in 0 until sorted.size - 1) {
                val first = sorted[index]
                val second = sorted[index + 1]
                if (first.endTick == second.tick) result += TieSegment(first, second)
            }
        }
    return result
}

/** 每个起音（同一 tick 的和弦算一次）的拨弦方向：每拍内交替，第一下为下拨。 */
internal fun pickDirectionsOf(phrase: Phrase): Map<Long, PickDirection> {
    val result = HashMap<Long, PickDirection>()
    phrase.bars.forEach { bar ->
        val beatTicks = phrase.beatTicksOf(bar).coerceAtLeast(1)
        val barEnd = bar.startTick + bar.lengthTicks
        val attacks =
            phrase.notes
                .asSequence()
                .map { it.tick }
                .filter { it >= bar.startTick && it < barEnd }
                .distinct()
                .toList()
        attacks
            .groupBy { ((it - bar.startTick) / beatTicks).toInt() }
            .forEach { (_, ticks) ->
                ticks.sorted().forEachIndexed { index, tick ->
                    result[tick] = if (index % 2 == 0) PickDirection.DOWN else PickDirection.UP
                }
            }
    }
    return result
}

/** 把拨向表转换成可直接定位的标记；同一 tick 的和弦取最高音所在的弦。 */
internal fun pickMarksOf(phrase: Phrase): List<PickMark> {
    val byTick = phrase.notes.groupBy { it.tick }
    return pickDirectionsOf(phrase).mapNotNull { (tick, direction) ->
        byTick[tick]?.minByOrNull { it.string }?.let { PickMark(tick, it.string, direction) }
    }
}

/** 三连音组中间那个音的 tick，用来在符杠上方标「3」。 */
internal fun tripletAnchorTicksOf(phrase: Phrase): List<Long> {
    val anchors = ArrayList<Long>()
    phrase.bars.forEach { bar ->
        val beatTicks = phrase.beatTicksOf(bar).coerceAtLeast(1)
        val barEnd = bar.startTick + bar.lengthTicks
        val notes = phrase.notes.filter { it.tick >= bar.startTick && it.tick < barEnd }
        notes
            .groupBy { ((it.tick - bar.startTick) / beatTicks).toInt() }
            .forEach { (_, beatNotes) ->
                val ticks = beatNotes.map { it.tick }.distinct().sorted()
                if (ticks.size != 3) return@forEach
                val allTriplets =
                    ticks.all { tick ->
                        val duration = beatNotes.firstOrNull { it.tick == tick }?.durationTicks ?: 0
                        noteValueOf(duration, phrase.ppq).tuplet == 3
                    }
                if (allTriplets) anchors += ticks[1]
            }
    }
    return anchors
}

/** 标准音值的候选表，按从长到短排列。 */
private fun standardNoteValues(ppq: Int): List<NoteValue> {
    val whole = 4L * ppq
    val half = 2L * ppq
    val quarter = ppq.toLong()
    val eighth = ppq / 2L
    val sixteenth = ppq / 4L
    val thirtySecond = ppq / 8L
    return listOf(
        NoteValue(whole, flags = 0, stem = false),
        NoteValue(whole * 3 / 2, flags = 0, dots = 1, stem = false),
        NoteValue(half, flags = 0),
        NoteValue(half * 3 / 2, flags = 0, dots = 1),
        NoteValue(quarter * 2 / 3, flags = 0, tuplet = 3),
        NoteValue(quarter, flags = 0),
        NoteValue(quarter * 3 / 2, flags = 0, dots = 1),
        NoteValue(eighth, flags = 1),
        NoteValue(eighth * 3 / 2, flags = 1, dots = 1),
        NoteValue(eighth * 2 / 3, flags = 1, tuplet = 3),
        NoteValue(sixteenth, flags = 2),
        NoteValue(sixteenth * 3 / 2, flags = 2, dots = 1),
        NoteValue(sixteenth * 2 / 3, flags = 2, tuplet = 3),
        NoteValue(thirtySecond, flags = 3),
        NoteValue(thirtySecond * 3 / 2, flags = 3, dots = 1),
    )
}

/** 单个标准音值放不下时，尝试拆成两段或三段；拆不开就按最接近的单个音值显示。 */
private fun splitPortion(
    durationTicks: Long,
    ppq: Int,
): List<Long> {
    val values = standardNoteValues(ppq)
    if (values.any { abs(it.ticks - durationTicks) <= tolerance(ppq) }) return listOf(durationTicks)
    val candidates =
        values
            .map { it.ticks }
            .filter { it in 1..durationTicks }
            .distinct()
            .sortedDescending()
    candidates.forEach { first ->
        val rest = durationTicks - first
        if (rest > 0 && values.any { abs(it.ticks - rest) <= tolerance(ppq) }) {
            return listOf(first, rest)
        }
    }
    candidates.forEach { first ->
        candidates.forEach { second ->
            val rest = durationTicks - first - second
            if (rest > 0 && values.any { abs(it.ticks - rest) <= tolerance(ppq) }) {
                return listOf(first, second, rest)
            }
        }
    }
    return listOf(durationTicks)
}

private fun tolerance(ppq: Int): Long = (ppq / 480L).coerceAtLeast(2L)

private const val MAX_DISPLAY_SEGMENTS = 256
