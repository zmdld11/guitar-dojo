package dev.zmdld11.guitardojo.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zmdld11.guitardojo.core.audio.pitch.GuitarTuning
import dev.zmdld11.guitardojo.core.audio.pitch.MusicalNote
import dev.zmdld11.guitardojo.core.plan.PlanTask
import dev.zmdld11.guitardojo.data.training.StepResult
import dev.zmdld11.guitardojo.ui.screens.tuner.TunerViewModel
import kotlin.math.abs

@Composable
internal fun TuneStep(
    task: PlanTask.Tune,
    stepIndex: Int,
    onComplete: (Int, StepResult) -> Unit,
) {
    val tuner: TunerViewModel = viewModel()
    val state by tuner.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        tuner.startGuidedTuning()
        onDispose { tuner.stopGuidedTuning() }
    }
    // 六弦依次准了之后自动进入下一步
    LaunchedEffect(state.guidedDone) {
        if (state.guidedDone) onComplete(stepIndex, StepResult.TuneDone(passed = true))
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) tuner.startGuidedTuning()
        }
    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    TaskCard(title = "调音") {
        StepReason(task.reason)
        Spacer(Modifier.height(12.dp))
        GuidedStringStrip(targetNumber = state.guidedTarget?.number)
        Spacer(Modifier.height(16.dp))
        Text(
            text = guidedTargetText(state.guidedDone, state.guidedTarget),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = guidedReadoutText(state.guidedInTune, state.note),
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (state.guidedInTune) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        state.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onComplete(stepIndex, StepResult.TuneDone(passed = false)) }) {
                Text("先不调音")
            }
        }
    }
}

private fun guidedTargetText(
    guidedDone: Boolean,
    target: dev.zmdld11.guitardojo.core.audio.pitch.GuitarString?,
): String =
    when {
        guidedDone -> "六弦全部调准"
        else -> target?.let { "第 ${it.number} 弦 · ${it.noteLabel}" } ?: "等待声音"
    }

private fun guidedReadoutText(
    guidedInTune: Boolean,
    note: MusicalNote?,
): String =
    when {
        note == null -> "拨一根弦"
        guidedInTune -> "准了，保持"
        abs(note.cents) <= MusicalNote.DEFAULT_TOLERANCE_CENTS -> note.label
        note.cents > 0 -> "${note.label} 偏高 ${"%.0f".format(note.cents)} 音分"
        else -> "${note.label} 偏低 ${"%.0f".format(-note.cents)} 音分"
    }

@Composable
private fun GuidedStringStrip(
    targetNumber: Int?,
    modifier: Modifier = Modifier,
) {
    val targetIndex = targetNumber?.let { target -> GuitarTuning.standard.indexOfFirst { it.number == target } }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GuitarTuning.standard.forEachIndexed { index, string ->
            val state =
                when {
                    targetIndex == null -> GuidedStringState.PENDING
                    index < targetIndex -> GuidedStringState.DONE
                    index == targetIndex -> GuidedStringState.CURRENT
                    else -> GuidedStringState.PENDING
                }
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                color =
                    when (state) {
                        GuidedStringState.CURRENT -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = string.number.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = string.noteLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color =
                            when (state) {
                                GuidedStringState.CURRENT -> MaterialTheme.colorScheme.onPrimaryContainer
                                GuidedStringState.DONE -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

private enum class GuidedStringState { DONE, CURRENT, PENDING }
