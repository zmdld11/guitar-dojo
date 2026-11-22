package dev.zmdld11.guitardojo.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zmdld11.guitardojo.core.plan.PlanTask
import dev.zmdld11.guitardojo.data.training.StepResult
import dev.zmdld11.guitardojo.ui.screens.session.taskLabel

/**
 * 无琴日的节奏对拍步（#33）：内嵌「冰与火之舞」式点击游戏。
 * 当前为占位版：只保留完成推进，游戏界面随后接入。
 */
@Composable
internal fun RhythmStep(
    task: PlanTask.RhythmGame,
    stepIndex: Int,
    onComplete: (Int, StepResult) -> Unit,
) {
    TaskCard(title = taskLabel(task)) {
        Column {
            StepReason(task.reason)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onComplete(stepIndex, StepResult.RhythmPlayed(levelId = "", accuracy = 0.0)) }) {
                Text("完成")
            }
        }
    }
}
