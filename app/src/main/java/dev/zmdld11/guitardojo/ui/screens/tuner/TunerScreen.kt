package dev.zmdld11.guitardojo.ui.screens.tuner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zmdld11.guitardojo.core.audio.pitch.GuitarTuning
import dev.zmdld11.guitardojo.core.audio.pitch.MusicalNote
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TunerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) viewModel.start()
        }

    fun requestStart() {
        val granted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.start() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("调音器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            ModeChips(mode = state.mode, onSelect = viewModel::setMode)
            Spacer(Modifier.height(28.dp))
            NoteReadout(note = state.note, mode = state.mode)
            Spacer(Modifier.height(8.dp))
            CentsGauge(cents = state.note?.cents, active = state.note != null)
            Spacer(Modifier.height(24.dp))

            when (state.mode) {
                TunerMode.GUITAR -> StringStrip(matchedStringNumber = state.matchedString?.number)
                TunerMode.PICKUP -> PickupHistory(history = state.history)
                TunerMode.CHROMATIC -> Unit
            }

            Spacer(Modifier.height(20.dp))
            if (!state.isListening) {
                state.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Button(onClick = ::requestStart, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("开始聆听")
                }
            } else {
                Button(onClick = viewModel::stop, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.MicOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("停止")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeChips(
    mode: TunerMode,
    onSelect: (TunerMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TunerMode.entries.forEach { candidate ->
            FilterChip(
                selected = candidate == mode,
                onClick = { onSelect(candidate) },
                label = { Text(candidate.label) },
            )
        }
    }
}

@Composable
private fun NoteReadout(
    note: MusicalNote?,
    mode: TunerMode,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = note?.label ?: "···",
            fontSize = if (note == null) 60.sp else 84.sp,
            fontWeight = FontWeight.SemiBold,
            color =
                if (note != null) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Text(
            text =
                when {
                    note == null && mode == TunerMode.PICKUP -> "拨一根弦，看看它是什么音"
                    note == null -> "等待声音…"
                    abs(note.cents) <= MusicalNote.DEFAULT_TOLERANCE_CENTS -> "准了"
                    note.cents > 0 -> "偏高 ${"%.0f".format(note.cents)} 音分"
                    else -> "偏低 ${"%.0f".format(-note.cents)} 音分"
                },
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (note != null && abs(note.cents) <= MusicalNote.DEFAULT_TOLERANCE_CENTS) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun CentsGauge(
    cents: Double?,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.outline
    val indicatorColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(72.dp),
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val halfWidth = size.width / 2 - 14.dp.toPx()
        drawLine(trackColor, Offset(0f, centerY), Offset(size.width, centerY), 3.dp.toPx(), StrokeCap.Round)

        for (step in -5..5) {
            val x = centerX + step / 5f * halfWidth
            val tickHeight = if (step == 0) 24.dp.toPx() else 10.dp.toPx()
            drawLine(
                color = if (step == 0) indicatorColor.copy(alpha = 0.5f) else tickColor,
                start = Offset(x, centerY - tickHeight / 2),
                end = Offset(x, centerY + tickHeight / 2),
                strokeWidth = if (step == 0) 3.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        if (active && cents != null) {
            val clamped = cents.coerceIn(-50.0, 50.0)
            val x = centerX + (clamped / 50.0).toFloat() * halfWidth
            drawCircle(indicatorColor, radius = 10.dp.toPx(), center = Offset(x, centerY))
        }
    }
}

@Composable
private fun StringStrip(
    matchedStringNumber: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GuitarTuning.standard.forEach { string ->
            val matched = string.number == matchedStringNumber
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                color =
                    if (matched) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
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
                            if (matched) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickupHistory(
    history: List<String>,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "最近听到的音", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(10.dp))
            if (history.isEmpty()) {
                Text(
                    text = "弹几个音，这里会记下它们的音名。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = history.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
