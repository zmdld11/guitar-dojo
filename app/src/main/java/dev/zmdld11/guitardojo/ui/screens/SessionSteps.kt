package dev.zmdld11.guitardojo.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import dev.zmdld11.guitardojo.core.plan.AmpPreset
import dev.zmdld11.guitardojo.core.plan.AttemptKind
import dev.zmdld11.guitardojo.core.plan.PlanTask
import dev.zmdld11.guitardojo.core.plan.SelfRating
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.data.cards.BuiltInCards
import dev.zmdld11.guitardojo.data.cards.CardDeck
import dev.zmdld11.guitardojo.data.training.StepResult
import dev.zmdld11.guitardojo.ui.screens.session.ampPresetLabel
import dev.zmdld11.guitardojo.ui.screens.session.inRestWindow
import dev.zmdld11.guitardojo.ui.screens.session.practiceTimerText
import dev.zmdld11.guitardojo.ui.screens.session.taskLabel

@Composable
internal fun SetupStep(
    task: PlanTask.Setup,
    stepIndex: Int,
    onComplete: (Int, StepResult) -> Unit,
) {
    val card =
        remember(task.ampPreset) {
            val wanted = if (task.ampPreset == AmpPreset.CLEAN) "setup.clean" else "setup.dirty"
            BuiltInCards.byDeck(CardDeck.SETUP).firstOrNull { it.id == wanted }
        }
    TaskCard(title = "设备 · ${ampPresetLabel(task.ampPreset)}") {
        StepReason(task.reason)
        if (card != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = card.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = card.answer,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onComplete(stepIndex, StepResult.Simple) }) {
            Text("完成")
        }
    }
}

@Composable
internal fun PracticeLikeStep(
    task: PlanTask,
    stepIndex: Int,
    phrase: Phrase?,
    bpm: Double,
    loopStartBar: Int,
    loopBarCount: Int,
    blocks: Int,
    enteredAt: Long,
    onComplete: (Int, StepResult) -> Unit,
    onStartPhrase: (phraseId: String, speed: Double?, loopBars: IntRange?) -> Unit,
) {
    val phraseId =
        when (task) {
            is PlanTask.Warmup -> task.phraseId
            is PlanTask.Practice -> task.phraseId
            else -> ""
        }
    val taskSpeed =
        when (task) {
            is PlanTask.Warmup -> task.speed
            is PlanTask.Practice -> task.speed
            else -> null
        }
    var elapsedSeconds by remember(stepIndex) { mutableStateOf(0L) }
    LaunchedEffect(stepIndex) {
        while (true) {
            delay(1_000)
            elapsedSeconds += 1
        }
    }
    TaskCard(title = taskLabel(task)) {
        StepReason(task.reason)
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                listOfNotNull(
                    phrase?.title,
                    "${bpm.toInt()} BPM",
                    "循环第 ${loopStartBar + 1}-${loopStartBar + loopBarCount} 小节",
                    if (blocks > 1) "$blocks 块 × 5 分钟，块间休息" else null,
                ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = practiceTimerText(elapsedSeconds, blocks),
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (inRestWindow(elapsedSeconds, blocks)) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { onStartPhrase(phraseId, taskSpeed, loopStartBar..(loopStartBar + loopBarCount - 1)) },
                enabled = phraseId.isNotEmpty(),
            ) {
                Text("打开播放器")
            }
            Button(
                onClick = {
                    val seconds = (SystemClock.elapsedRealtime() - enteredAt) / 1000
                    onComplete(stepIndex, StepResult.PracticeDone(seconds))
                },
            ) {
                Text("练完了")
            }
        }
    }
}

@Composable
internal fun AssessStep(
    task: PlanTask,
    stepIndex: Int,
    phrase: Phrase?,
    bpm: Double,
    kind: AttemptKind,
    onComplete: (Int, StepResult) -> Unit,
) {
    val phraseId =
        when (task) {
            is PlanTask.Assess -> task.phraseId
            is PlanTask.Review -> task.phraseId
            else -> ""
        }
    val title = taskLabel(task)
    val tempo = phrase?.bars?.firstOrNull()?.tempoBpm ?: bpm
    var rating by remember { mutableStateOf<SelfRating?>(null) }
    var stopped by remember { mutableStateOf(false) }
    TaskCard(title = title) {
        StepReason(task.reason)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${phrase?.title ?: phraseId} · ${bpm.toInt()} BPM",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text("自评", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelfRating.entries.forEach { item ->
                FilterChip(
                    selected = rating == item,
                    onClick = { rating = item },
                    label = {
                        Text(
                            when (item) {
                                SelfRating.EASY -> "轻松"
                                SelfRating.OK -> "刚好"
                                SelfRating.HARD -> "吃力"
                            },
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = stopped, onCheckedChange = { stopped = it })
            Text("中途停过", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val item = rating ?: return@Button
                onComplete(
                    stepIndex,
                    StepResult.Assessed(
                        phraseId = phraseId,
                        kind = kind,
                        bpm = bpm,
                        phraseTempoBpm = tempo,
                        stopped = stopped,
                        rating = item,
                    ),
                )
            },
            enabled = rating != null,
        ) {
            Text("完成$title")
        }
    }
}

@Composable
internal fun CardStep(
    title: String,
    cardId: String,
    deck: CardDeck,
    stepIndex: Int,
    onComplete: (Int, StepResult) -> Unit,
    onOpenCardDeck: (CardDeck) -> Unit,
) {
    TaskCard(title = title) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onOpenCardDeck(deck) }) {
                Text("打开卡片")
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onComplete(stepIndex, StepResult.CardRated(cardId, remembered = true)) }) {
                Text("记住了")
            }
            OutlinedButton(onClick = { onComplete(stepIndex, StepResult.CardRated(cardId, remembered = false)) }) {
                Text("还没记住")
            }
        }
    }
}

@Composable
internal fun MentalStep(
    task: PlanTask.MentalPractice,
    stepIndex: Int,
    phrase: Phrase?,
    onComplete: (Int, StepResult) -> Unit,
) {
    TaskCard(title = "心理练习") {
        StepReason(task.reason)
        Spacer(Modifier.height(8.dp))
        if (phrase != null) {
            Text(
                text = "${phrase.title} · ${task.minutes} 分钟",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
        Button(onClick = { onComplete(stepIndex, StepResult.Simple) }) {
            Text("完成")
        }
    }
}

@Composable
internal fun NoteStep(
    stepIndex: Int,
    onComplete: (Int, StepResult) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    TaskCard(title = "记录") {
        StepReason("写一句下次要注意什么")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onComplete(stepIndex, StepResult.NoteSaved(text)) },
            enabled = text.isNotBlank(),
        ) {
            Text("保存")
        }
    }
}

@Composable
internal fun SimpleStep(
    task: PlanTask,
    stepIndex: Int,
    onComplete: (Int, StepResult) -> Unit,
) {
    TaskCard(title = taskLabel(task)) {
        StepReason(task.reason)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onComplete(stepIndex, StepResult.Simple) }) {
            Text("完成")
        }
    }
}

@Composable
internal fun TaskCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
internal fun StepReason(reason: String) {
    Text(
        text = reason,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
