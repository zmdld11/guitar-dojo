package dev.zmdld11.guitardojo.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.zmdld11.guitardojo.core.phrase.Phrase

internal fun DrawScope.drawStems(
    geometry: ScoreGeometry,
    phrase: Phrase,
    attacks: Map<Long, List<DisplayNote>>,
    currentTick: Long?,
    colors: ScoreColors,
) {
    val stemTop = geometry.staffTop - SCORE_STEM_DP.dp.toPx()
    attacks.forEach { (tick, group) ->
        if (!group.first().value.stem) return@forEach
        val positions = group.map { displayPosition(geometry, phrase, it) }
        val x = positions.first().x.toPx() + STEM_OFFSET_DP.dp.toPx()
        val top = positions.minOf { it.y.toPx() }
        val color = if (tick == currentTick) colors.activeStem else colors.stem
        drawLine(
            color = color,
            start = Offset(x, top),
            end = Offset(x, stemTop),
            strokeWidth = if (tick == currentTick) 2.dp.toPx() else 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

internal fun DrawScope.drawBeamsAndFlags(
    geometry: ScoreGeometry,
    phrase: Phrase,
    attacks: Map<Long, List<DisplayNote>>,
    currentTick: Long?,
    colors: ScoreColors,
) {
    val stemTop = geometry.staffTop - SCORE_STEM_DP.dp.toPx()
    phrase.bars.forEach { bar ->
        val barEnd = bar.startTick + bar.lengthTicks
        val beatTicks = phrase.beatTicksOf(bar).coerceAtLeast(1)
        attacks
            .filterKeys { it in bar.startTick until barEnd }
            .entries
            .groupBy { ((it.key - bar.startTick) / beatTicks).toInt() }
            .forEach { (_, entries) ->
                val group =
                    entries
                        .sortedBy { it.key }
                        .map { (tick, notes) ->
                            Attack(tick, stemX(geometry, phrase, notes.first()), notes.first().value.flags)
                        }
                drawBeatGroup(group, stemTop, currentTick, colors)
            }
    }
}

private fun DrawScope.drawBeatGroup(
    attacks: List<Attack>,
    stemTop: Float,
    currentTick: Long?,
    colors: ScoreColors,
) {
    var index = 0
    while (index < attacks.size) {
        if (attacks[index].flags == 0) {
            index++
            continue
        }
        var end = index
        while (end + 1 < attacks.size && attacks[end + 1].flags > 0) end++
        val run = attacks.subList(index, end + 1)
        val color = if (run.any { it.tick == currentTick }) colors.activeStem else colors.stem
        if (run.size == 1) drawFlags(run.first(), stemTop, color) else drawBeams(run, stemTop, color)
        index = end + 1
    }
}

private fun DrawScope.drawBeams(
    run: List<Attack>,
    stemTop: Float,
    color: Color,
) {
    val maxFlags = run.maxOf { it.flags }
    for (level in 0 until maxFlags) {
        val y = stemTop + level * BEAM_GAP_DP.dp.toPx()
        var index = 0
        while (index < run.size) {
            if (run[index].flags <= level) {
                index++
                continue
            }
            var end = index
            while (end + 1 < run.size && run[end + 1].flags > level) end++
            val left = run[index].x
            val right = run[end].x
            drawLine(
                color = color,
                start = Offset(left, y),
                end = Offset(if (end > index) right else left + PARTIAL_BEAM_DP.dp.toPx(), y),
                strokeWidth = BEAM_WIDTH_DP.dp.toPx(),
                cap = StrokeCap.Round,
            )
            index = end + 1
        }
    }
}

private fun DrawScope.drawFlags(
    attack: Attack,
    stemTop: Float,
    color: Color,
) {
    for (level in 0 until attack.flags) {
        val y = stemTop + level * BEAM_GAP_DP.dp.toPx()
        drawLine(
            color = color,
            start = Offset(attack.x, y),
            end = Offset(attack.x + FLAG_LENGTH_DP.dp.toPx(), y + FLAG_DROP_DP.dp.toPx()),
            strokeWidth = BEAM_WIDTH_DP.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

internal fun DrawScope.drawDots(
    geometry: ScoreGeometry,
    phrase: Phrase,
    notes: List<DisplayNote>,
    currentTick: Long?,
    colors: ScoreColors,
) {
    notes.forEach { note ->
        val dots = note.value.dots
        if (dots == 0) return@forEach
        val position = displayPosition(geometry, phrase, note)
        val color = if (note.tick == currentTick) colors.activeStem else colors.stem
        for (dot in 0 until dots) {
            drawCircle(
                color = color,
                radius = DOT_RADIUS_DP.dp.toPx(),
                center =
                    Offset(
                        position.x.toPx() + DOT_OFFSET_DP.dp.toPx() + dot * DOT_GAP_DP.dp.toPx(),
                        position.y.toPx(),
                    ),
            )
        }
    }
}

internal fun DrawScope.drawTies(
    geometry: ScoreGeometry,
    phrase: Phrase,
    ties: List<TieSegment>,
    currentTick: Long?,
    colors: ScoreColors,
) {
    ties.forEach { tie -> drawTieSegment(geometry, phrase, tie.first, tie.second, currentTick, colors) }
}

private fun DrawScope.drawTieSegment(
    geometry: ScoreGeometry,
    phrase: Phrase,
    first: DisplayNote,
    second: DisplayNote,
    currentTick: Long?,
    colors: ScoreColors,
) {
    val start = displayPosition(geometry, phrase, first)
    val end = displayPosition(geometry, phrase, second)
    val y = start.y.toPx() - TIE_LIFT_DP.dp.toPx()
    val path =
        Path().apply {
            moveTo(start.x.toPx() + STEM_OFFSET_DP.dp.toPx(), y)
            quadraticTo(
                (start.x.toPx() + end.x.toPx()) / 2f,
                y - TIE_ARC_DP.dp.toPx(),
                end.x.toPx() - STEM_OFFSET_DP.dp.toPx(),
                y,
            )
        }
    val color = if (first.tick == currentTick) colors.activeStem else colors.stem
    drawPath(path, color, style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
}

private class Attack(
    val tick: Long,
    val x: Float,
    val flags: Int,
)
