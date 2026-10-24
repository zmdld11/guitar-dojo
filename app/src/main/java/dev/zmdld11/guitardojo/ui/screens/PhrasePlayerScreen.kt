package dev.zmdld11.guitardojo.ui.screens

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.phrase.PhraseBar
import dev.zmdld11.guitardojo.core.phrase.PhraseNote
import kotlin.math.roundToInt

private const val STRING_GAP_DP = 14
private const val BEAT_WIDTH_DP = 44
private const val TOP_PADDING_DP = 24
private const val SIDE_PADDING_DP = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhrasePlayerScreen(
    phraseId: String,
    onBack: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: PhrasePlayerViewModel =
        viewModel(key = phraseId) { PhrasePlayerViewModel(application, phraseId) }
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
    val listState = rememberLazyListState()
    val notesByBar = remember(phrase.id) { phrase.notes.groupBy { it.bar } }

    LaunchedEffect(state.currentBar) {
        val bar = state.currentBar ?: return@LaunchedEffect
        if (bar in phrase.bars.indices) {
            listState.animateScrollToItem(bar)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            if (phrase.description.isNotBlank()) {
                Text(
                    text = phrase.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = "${phrase.bars.size} 小节 · 基础 ${baseBpm(phrase).roundToInt()} BPM · 难度 ${difficultyDots(phrase.difficulty)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(phrase.bars, key = { it.index }) { bar ->
                TabBarCard(
                    phrase = phrase,
                    bar = bar,
                    notes = notesByBar[bar.index].orEmpty(),
                    isCurrent = state.currentBar == bar.index,
                    isLoopSelected = isBarInLoop(bar.index, state),
                    isLoopStart = state.loopStartBar == bar.index,
                    isLoopEnd = state.loopEndBar == bar.index,
                    onClick = { viewModel.selectBar(bar.index) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    if (state.loopStartBar == null) {
                        "循环：整首 · 点小节选段"
                    } else {
                        "循环：第 ${(state.loopStartBar ?: 0) + 1}–${(state.loopEndBar ?: 0) + 1} 小节"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (state.loopStartBar != null) {
                TextButton(onClick = viewModel::clearLoop) { Text("整首") }
            }
        }

        Spacer(Modifier.weight(1f))
        ControlPanel(state = state, viewModel = viewModel)
    }
}

@Composable
private fun TabBarCard(
    phrase: Phrase,
    bar: PhraseBar,
    notes: List<PhraseNote>,
    isCurrent: Boolean,
    isLoopSelected: Boolean,
    isLoopStart: Boolean,
    isLoopEnd: Boolean,
    onClick: () -> Unit,
) {
    val beats = bar.beatsPerBar.coerceAtLeast(1)
    val barWidth = (beats * BEAT_WIDTH_DP + SIDE_PADDING_DP * 2).dp
    val strings = phrase.tuning.size.coerceAtLeast(1)
    val height = (TOP_PADDING_DP + (strings - 1) * STRING_GAP_DP + 14).dp
    val background =
        when {
            isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            isLoopSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.surface
        }
    val borderColor =
        when {
            isCurrent -> MaterialTheme.colorScheme.primary
            isLoopSelected -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    val shape = RoundedCornerShape(10.dp)
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Box(
        modifier =
            Modifier
                .width(barWidth)
                .height(height)
                .background(background, shape)
                .border(if (isCurrent || isLoopSelected) 2.dp else 1.dp, borderColor, shape)
                .clickable(onClick = onClick),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = SIDE_PADDING_DP.dp.toPx()
            val right = size.width - SIDE_PADDING_DP.dp.toPx()
            val top = TOP_PADDING_DP.dp.toPx()
            val gap = STRING_GAP_DP.dp.toPx()
            for (index in 0 until strings) {
                val y = top + index * gap
                drawLine(
                    color = lineColor,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            for (beat in 1 until beats) {
                val x = left + (right - left) * beat / beats
                drawLine(
                    color = gridColor,
                    start = Offset(x, top),
                    end = Offset(x, top + (strings - 1) * gap),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        Text(
            text = "${bar.index + 1}",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.offset(x = 4.dp, y = 2.dp),
        )
        if (isLoopStart) {
            Text(
                text = "A",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 2.dp),
            )
        }
        if (isLoopEnd) {
            Text(
                text = "B",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 12.dp),
            )
        }
        notes.forEach { note ->
            val fraction = (note.tick - bar.startTick).toFloat() / bar.lengthTicks.toFloat()
            val x = (SIDE_PADDING_DP + (barWidth.value - SIDE_PADDING_DP * 2) * fraction).dp - 6.dp
            val y = (TOP_PADDING_DP + (note.string - 1) * STRING_GAP_DP).dp - 6.5.dp
            NoteChip(fret = note.fret, x = x, y = y)
        }
    }
}

@Composable
private fun NoteChip(
    fret: Int,
    x: Dp,
    y: Dp,
) {
    Box(
        modifier =
            Modifier
                .offset(x = x, y = y)
                .size(width = 12.dp, height = 13.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$fret",
            fontSize = if (fret >= 10) 7.sp else 8.sp,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
        )
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
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = viewModel::togglePlayback,
                    modifier = Modifier.size(54.dp),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        contentDescription = if (state.isPlaying) "停止" else "播放",
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
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
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

private fun isBarInLoop(
    bar: Int,
    state: PhrasePlayerUiState,
): Boolean {
    val start = state.loopStartBar ?: return false
    val end = state.loopEndBar ?: return false
    return bar in start..end
}

private fun baseBpm(phrase: Phrase): Double = phrase.bars.firstOrNull()?.tempoBpm ?: 120.0

private fun difficultyDots(difficulty: Int): String {
    val filled = difficulty.coerceIn(1, 5)
    return "●".repeat(filled) + "○".repeat(5 - filled)
}

private val SPEED_PRESETS = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5)
private const val SPEED_STEPS = 24
