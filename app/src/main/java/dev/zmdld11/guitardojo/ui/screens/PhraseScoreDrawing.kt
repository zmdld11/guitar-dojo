package dev.zmdld11.guitardojo.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.zmdld11.guitardojo.core.phrase.Phrase

@Composable
internal fun ScoreStaff(
    phrase: Phrase,
    displayNotes: List<DisplayNote>,
    attacks: Map<Long, List<DisplayNote>>,
    ties: List<TieSegment>,
    pickMarks: List<PickMark>,
    barStarts: List<Dp>,
    barWidths: List<Dp>,
    loopStartBar: Int?,
    loopEndBar: Int?,
    currentBar: Int?,
    currentTick: Long?,
    showPicking: Boolean,
) {
    val colors =
        ScoreColors(
            line = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            bar = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            loop = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
            current = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f),
            stem = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            activeStem = MaterialTheme.colorScheme.primary,
        )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val geometry =
            ScoreGeometry(
                staffTop = SCORE_STAFF_TOP_DP.dp.toPx(),
                gap = SCORE_STRING_GAP_DP.dp.toPx(),
                strings = phrase.tuning.size.coerceAtLeast(1),
                barStarts = barStarts,
                barWidths = barWidths,
            )
        drawScoreHighlights(geometry, phrase, loopStartBar, loopEndBar, currentBar, colors)
        drawStaffLines(geometry, colors)
        drawBarAndBeatLines(geometry, phrase, colors)
        drawRhythm(geometry, phrase, displayNotes, attacks, ties, currentTick, colors)
        if (showPicking) drawPickMarks(geometry, phrase, pickMarks, currentTick, colors)
    }
}

private fun DrawScope.drawScoreHighlights(
    geometry: ScoreGeometry,
    phrase: Phrase,
    loopStartBar: Int?,
    loopEndBar: Int?,
    currentBar: Int?,
    colors: ScoreColors,
) {
    val top = geometry.staffTop - SCORE_STEM_DP.dp.toPx()
    val bottom = geometry.staffBottom + SCORE_BOTTOM_PADDING_DP.dp.toPx() * 0.6f
    val loopStart = loopStartBar?.let { bar -> phrase.bars.indexOfFirst { it.index == bar } } ?: -1
    val loopEnd = loopEndBar?.let { bar -> phrase.bars.indexOfFirst { it.index == bar } } ?: -1
    if (loopStart >= 0 && loopEnd >= loopStart) {
        val left = geometry.barStarts[loopStart].toPx()
        val right = geometry.barStarts[loopEnd].toPx() + geometry.barWidths[loopEnd].toPx()
        drawRoundRect(
            color = colors.loop,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            cornerRadius = CornerRadius(6.dp.toPx()),
        )
    }
    val currentIndex = currentBar?.let { bar -> phrase.bars.indexOfFirst { it.index == bar } } ?: -1
    if (currentIndex >= 0) {
        drawRect(
            color = colors.current,
            topLeft = Offset(geometry.barStarts[currentIndex].toPx(), top),
            size = Size(geometry.barWidths[currentIndex].toPx(), bottom - top),
        )
    }
}

private fun DrawScope.drawStaffLines(
    geometry: ScoreGeometry,
    colors: ScoreColors,
) {
    for (string in 0 until geometry.strings) {
        val y = geometry.staffTop + string * geometry.gap
        drawLine(
            color = colors.line,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private fun DrawScope.drawBarAndBeatLines(
    geometry: ScoreGeometry,
    phrase: Phrase,
    colors: ScoreColors,
) {
    phrase.bars.forEachIndexed { index, _ ->
        val startX = geometry.barStarts[index].toPx()
        drawLine(
            color = colors.bar,
            start = Offset(startX, geometry.staffTop),
            end = Offset(startX, geometry.staffBottom),
            strokeWidth = 1.2.dp.toPx(),
        )
    }
    drawLine(
        color = colors.bar,
        start = Offset(size.width, geometry.staffTop),
        end = Offset(size.width, geometry.staffBottom),
        strokeWidth = 1.2.dp.toPx(),
    )
}

private fun DrawScope.drawRhythm(
    geometry: ScoreGeometry,
    phrase: Phrase,
    notes: List<DisplayNote>,
    attacks: Map<Long, List<DisplayNote>>,
    ties: List<TieSegment>,
    currentTick: Long?,
    colors: ScoreColors,
) {
    drawStems(geometry, phrase, attacks, currentTick, colors)
    drawBeamsAndFlags(geometry, phrase, attacks, currentTick, colors)
    drawDots(geometry, phrase, notes, currentTick, colors)
    drawTies(geometry, phrase, ties, currentTick, colors)
}

internal fun displayPosition(
    geometry: ScoreGeometry,
    phrase: Phrase,
    note: DisplayNote,
): NotePosition = displayPosition(geometry, phrase, note.tick, note.source.string)

internal fun displayPosition(
    geometry: ScoreGeometry,
    phrase: Phrase,
    tick: Long,
    string: Int,
): NotePosition = notePosition(phrase, tick, string, geometry.barStarts, geometry.barWidths)

internal fun DrawScope.stemX(
    geometry: ScoreGeometry,
    phrase: Phrase,
    note: DisplayNote,
): Float = displayPosition(geometry, phrase, note).x.toPx() + STEM_OFFSET_DP.dp.toPx()

internal class ScoreGeometry(
    val staffTop: Float,
    val gap: Float,
    val strings: Int,
    val barStarts: List<Dp>,
    val barWidths: List<Dp>,
) {
    val staffBottom: Float get() = staffTop + (strings - 1) * gap
}

internal class ScoreColors(
    val line: Color,
    val bar: Color,
    val loop: Color,
    val current: Color,
    val stem: Color,
    val activeStem: Color,
)

internal const val STEM_OFFSET_DP = 6
internal const val BEAM_GAP_DP = 4
internal const val BEAM_WIDTH_DP = 1.4f
internal const val PARTIAL_BEAM_DP = 5
internal const val FLAG_LENGTH_DP = 4
internal const val FLAG_DROP_DP = 5
internal const val DOT_OFFSET_DP = 9
internal const val DOT_GAP_DP = 3.5f
internal const val DOT_RADIUS_DP = 1.3f
internal const val TIE_LIFT_DP = 8
internal const val TIE_ARC_DP = 4
internal const val PICK_ROW_DP = 20
internal const val PICK_HALF_WIDTH_DP = 2.5f
internal const val PICK_HEIGHT_DP = 5
