package dev.zmdld11.guitardojo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zmdld11.guitardojo.core.plan.AttemptKind
import dev.zmdld11.guitardojo.core.plan.DayKind
import dev.zmdld11.guitardojo.core.plan.SelfRating
import dev.zmdld11.guitardojo.data.training.AttemptEntity
import dev.zmdld11.guitardojo.data.training.TrainingDayEntity
import dev.zmdld11.guitardojo.data.training.isValidSession
import java.time.LocalDate
import java.time.YearMonth

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CalendarViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("打卡日历") },
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
            Spacer(Modifier.height(8.dp))
            MonthHeader(
                yearMonth = state.yearMonth,
                onPrev = { viewModel.shiftMonth(-1) },
                onNext = { viewModel.shiftMonth(1) },
            )
            Spacer(Modifier.height(12.dp))
            WeekdayHeader()
            Spacer(Modifier.height(4.dp))
            MonthGrid(
                yearMonth = state.yearMonth,
                days = state.days,
                selectedDay = state.selectedDay,
                onSelect = viewModel::selectDay,
            )
            Spacer(Modifier.height(16.dp))
            DayDetail(state)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MonthHeader(
    yearMonth: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(imageVector = Icons.Filled.ChevronLeft, contentDescription = "上个月")
        }
        Text(
            text = "${yearMonth.year} 年 ${yearMonth.monthValue} 月",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext) {
            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = "下个月")
        }
    }
}

@Composable
private fun WeekdayHeader(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        WEEKDAY_LABELS.forEach { label ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    days: Map<Long, TrainingDayEntity>,
    selectedDay: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstDay = yearMonth.atDay(1)
    val offset = firstDay.dayOfWeek.value - 1
    val cells = List(offset) { 0L } + (1..yearMonth.lengthOfMonth()).map { firstDay.withDayOfMonth(it).toEpochDay() }
    val today = LocalDate.now().toEpochDay()
    val rows = cells.chunked(7)
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { epochDay ->
                    DayCell(
                        epochDay = epochDay,
                        entity = days[epochDay],
                        isToday = epochDay == today,
                        isSelected = epochDay == selectedDay,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (week.size < 7) {
                    repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    epochDay: Long,
    entity: TrainingDayEntity?,
    isToday: Boolean,
    isSelected: Boolean,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val finished = entity != null && entity.finishedAtMillis != null && isValidSession(entity)
    val dayNumber = if (epochDay > 0) LocalDate.ofEpochDay(epochDay).dayOfMonth.toString() else ""
    Box(
        modifier =
            modifier
                .aspectRatio(1.1f)
                .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = { if (epochDay > 0) onSelect(epochDay) },
            enabled = epochDay > 0,
            shape = CircleShape,
            color =
                when {
                    finished -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f)
                },
            border =
                when {
                    isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> null
                },
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = dayNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        when {
                            finished -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                )
            }
        }
    }
}

@Composable
private fun DayDetail(
    state: CalendarViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    val selectedDay = state.selectedDay ?: return
    val entity = state.days[selectedDay]
    ElevatedCard(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = LocalDate.ofEpochDay(selectedDay).let { "${it.monthValue} 月 ${it.dayOfMonth} 日" },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (entity == null) {
                Text("当天没有训练记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val kindLabel = if (entity.kind == DayKind.NO_GUITAR.name) "无琴日" else "琴上日"
            val status = if (entity.finishedAtMillis != null) "已完成" else "未完成"
            Text(
                text =
                    listOf(
                        "$kindLabel · $status",
                        "练习 ${entity.practiceSeconds / 60} 分钟",
                        if (entity.kind == DayKind.NO_GUITAR.name) {
                            ""
                        } else if (entity.tuningPassed) {
                            "已调音"
                        } else {
                            "未调音"
                        },
                        entity.note.takeIf { it.isNotBlank() }?.let { "「$it」" },
                    ).filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.selectedAttempts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                state.selectedAttempts.forEach { attempt ->
                    AttemptRow(attempt, state.phraseTitles[attempt.phraseId] ?: attempt.phraseId)
                }
            }
        }
    }
}

@Composable
private fun AttemptRow(
    attempt: AttemptEntity,
    phraseTitle: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text =
            listOfNotNull(
                if (attempt.kind == AttemptKind.REVIEW.name) "复习" else "考核",
                phraseTitle,
                "${attempt.bpm.toInt()} BPM",
                when (attempt.selfRating) {
                    SelfRating.EASY.name -> "轻松"
                    SelfRating.OK.name -> "刚好"
                    else -> "吃力"
                },
                if (attempt.clean) "干净" else "有停顿",
                attempt.scoreAccuracy?.let { "音准 ${(it * 100).toInt()}%" },
                attempt.scoreRhythm?.let { "节奏 ${(it * 100).toInt()}%" },
            ).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 2.dp),
    )
}
