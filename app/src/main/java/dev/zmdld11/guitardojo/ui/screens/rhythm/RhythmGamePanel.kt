package dev.zmdld11.guitardojo.ui.screens.rhythm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.zmdld11.guitardojo.core.audio.phrase.PhraseTimbre
import dev.zmdld11.guitardojo.core.audio.rhythm.HitGrade
import dev.zmdld11.guitardojo.core.audio.rhythm.HitVerdict
import dev.zmdld11.guitardojo.core.audio.rhythm.RhythmEngine
import dev.zmdld11.guitardojo.core.audio.rhythm.RhythmEvent
import dev.zmdld11.guitardojo.core.audio.rhythm.RhythmLevel
import dev.zmdld11.guitardojo.core.audio.rhythm.RhythmStats
import dev.zmdld11.guitardojo.core.audio.rhythm.RhythmTimeline
import dev.zmdld11.guitardojo.core.phrase.Phrase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val FIRE_COLOR = Color(0xFFFF7043)
private val ICE_COLOR = Color(0xFF4FC3F7)

private val PI_F = PI.toFloat()

/**
 * 冰与火之舞式节奏对拍核心组件（#33）：双球绕当前圆心匀速旋转，
 * 球到达下一圆心的瞬间点屏。判定 PERFECT（±80ms）/ GOOD（±160ms）/ MISS，
 * 连续点空三次结束；走完显示命中率与早/晚偏差。
 *
 * [onFinished] 给向导步确认成绩用；工具页传 null，用自带的重开按钮。
 */
@Composable
internal fun RhythmGamePanel(
    level: RhythmLevel,
    speed: Double,
    phrase: Phrase?,
    timbre: PhraseTimbre,
    modifier: Modifier = Modifier,
    onFinished: ((RhythmStats) -> Unit)? = null,
) {
    var restartKey by remember { mutableIntStateOf(0) }
    val engine =
        remember(level.id, speed, restartKey) {
            RhythmEngine(level = level, speed = speed, phrase = phrase, timbre = timbre)
        }
    var phase by remember(restartKey) { mutableStateOf(RhythmPhase.READY) }
    var ready by remember(restartKey) { mutableStateOf(false) }
    var verdictText by remember(restartKey) { mutableStateOf("") }
    var stats by remember(restartKey) { mutableStateOf<RhythmStats?>(null) }
    var headFrame by remember(restartKey) { mutableLongStateOf(0L) }

    DisposableEffect(level.id, restartKey) {
        onDispose { engine.stop() }
    }
    LaunchedEffect(engine) {
        engine.events.collect { event ->
            when (event) {
                is RhythmEvent.TileMissed -> verdictText = "滑过去了"
                RhythmEvent.Failed -> {
                    stats = engine.stats()
                    phase = RhythmPhase.RESULT
                }
                RhythmEvent.Finished -> {
                    phase = RhythmPhase.RESULT
                    onFinished?.invoke(engine.stats() ?: return@collect)
                }
            }
        }
    }
    LaunchedEffect(phase, restartKey) {
        if (phase == RhythmPhase.PLAYING && !engine.isRunning) {
            engine.start()
            ready = engine.awaitReady()
        }
    }

    Column(modifier = modifier) {
        when (phase) {
            RhythmPhase.READY -> ReadyBody(level = level, onStart = { phase = RhythmPhase.PLAYING })
            RhythmPhase.PLAYING ->
                PlayingBody(
                    engine = engine,
                    ready = ready,
                    headFrame = headFrame,
                    onHeadFrame = { headFrame = it },
                    onTap = {
                        engine.hit()?.let { verdict -> verdictText = verdictLabel(verdict) }
                    },
                    verdictText = verdictText,
                )
            RhythmPhase.RESULT -> ResultBody(stats = stats, onRestart = { restartKey++ })
        }
    }
}

@Composable
private fun ReadyBody(
    level: RhythmLevel,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(level.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = level.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${level.bpm.toInt()} BPM · ${level.beats.size * level.repeats + 1} 拍 · 连续点空三次结束",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onStart) {
            Text("开始")
        }
    }
}

@Composable
private fun PlayingBody(
    engine: RhythmEngine,
    ready: Boolean,
    headFrame: Long,
    onHeadFrame: (Long) -> Unit,
    onTap: () -> Unit,
    verdictText: String,
) {
    LaunchedEffect(engine) {
        while (true) {
            withFrameNanos { onHeadFrame(engine.headFrame()) }
        }
    }
    RhythmTrack(
        engine = engine,
        ready = ready,
        headFrame = headFrame,
        onTap = onTap,
        modifier = Modifier.fillMaxWidth().height(300.dp),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = verdictText,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ResultBody(
    stats: RhythmStats?,
    onRestart: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (stats == null) {
            Text("本局没有结算数据")
        } else {
            Text("命中率 ${(stats.accuracy * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = statsSummary(stats),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row {
            OutlinedButton(onClick = onRestart) {
                Text("再来一局")
            }
        }
    }
}

/** 轨道画布：当前圆心 + 即将到达的圆心 + 双球。 */
@Composable
private fun RhythmTrack(
    engine: RhythmEngine,
    ready: Boolean,
    headFrame: Long,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeline = engine.timeline
    val directions = remember(timeline) { timeline?.let(::directionAngles) }
    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(ready) {
                    if (ready) {
                        detectTapGestures { onTap() }
                    }
                },
    ) {
        val activeTimeline = timeline
        val dirs = directions
        if (!ready || activeTimeline == null || dirs == null) return@Canvas
        val progress = activeTimeline.progressAt(headFrame) ?: return@Canvas
        val radius = size.minDimension / 5f
        val center = Offset(size.width / 2f, size.height / 2f)

        val nextAngle = dirs[progress.tileIndex]
        val nextCenter = center + Offset(cos(nextAngle) * radius, sin(nextAngle) * radius)
        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 18f, center = nextCenter)
        drawCircle(color = Color.Gray, radius = 18f, center = nextCenter, style = Stroke(width = 5f))

        val gapIn = if (progress.tileIndex == 0) 1.0 else activeTimeline.gaps[progress.tileIndex - 1]
        val dirIn = nextAngle - (gapIn * PI).toFloat()
        val ballAngle = dirIn + (progress.turnFraction * PI).toFloat()
        val fire = center + Offset(cos(ballAngle) * radius, sin(ballAngle) * radius)
        val ice = center + Offset(cos(ballAngle + PI_F) * radius, sin(ballAngle + PI_F) * radius)
        drawCircle(color = FIRE_COLOR, radius = 26f, center = fire)
        drawCircle(color = ICE_COLOR, radius = 26f, center = ice)
        drawCircle(color = Color.DarkGray, radius = 12f, center = center)
    }
}

private fun verdictLabel(verdict: HitVerdict): String =
    when (verdict.grade) {
        HitGrade.PERFECT -> "准了"
        HitGrade.GOOD -> {
            val ms = verdict.deviationMs.toInt()
            "偏${if (ms > 0) "晚" else "早"} ${if (ms < 0) -ms else ms} 毫秒"
        }
        HitGrade.MISS -> "点空了"
    }

private fun statsSummary(stats: RhythmStats): String =
    listOf(
        "准了 ${stats.perfectCount} · 接近 ${stats.goodCount} · 点空 ${stats.missCount}",
        driftLine(stats),
    ).filter { it.isNotBlank() }.joinToString(" · ")

private fun driftLine(stats: RhythmStats): String {
    if (stats.meanAbsDeviationMs == 0.0) return ""
    val mean = stats.meanDeviationMs.toInt()
    return "整体偏${if (mean > 0) "晚" else "早"} ${if (mean < 0) -mean else mean} 毫秒"
}

/**
 * 每个 tile 的进入方向角（弧度）：tile[i] 相对 tile[i-1] 的方向 =
 * 前 i 个间隔的累计转角。dirs[tiles] 是最后一段余韵的离开方向。
 */
private fun directionAngles(timeline: RhythmTimeline): List<Float> {
    val result = ArrayList<Float>(timeline.gaps.size + 2)
    var angle = 0f
    result.add(angle)
    for (gap in timeline.gaps) {
        angle += (gap * PI).toFloat()
        result.add(angle)
    }
    return result
}
