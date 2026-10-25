package dev.zmdld11.guitardojo.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import dev.zmdld11.guitardojo.core.phrase.Phrase

internal fun DrawScope.drawPickMarks(
    geometry: ScoreGeometry,
    phrase: Phrase,
    marks: List<PickMark>,
    currentTick: Long?,
    colors: ScoreColors,
) {
    marks.forEach { mark ->
        val position = displayPosition(geometry, phrase, mark.tick, mark.string)
        val color = if (mark.tick == currentTick) colors.activeStem else colors.stem
        drawPickGlyph(position.x.toPx(), PICK_ROW_DP.dp.toPx(), mark.direction, color)
    }
}

private fun DrawScope.drawPickGlyph(
    x: Float,
    top: Float,
    direction: PickDirection,
    color: Color,
) {
    val half = PICK_HALF_WIDTH_DP.dp.toPx()
    val height = PICK_HEIGHT_DP.dp.toPx()
    val stroke = 1.dp.toPx()
    if (direction == PickDirection.DOWN) {
        drawLine(color, Offset(x - half, top), Offset(x + half, top), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(x - half, top), Offset(x - half, top + height), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(x + half, top), Offset(x + half, top + height), stroke, cap = StrokeCap.Round)
    } else {
        drawLine(color, Offset(x - half, top), Offset(x, top + height), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(x + half, top), Offset(x, top + height), stroke, cap = StrokeCap.Round)
    }
}
