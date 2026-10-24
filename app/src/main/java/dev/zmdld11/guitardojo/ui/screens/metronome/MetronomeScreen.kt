package dev.zmdld11.guitardojo.ui.screens.metronome

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zmdld11.guitardojo.core.audio.metronome.MetronomeConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MetronomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { viewModel.togglePlayback() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("节拍器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            MetronomePlaybackBar(
                isPlaying = state.isPlaying,
                onTap = viewModel::tap,
                onToggle = {
                    val needsPermission =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission && !state.isPlaying) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.togglePlayback()
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
            Spacer(Modifier.height(24.dp))
            BpmReadout(
                bpm = state.bpm,
                pulseCount = state.pulseCount,
                isPlaying = state.isPlaying,
            )
            Spacer(Modifier.height(16.dp))
            BeatDots(
                beatsPerBar = state.beatsPerBar,
                activeBeat = state.activeBeat,
                isPlaying = state.isPlaying,
            )
            Spacer(Modifier.height(20.dp))
            Slider(
                value = state.bpm.toFloat(),
                onValueChange = { viewModel.setBpm(it.toInt()) },
                valueRange = MetronomeConfig.MIN_BPM.toFloat()..MetronomeConfig.MAX_BPM.toFloat(),
            )
            Spacer(Modifier.height(4.dp))
            BpmSteppers(onAdjust = viewModel::adjustBpm)
            Spacer(Modifier.height(28.dp))
            TempoSettings(
                beatsPerBar = state.beatsPerBar,
                subdivisions = state.subdivisions,
                accentFirstBeat = state.accentFirstBeat,
                onBeatsPerBarChange = viewModel::setBeatsPerBar,
                onSubdivisionsChange = viewModel::setSubdivisions,
                onAccentToggle = viewModel::toggleAccent,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BpmReadout(
    bpm: Int,
    pulseCount: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1f) }

    LaunchedEffect(pulseCount) {
        if (pulseCount > 0 && isPlaying) {
            scale.snapTo(1.06f)
            scale.animateTo(1f, animationSpec = tween(durationMillis = 160))
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = bpm.toString(),
            fontSize = 88.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
        Text(
            text = "BPM",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BeatDots(
    beatsPerBar: Int,
    activeBeat: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(beatsPerBar) { index ->
            val active = isPlaying && index == activeBeat
            val color by animateColorAsState(
                targetValue =
                    when {
                        active -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                animationSpec = tween(durationMillis = 90),
                label = "beatDotColor",
            )
            val dotSize by animateDpAsState(
                targetValue = if (active) 15.dp else 9.dp,
                animationSpec = tween(durationMillis = 90),
                label = "beatDotSize",
            )
            Surface(
                modifier =
                    Modifier
                        .padding(horizontal = 5.dp)
                        .size(dotSize),
                shape = CircleShape,
                color = color,
            ) {}
        }
    }
}

@Composable
private fun BpmSteppers(
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        listOf(-5, -1, 1, 5).forEach { delta ->
            FilledTonalButton(
                onClick = { onAdjust(delta) },
                modifier = Modifier.height(46.dp),
            ) {
                Text(if (delta > 0) "+$delta" else delta.toString())
            }
        }
    }
}

@Composable
private fun TempoSettings(
    beatsPerBar: Int,
    subdivisions: Int,
    accentFirstBeat: Boolean,
    onBeatsPerBarChange: (Int) -> Unit,
    onSubdivisionsChange: (Int) -> Unit,
    onAccentToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "每小节拍数",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onBeatsPerBarChange(beatsPerBar - 1) },
                    enabled = beatsPerBar > MetronomeConfig.MIN_BEATS_PER_BAR,
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "减少拍数")
                }
                Text(
                    text = beatsPerBar.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                IconButton(
                    onClick = { onBeatsPerBarChange(beatsPerBar + 1) },
                    enabled = beatsPerBar < MetronomeConfig.MAX_BEATS_PER_BAR,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "增加拍数")
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(text = "每拍细分", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUBDIVISION_LABELS.forEachIndexed { index, label ->
                    val value = index + 1
                    FilterChip(
                        selected = subdivisions == value,
                        onClick = { onSubdivisionsChange(value) },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "重读第一拍", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "每小节第一拍用更高的音色",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = accentFirstBeat, onCheckedChange = { onAccentToggle() })
            }
        }
    }
}

@Composable
private fun MetronomePlaybackBar(
    isPlaying: Boolean,
    onTap: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onTap,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp),
            ) {
                Text("Tap 定速")
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            ) {
                IconButton(onClick = onToggle, modifier = Modifier.size(64.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "停止" else "开始",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

private val SUBDIVISION_LABELS = listOf("四分", "八分", "三连", "十六分")
