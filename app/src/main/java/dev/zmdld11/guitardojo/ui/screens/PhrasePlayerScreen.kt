package dev.zmdld11.guitardojo.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zmdld11.guitardojo.core.audio.phrase.PhraseTimbre
import dev.zmdld11.guitardojo.core.phrase.Phrase
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhrasePlayerScreen(
    phraseId: String,
    onBack: () -> Unit,
    initialSpeed: Double? = null,
    initialLoopBars: IntRange? = null,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: PhrasePlayerViewModel =
        viewModel(key = phraseId) {
            PhrasePlayerViewModel(application, phraseId, initialSpeed, initialLoopBars)
        }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.phrase?.title ?: "乐句",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null ->
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                    )
                state.phrase != null -> PlayerContent(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun PlayerContent(
    state: PhrasePlayerUiState,
    viewModel: PhrasePlayerViewModel,
) {
    val phrase = state.phrase ?: return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val metrics = ScoreMetrics.forWidth(maxWidth.value.toInt())
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(6.dp))
                if (phrase.description.isNotBlank()) {
                    Text(
                        text = phrase.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = "${phrase.bars.size} 小节 · 基础 ${baseBpm(phrase).roundToInt()} BPM · 难度 ${difficultyDots(phrase.difficulty)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(6.dp))
            val currentNote = state.currentNoteIndex?.let { phrase.notes.getOrNull(it) }
            PhraseScore(
                phrase = phrase,
                currentTick = currentNote?.tick,
                currentBar = state.currentBar,
                loopStartBar = state.loopStartBar,
                loopEndBar = state.loopEndBar,
                showNoteNames = state.showNoteNames,
                showPicking = state.showPicking,
                metrics = metrics,
                onSelectBar = viewModel::selectBar,
            )

            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        if (state.loopStartBar == null) {
                            "循环：整首 · 点小节选段"
                        } else {
                            val start = (state.loopStartBar ?: 0) + 1
                            val end = (state.loopEndBar ?: 0) + 1
                            if (start == end) "循环：第 $start 小节" else "循环：第 $start–$end 小节"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.loopStartBar != null) {
                    TextButton(onClick = viewModel::clearLoop) { Text("整首") }
                }
            }

            NowPlayingStrip(phrase = phrase, state = state)

            Spacer(Modifier.weight(1f))
            ControlPanel(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun ControlPanel(
    state: PhrasePlayerUiState,
    viewModel: PhrasePlayerViewModel,
) {
    val phrase = state.phrase ?: return
    val base = baseBpm(phrase)
    val actual = (base * state.speed).roundToInt()
    var sliderValue by remember(state.speed) { mutableFloatStateOf(state.speed.toFloat()) }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = viewModel::togglePlayback,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        contentDescription = if (state.isPlaying) "停止" else "播放",
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$actual BPM",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "基础 ${base.roundToInt()} × ${"%.2f".format(state.speed)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilterChip(
                    selected = state.clickEnabled,
                    onClick = viewModel::toggleClick,
                    label = { Text("节拍") },
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = state.showNoteNames,
                    onClick = viewModel::toggleNoteNames,
                    label = { Text("音名") },
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = state.showPicking,
                    onClick = viewModel::togglePicking,
                    label = { Text("拨向") },
                )
            }
            Spacer(Modifier.height(4.dp))
            val timbreIndex = PhraseTimbre.entries.indexOf(state.timbre)
            val timbreListState =
                rememberLazyListState(
                    initialFirstVisibleItemIndex = (timbreIndex - 1).coerceAtLeast(0),
                )
            LazyRow(
                state = timbreListState,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(PhraseTimbre.entries) { timbre ->
                    FilterChip(
                        selected = state.timbre == timbre,
                        onClick = { viewModel.setTimbre(timbre) },
                        label = { Text(timbre.label) },
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SPEED_PRESETS) { preset ->
                    FilterChip(
                        selected = kotlin.math.abs(state.speed - preset) < 0.001,
                        onClick = { viewModel.setSpeed(preset) },
                        label = { Text("${"%.2f".format(preset)}x") },
                    )
                }
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { viewModel.setSpeed(sliderValue.toDouble()) },
                valueRange = PhrasePlayerViewModel.MIN_SPEED.toFloat()..PhrasePlayerViewModel.MAX_SPEED.toFloat(),
                steps = SPEED_STEPS,
            )
        }
    }
}

private fun baseBpm(phrase: Phrase): Double = phrase.bars.firstOrNull()?.tempoBpm ?: 120.0

private fun difficultyDots(difficulty: Int): String {
    val filled = difficulty.coerceIn(1, 5)
    return "●".repeat(filled) + "○".repeat(5 - filled)
}

private val SPEED_PRESETS = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5)
private const val SPEED_STEPS = 24
