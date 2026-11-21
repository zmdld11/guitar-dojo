package dev.zmdld11.guitardojo.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zmdld11.guitardojo.core.audio.assess.NoteVerdict
import dev.zmdld11.guitardojo.core.audio.assess.PerformanceScores
import dev.zmdld11.guitardojo.core.audio.assess.VerdictKind
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.plan.AttemptKind
import dev.zmdld11.guitardojo.core.plan.PlanTask
import dev.zmdld11.guitardojo.core.plan.SelfRating
import dev.zmdld11.guitardojo.data.training.StepResult
import dev.zmdld11.guitardojo.ui.screens.assess.AssessmentViewModel
import kotlin.math.abs

/**
 * 考核/复习步的入口：单音乐句走跟弹自动评分，和弦乐句或没有乐句时回退自评。
 */
@Composable
internal fun AssessBranch(
    task: PlanTask,
    stepIndex: Int,
    phrase: Phrase?,
    bpm: Double,
    kind: AttemptKind,
    onComplete: (Int, StepResult) -> Unit,
) {
    var manualFallback by remember(stepIndex) { mutableStateOf(false) }
    if (phrase?.isMonophonic == true && !manualFallback) {
        AssessAutoStep(task, stepIndex, phrase, bpm, kind, onComplete, onFallback = { manualFallback = true })
    } else {
        AssessStep(task, stepIndex, phrase, bpm, kind, onComplete)
    }
}

@Composable
private fun AssessAutoStep(
    task: PlanTask,
    stepIndex: Int,
    phrase: Phrase,
    bpm: Double,
    kind: AttemptKind,
    onComplete: (Int, StepResult) -> Unit,
    onFallback: () -> Unit,
) {
    val viewModel: AssessmentViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var awaitingStart by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val shouldStart = awaitingStart
            awaitingStart = false
            if (granted && shouldStart) viewModel.startAssessment(phrase, bpm)
        }

    fun startWithPermission() {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startAssessment(phrase, bpm)
        } else {
            awaitingStart = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    TaskCard(title = "考核 · ${phrase.title}") {
        when (state.phase) {
            AssessmentViewModel.Phase.READY -> {
                StepReason(task.reason)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${bpm.toInt()} BPM · 示范静音，只放拍点",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "开始后跟拍子弹完整乐句，弹完自动评分；需要安静环境。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.message?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { startWithPermission() }) {
                        Text("开始考核")
                    }
                    OutlinedButton(onClick = onFallback) {
                        Text("不弹，改自评")
                    }
                }
            }
            AssessmentViewModel.Phase.RECORDING -> {
                Text(
                    text = "跟拍子弹完整乐句",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { state.beat.toFloat() / state.totalBeats.coerceAtLeast(1).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${state.beat}/${state.totalBeats} 拍",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.finishNow(stopped = false) }) {
                    Text("弹完了")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.finishNow(stopped = true) }) {
                    Text("中途停了")
                }
            }
            AssessmentViewModel.Phase.ANALYZING -> {
                Text(
                    text = "分析中",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator()
            }
            else -> {
                ResultContent(
                    state = state,
                    bpm = bpm,
                    onRetry = { viewModel.resetForRetry() },
                    onToggleTake = { viewModel.playTake() },
                    onToggleReference = { viewModel.playReference() },
                    onConfirm = {
                        val tempo = phrase.bars.firstOrNull()?.tempoBpm ?: bpm
                        val scores = state.scores
                        onComplete(
                            stepIndex,
                            StepResult.Assessed(
                                phraseId = taskPhraseId(task),
                                kind = kind,
                                bpm = bpm,
                                phraseTempoBpm = tempo,
                                stopped = state.stoppedEarly,
                                // 有评分时 clean/reachedTarget/rating 由 AssessPolicy 决定，这里只是占位
                                rating = SelfRating.OK,
                                accuracy = scores?.accuracy,
                                rhythm = scores?.rhythm,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultContent(
    state: AssessmentViewModel.UiState,
    bpm: Double,
    onRetry: () -> Unit,
    onToggleTake: () -> Unit,
    onToggleReference: () -> Unit,
    onConfirm: () -> Unit,
) {
    val scores = state.scores
    if (scores == null) {
        Text("没有拿到评分", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("重考一次")
        }
        return
    }
    Text(
        text =
            "总分 ${(scores.total * 100).toInt()}% · 音准 ${(scores.accuracy * 100).toInt()}% · " +
                "节奏 ${(scores.rhythm * 100).toInt()}%",
        style = MaterialTheme.typography.titleMedium,
    )
    if (state.stoppedEarly) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "中途停过，本次按停过记录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(8.dp))
    verdictLines(scores).forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onRetry) {
            Text("重考一次")
        }
        OutlinedButton(onClick = onToggleTake) {
            Text(if (state.playingTake) "停止我的录音" else "听我的录音")
        }
        OutlinedButton(onClick = onToggleReference) {
            Text(if (state.playingReference) "停止示范" else "听示范")
        }
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = onConfirm) {
        Text("确认成绩（${bpm.toInt()} BPM）")
    }
}

private fun taskPhraseId(task: PlanTask): String =
    when (task) {
        is PlanTask.Assess -> task.phraseId
        is PlanTask.Review -> task.phraseId
        else -> ""
    }

/** 错误音的展示行；全部正确时给一句汇总。 */
private fun verdictLines(scores: PerformanceScores): List<String> {
    val problems = scores.verdicts.filter { it.kind != VerdictKind.CORRECT }
    if (problems.isEmpty()) return listOf("全部 ${scores.verdicts.size} 个音符正确")
    val correctCount = scores.verdicts.size - problems.size
    val lines = problems.map { verdictText(it) }
    return if (correctCount > 0) {
        lines + listOf("其余 $correctCount 个音正确")
    } else {
        lines
    }
}

private fun verdictText(verdict: NoteVerdict): String {
    val bar = if (verdict.barIndex >= 0) "第 ${verdict.barIndex + 1} 小节 · " else ""
    return when (verdict.kind) {
        VerdictKind.WRONG_PITCH ->
            bar + verdictCentsText(verdict)
        VerdictKind.WRONG_TIMING -> {
            val deviation = verdict.onsetDeviationMs ?: 0.0
            val direction = if (deviation > 0) "晚了" else "早了"
            "${bar}起音$direction ${abs(deviation).toInt()} 毫秒"
        }
        VerdictKind.MISSING -> "${bar}漏弹"
        VerdictKind.EXTRA -> "${bar}多弹的音"
        VerdictKind.CORRECT -> ""
    }
}

private fun verdictCentsText(verdict: NoteVerdict): String {
    val cents = verdict.centsDeviation ?: 0.0
    val direction = if (cents > 0) "偏高" else "偏低"
    return "音高不对（$direction ${abs(cents).toInt()} 音分）"
}
