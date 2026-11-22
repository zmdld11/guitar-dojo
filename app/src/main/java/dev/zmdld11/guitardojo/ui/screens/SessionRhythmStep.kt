package dev.zmdld11.guitardojo.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.zmdld11.guitardojo.core.audio.rhythm.BuiltInRhythmLevels
import dev.zmdld11.guitardojo.core.audio.rhythm.RhythmStats
import dev.zmdld11.guitardojo.core.plan.PlanTask
import dev.zmdld11.guitardojo.data.TimbrePreference
import dev.zmdld11.guitardojo.data.training.StepResult
import dev.zmdld11.guitardojo.ui.screens.rhythm.RhythmGamePanel
import dev.zmdld11.guitardojo.ui.screens.session.taskLabel

/** 无琴日的节奏对拍步（#33）：按课程周挑关，内嵌冰与火之舞式点击游戏。 */
@Composable
internal fun RhythmStep(
    task: PlanTask.RhythmGame,
    stepIndex: Int,
    onComplete: (Int, StepResult) -> Unit,
) {
    val level = remember(task.weekIndex) { BuiltInRhythmLevels.forWeek(task.weekIndex) }
    val context = LocalContext.current
    val timbre = remember(context) { TimbrePreference(context).load() }
    var resultStats by remember { mutableStateOf<RhythmStats?>(null) }

    TaskCard(title = taskLabel(task)) {
        StepReason(task.reason)
        Spacer(Modifier.height(12.dp))
        RhythmGamePanel(
            level = level,
            speed = 1.0,
            phrase = null,
            timbre = timbre,
            onFinished = { stats -> resultStats = stats },
        )
        resultStats?.let { stats ->
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onComplete(
                        stepIndex,
                        StepResult.RhythmPlayed(levelId = level.id, accuracy = stats.accuracy),
                    )
                },
            ) {
                Text("确认成绩（命中 ${(stats.accuracy * 100).toInt()}%）")
            }
        }
    }
}
