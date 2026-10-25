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
    barStarts: List<Dp>,
    barWidths: List<Dp>,
    loopStartBar: Int?,
    loopEndBar: Int?,
    currentBar: Int?,
    currentTick: Long?,
) {
    val colors =
        ScoreColors(
            line = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
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
        drawStems(geometry, phrase, currentTick, colors)
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
    phrase.bars.forEachIndexed { index, bar ->
        val startX = geometry.barStarts[index].toPx()
        val widthPx = geometry.barWidths[index].toPx()
        val beats = bar.beatsPerBar.coerceAtLeast(1)
        for (beat in 1 until beats) {
            val x = startX + widthPx * beat / beats
            drawLine(
                color = colors.grid,
                start = Offset(x, geometry.staffTop),
                end = Offset(x, geometry.staffBottom),
                strokeWidth = 0.7.dp.toPx(),
            )
        }
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

private fun DrawScope.drawStems(
    geometry: ScoreGeometry,
    phrase: Phrase,
    currentTick: Long?,
    colors: ScoreColors,
) {
    val stemTop = geometry.staffTop - SCORE_STEM_DP.dp.toPx()
    phrase.notes.groupBy { it.tick }.forEach { (tick, group) ->
        val position = notePosition(phrase, group.first(), geometry.barStarts, geometry.barWidths)
        val x = position.x.toPx()
        val highlighted = tick == currentTick
        val color = if (highlighted) colors.activeStem else colors.stem
        drawLine(
            color = color,
            start = Offset(x, geometry.staffTop),
            end = Offset(x, stemTop),
            strokeWidth = if (highlighted) 2.dp.toPx() else 1.dp.toPx(),
        )
        val flags = stemFlags(group.minOf { it.durationTicks }, phrase.ppq)
        for (flag in 0 until flags) {
            val flagY = stemTop + flag * 5.dp.toPx()
            drawLine(
                color = color,
                start = Offset(x, flagY),
                end = Offset(x + 4.dp.toPx(), flagY + 5.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

private class ScoreGeometry(
    val staffTop: Float,
    val gap: Float,
    val strings: Int,
    val barStarts: List<Dp>,
    val barWidths: List<Dp>,
) {
    val staffBottom: Float get() = staffTop + (strings - 1) * gap
}

private class ScoreColors(
    val line: Color,
    val grid: Color,
    val bar: Color,
    val loop: Color,
    val current: Color,
    val stem: Color,
    val activeStem: Color,
)
