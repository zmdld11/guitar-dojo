package dev.zmdld11.guitardojo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.zmdld11.guitardojo.core.audio.pitch.NoteNaming
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.phrase.PhraseNote

/** 正在弹的音：音名 + 弦品，并给出循环里接下来的三个音。 */
@Composable
internal fun NowPlayingStrip(
    phrase: Phrase,
    state: PhrasePlayerUiState,
) {
    val index = state.currentNoteIndex
    val note = index?.let { phrase.notes.getOrNull(it) }
    val chord =
        remember(phrase.id, note?.tick) {
            note?.let { current -> phrase.notes.filter { it.tick == current.tick } }.orEmpty()
        }
    val upcoming =
        remember(phrase.id, index, state.loopStartBar, state.loopEndBar) {
            upcomingChords(phrase, state, index)
        }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (state.isPlaying) "正在弹" else "起始音",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (upcoming.isNotEmpty()) {
                    Text(
                        text = "接下来",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (note != null) {
                val positions = chord.joinToString(" · ") { "${it.string}弦${it.fret}品" }
                val names = chord.joinToString(" + ") { NoteNaming.labelOf(it.midi) }
                Text(
                    text = if (state.showNoteNames) names else positions,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (state.showNoteNames) positions else names,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text("—", style = MaterialTheme.typography.headlineSmall)
            }
            if (upcoming.isNotEmpty()) {
                val shown = if (upcoming.any { it.size > 1 }) upcoming.take(2) else upcoming
                Text(
                    text =
                        shown.joinToString(" → ") { chord ->
                            chord.joinToString("+") { next ->
                                if (state.showNoteNames) {
                                    NoteNaming.labelOf(next.midi)
                                } else {
                                    "${next.string}弦${next.fret}品"
                                }
                            }
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 循环区间内、当前音之后的三个「时刻」，同一拍上的和弦音会合在一起；
 * 到末尾绕回循环开头。停止时 [index] 是循环的第一个音，所以这里显示的是起手顺序。
 */
private fun upcomingChords(
    phrase: Phrase,
    state: PhrasePlayerUiState,
    index: Int?,
): List<List<PhraseNote>> {
    if (index == null || phrase.notes.isEmpty()) return emptyList()
    val bars = phrase.bars
    val startBar = (state.loopStartBar ?: 0).coerceIn(0, bars.lastIndex.coerceAtLeast(0))
    val endBar = (state.loopEndBar ?: bars.lastIndex).coerceIn(startBar, bars.lastIndex.coerceAtLeast(0))
    val from = bars.getOrNull(startBar)?.startTick ?: 0L
    val to = bars.getOrNull(endBar)?.let { it.startTick + it.lengthTicks } ?: Long.MAX_VALUE
    val loopNotes = phrase.notes.filter { it.tick in from until to }
    if (loopNotes.isEmpty()) return emptyList()
    val ticks = loopNotes.map { it.tick }.distinct()
    val currentTick = phrase.notes[index].tick
    val position = ticks.indexOf(currentTick).takeIf { it >= 0 } ?: 0
    return (1..3)
        .mapNotNull { offset -> ticks.getOrNull((position + offset) % ticks.size) }
        .map { tick -> loopNotes.filter { it.tick == tick } }
}
