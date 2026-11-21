package dev.zmdld11.guitardojo.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zmdld11.guitardojo.core.plan.AttemptKind
import dev.zmdld11.guitardojo.core.plan.PlanTask
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.data.cards.CardDeck
import dev.zmdld11.guitardojo.data.training.StepResult
import dev.zmdld11.guitardojo.ui.screens.session.ampPresetLabel
import dev.zmdld11.guitardojo.ui.screens.session.taskLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    onBack: () -> Unit,
    onStartPhrase: (String) -> Unit,
    onOpenCardDeck: (CardDeck) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SessionViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("今日训练") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
        ) {
            when {
                state.loading -> Text("读取计划中", style = MaterialTheme.typography.bodyLarge)
                state.error != null -> Text(
                    text = "计划加载失败：${state.error}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> {
                    val snapshot = state.snapshot ?: return@Column
                    val plan = snapshot.plan
                    val stepIndex = snapshot.entity.currentStep
                    val task = plan.tasks.getOrNull(stepIndex) ?: return@Column
                    // 步骤进入时刻，练习类步骤完成时累计有效练习时长
                    val enteredAt = remember(stepIndex) { SystemClock.elapsedRealtime() }
                    StepHeader(
                        stepNumber = stepIndex + 1,
                        totalSteps = plan.tasks.size,
                        label = taskLabel(task),
                    )
                    Spacer(Modifier.height(16.dp))
                    TaskContent(
                        task = task,
                        stepIndex = stepIndex,
                        phrase = state.currentPhrase,
                        enteredAt = enteredAt,
                        onComplete = viewModel::completeStep,
                        onStartPhrase = onStartPhrase,
                        onOpenCardDeck = onOpenCardDeck,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepHeader(
    stepNumber: Int,
    totalSteps: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "第 $stepNumber/$totalSteps 步 · $label",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { stepNumber.toFloat() / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TaskContent(
    task: PlanTask,
    stepIndex: Int,
    phrase: Phrase?,
    enteredAt: Long,
    onComplete: (Int, StepResult) -> Unit,
    onStartPhrase: (String) -> Unit,
    onOpenCardDeck: (CardDeck) -> Unit,
) {
    when (task) {
        is PlanTask.Tune -> TuneStep(task, stepIndex, onComplete)
        is PlanTask.Setup -> SetupStep(task, stepIndex, onComplete)
        is PlanTask.Warmup ->
            PracticeLikeStep(
                task = task,
                stepIndex = stepIndex,
                phrase = phrase,
                bpm = task.bpm,
                loopStartBar = task.loopStartBar,
                loopBarCount = task.loopBarCount,
                blocks = 1,
                enteredAt = enteredAt,
                onComplete = onComplete,
                onStartPhrase = onStartPhrase,
            )
        is PlanTask.Practice ->
            PracticeLikeStep(
                task = task,
                stepIndex = stepIndex,
                phrase = phrase,
                bpm = task.bpm,
                loopStartBar = task.loopStartBar,
                loopBarCount = task.loopBarCount,
                blocks = task.blocks,
                enteredAt = enteredAt,
                onComplete = onComplete,
                onStartPhrase = onStartPhrase,
            )
        is PlanTask.Review ->
            AssessStep(
                task = task,
                stepIndex = stepIndex,
                phrase = phrase,
                bpm = task.bpm,
                kind = AttemptKind.REVIEW,
                onComplete = onComplete,
            )
        is PlanTask.Assess ->
            AssessStep(
                task = task,
                stepIndex = stepIndex,
                phrase = phrase,
                bpm = task.targetBpm,
                kind = AttemptKind.PRACTICE,
                onComplete = onComplete,
            )
        is PlanTask.TheoryCard -> CardStep("乐理卡", task.cardId, CardDeck.THEORY, stepIndex, onComplete, onOpenCardDeck)
        is PlanTask.PracticeCard -> CardStep("练法卡", task.cardId, CardDeck.PRACTICE, stepIndex, onComplete, onOpenCardDeck)
        is PlanTask.MentalPractice -> MentalStep(task, stepIndex, phrase, onComplete)
        is PlanTask.NoteEntry -> NoteStep(stepIndex, onComplete)
        is PlanTask.Cooldown -> SimpleStep(task, stepIndex, onComplete)
    }
}
