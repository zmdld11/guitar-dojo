package dev.zmdld11.guitardojo.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zmdld11.guitardojo.core.audio.pitch.NoteNaming
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.phrase.PhraseNote

internal const val SCORE_BEAT_WIDTH_DP = 46
internal const val SCORE_STRING_GAP_DP = 13
internal const val SCORE_HEADER_DP = 40
internal const val SCORE_STEM_DP = 18
internal const val SCORE_STAFF_TOP_DP = SCORE_HEADER_DP + SCORE_STEM_DP
internal const val SCORE_BOTTOM_PADDING_DP = 12
internal const val SCORE_LEADING_DP = 14
private const val LEGEND_WIDTH_DP = 20
private const val NOTE_BOX_WIDTH_DP = 24
private const val NOTE_BOX_HEIGHT_DP = 13
private const val TUPLET_TOP_DP = 28

/**
 * GP 风格的单行六线谱：六条弦连续贯穿所有小节，小节线、拍格、符干都在同一张谱上，
 * 不再是按小节切开的卡片。点击任意小节设置循环区间。
 */
@Composable
internal fun PhraseScore(
    phrase: Phrase,
    currentTick: Long?,
    currentBar: Int?,
    loopStartBar: Int?,
    loopEndBar: Int?,
    showNoteNames: Boolean,
    showPicking: Boolean,
    onSelectBar: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (phrase.bars.isEmpty()) return
    val density = LocalDensity.current
    val displayNotes = remember(phrase.id) { displayNotesOf(phrase) }
    val attacks = remember(displayNotes) { displayNotes.groupBy { it.tick }.toSortedMap() }
    val ties = remember(displayNotes) { tieSegmentsOf(displayNotes) }
    val pickMarks = remember(phrase.id) { pickMarksOf(phrase) }
    val tripletTicks = remember(phrase.id) { tripletAnchorTicksOf(phrase) }
    val barWidths = remember(phrase.id) { phrase.bars.map { SCORE_BEAT_WIDTH_DP.dp * it.beatsPerBar } }
    val barStarts =
        remember(barWidths) {
            val starts = ArrayList<Dp>(barWidths.size)
            var cursor = SCORE_LEADING_DP.dp
            barWidths.forEach {
                starts += cursor
                cursor += it
            }
            starts
        }
    val totalWidth = barStarts.last() + barWidths.last() + SCORE_LEADING_DP.dp
    val staffHeight = (SCORE_STAFF_TOP_DP + (phrase.tuning.size.coerceAtLeast(1) - 1) * SCORE_STRING_GAP_DP + SCORE_BOTTOM_PADDING_DP).dp
    val scrollState = rememberScrollState()
    val barStartsPx = remember(barStarts) { barStarts.map { with(density) { it.toPx() } } }
    val barWidthsPx = remember(barWidths) { barWidths.map { with(density) { it.toPx() } } }

    FollowCurrentBar(
        currentBar = currentBar,
        phrase = phrase,
        barStarts = barStartsPx,
        barWidths = barWidthsPx,
        scrollState = scrollState,
    )

    Row(modifier = modifier.fillMaxWidth()) {
        ScoreLegend(phrase = phrase)
        Spacer(Modifier.width(10.dp))
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(totalWidth)
                        .height(staffHeight + SCORE_HEADER_DP.dp)
                        .pointerInput(barStartsPx, barWidthsPx) {
                            detectTapGestures { offset ->
                                val index = barIndexAt(offset.x, barStartsPx, barWidthsPx) ?: return@detectTapGestures
                                phrase.bars.getOrNull(index)?.let { onSelectBar(it.index) }
                            }
                        },
            ) {
                ScoreStaff(
                    phrase = phrase,
                    displayNotes = displayNotes,
                    attacks = attacks,
                    ties = ties,
                    pickMarks = pickMarks,
                    barStarts = barStarts,
                    barWidths = barWidths,
                    loopStartBar = loopStartBar,
                    loopEndBar = loopEndBar,
                    currentBar = currentBar,
                    currentTick = currentTick,
                    showPicking = showPicking,
                )
                ScoreHeader(
                    phrase = phrase,
                    barStarts = barStarts,
                    barWidths = barWidths,
                    loopStartBar = loopStartBar,
                    loopEndBar = loopEndBar,
                )
                displayNotes.forEach { note ->
                    val position = notePosition(phrase, note.tick, note.source.string, barStarts, barWidths)
                    ScoreNoteLabel(
                        note = note,
                        position = position,
                        isCurrent = !note.tiedFromPrevious && note.tick == currentTick,
                        showNoteNames = showNoteNames,
                    )
                }
                tripletTicks.forEach { tick ->
                    val note = phrase.notes.firstOrNull { it.tick == tick } ?: return@forEach
                    val x = notePosition(phrase, tick, note.string, barStarts, barWidths).x
                    Text(
                        text = "3",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.offset(x = x - 3.dp, y = TUPLET_TOP_DP.dp),
                    )
                }
            }
        }
    }
}

/** 播放时让当前小节留在可视区域；用户手动滚动后，只有当前小节跑出屏幕才追。 */
@Composable
private fun FollowCurrentBar(
    currentBar: Int?,
    phrase: Phrase,
    barStarts: List<Float>,
    barWidths: List<Float>,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    LaunchedEffect(currentBar) {
        val bar = currentBar ?: return@LaunchedEffect
        val index = phrase.bars.indexOfFirst { it.index == bar }
        if (index < 0) return@LaunchedEffect
        val viewport = scrollState.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        val start = barStarts[index]
        val end = start + barWidths[index]
        if (start < scrollState.value || end > scrollState.value + viewport) {
            scrollState.animateScrollTo((start - viewport * 0.1f).coerceAtLeast(0f).toInt())
        }
    }
}

@Composable
private fun ScoreHeader(
    phrase: Phrase,
    barStarts: List<Dp>,
    barWidths: List<Dp>,
    loopStartBar: Int?,
    loopEndBar: Int?,
) {
    phrase.bars.forEachIndexed { index, bar ->
        Text(
            text = "${bar.index + 1}",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.offset(x = barStarts[index] + 3.dp, y = 1.dp),
        )
        val signature = bar.timeSignature
        if (signature.size >= 2 && signature != listOf(4, 4)) {
            Text(
                text = "${signature[0]}/${signature[1]}",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = barStarts[index] + 13.dp, y = 2.dp),
            )
        }
    }
    val loopStart = loopStartBar?.let { bar -> phrase.bars.indexOfFirst { it.index == bar } } ?: -1
    val loopEnd = loopEndBar?.let { bar -> phrase.bars.indexOfFirst { it.index == bar } } ?: -1
    if (loopStart >= 0 && loopEnd >= loopStart) {
        Text(
            text = "A",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.offset(x = barStarts[loopStart] + 15.dp, y = 1.dp),
        )
        Text(
            text = "B",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.offset(x = barStarts[loopEnd] + barWidths[loopEnd] - 12.dp, y = 1.dp),
        )
    }
}

@Composable
private fun ScoreNoteLabel(
    note: DisplayNote,
    position: NotePosition,
    isCurrent: Boolean,
    showNoteNames: Boolean,
) {
    val text = if (showNoteNames) NoteNaming.labelOf(note.source.midi) else "${note.source.fret}"
    val color =
        if (isCurrent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val x = position.x - (NOTE_BOX_WIDTH_DP / 2).dp
    val y = position.y - (NOTE_BOX_HEIGHT_DP / 2).dp
    Box(
        modifier =
            Modifier
                .offset(x = x, y = y)
                .width(NOTE_BOX_WIDTH_DP.dp)
                .height(NOTE_BOX_HEIGHT_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = if (showNoteNames) 8.sp else 10.sp,
            lineHeight = if (showNoteNames) 10.sp else 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ScoreLegend(phrase: Phrase) {
    val strings = phrase.tuning.size.coerceAtLeast(1)
    Column(modifier = Modifier.padding(top = (SCORE_STAFF_TOP_DP - SCORE_STRING_GAP_DP / 2).dp)) {
        for (string in 1..strings) {
            val midi = phrase.tuning.getOrNull(phrase.tuning.size - string)
            Box(
                modifier =
                    Modifier
                        .width(LEGEND_WIDTH_DP.dp)
                        .height(SCORE_STRING_GAP_DP.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = if (midi != null) NoteNaming.nameOf(midi) else "?",
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal data class NotePosition(
    val x: Dp,
    val y: Dp,
)

internal fun notePosition(
    phrase: Phrase,
    note: PhraseNote,
    barStarts: List<Dp>,
    barWidths: List<Dp>,
): NotePosition = notePosition(phrase, note.tick, note.string, barStarts, barWidths)

internal fun notePosition(
    phrase: Phrase,
    tick: Long,
    string: Int,
    barStarts: List<Dp>,
    barWidths: List<Dp>,
): NotePosition {
    val bar = phrase.barAt(tick) ?: phrase.bars.last()
    val index = phrase.bars.indexOfFirst { it.index == bar.index }.coerceAtLeast(0)
    val fraction = ((tick - bar.startTick).toFloat() / bar.lengthTicks.toFloat()).coerceIn(0f, 1f)
    return NotePosition(
        x = barStarts[index] + barWidths[index] * fraction,
        y = (SCORE_STAFF_TOP_DP + (string - 1) * SCORE_STRING_GAP_DP).dp,
    )
}

private fun barIndexAt(
    x: Float,
    barStarts: List<Float>,
    barWidths: List<Float>,
): Int? {
    val index = barStarts.indexOfLast { x >= it }
    if (index < 0) return null
    return index.takeIf { x <= barStarts[index] + barWidths[index] }
}
