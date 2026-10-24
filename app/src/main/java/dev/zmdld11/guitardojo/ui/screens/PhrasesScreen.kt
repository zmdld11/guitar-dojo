package dev.zmdld11.guitardojo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.phrase.PhraseOrigin
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class PhraseFilter(
    val label: String,
) {
    ALL("全部"),
    WARMUP("热身"),
    SCALE("音阶"),
    RIFF("Riff"),
    MELODY("旋律"),
    LOCAL("本地"),
    ;

    fun matches(phrase: Phrase): Boolean =
        when (this) {
            ALL -> true
            WARMUP -> phrase.tags.any { it == "热身" || it == "爬格子" }
            SCALE -> "音阶" in phrase.tags
            RIFF -> "Riff" in phrase.tags
            MELODY -> "旋律" in phrase.tags
            LOCAL -> phrase.origin == PhraseOrigin.LOCAL
        }
}

@Composable
fun PhrasesScreen(
    onOpenPhrase: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PhraseLibraryViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filterName by rememberSaveable { mutableStateOf(PhraseFilter.ALL.name) }
    var showErrors by remember { mutableStateOf(false) }
    val filter = PhraseFilter.entries.firstOrNull { it.name == filterName } ?: PhraseFilter.ALL

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importPhrase)
        }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_MILLIS)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "乐句库",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "想弹的歌，拆成能练的小段。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新乐句库", modifier = Modifier.size(20.dp))
                }
                TextButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    },
                ) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导入")
                }
            }
            Spacer(Modifier.height(14.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 8.dp),
            ) {
                items(PhraseFilter.entries) { entry ->
                    FilterChip(
                        selected = filter == entry,
                        onClick = { filterName = entry.name },
                        label = { Text(entry.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.message?.let { message ->
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clickable { viewModel.consumeMessage() },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        if (state.errors.isNotEmpty()) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clickable { showErrors = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${state.errors.size} 个乐句文件加载失败，点此查看",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        val visible = state.phrases.filter(filter::matches)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(visible, key = { it.id }) { phrase ->
                PhraseCard(phrase = phrase, onClick = { onOpenPhrase(phrase.id) })
            }
            if (!state.isLoading && visible.isEmpty()) {
                item {
                    EmptyState(filter)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showErrors) {
        AlertDialog(
            onDismissRequest = { showErrors = false },
            title = { Text("加载失败的文件") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.errors.forEach { error ->
                        Text(error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showErrors = false }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun PhraseCard(
    phrase: Phrase,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = phrase.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = difficultyDots(phrase.difficulty),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (phrase.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = phrase.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (phrase.origin == PhraseOrigin.LOCAL) {
                    Tag("本地", MaterialTheme.colorScheme.primary)
                }
                phrase.tags.take(3).forEach { tag ->
                    Tag(tag, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = metaLine(phrase),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Tag(
    text: String,
    color: Color,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(end = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyState(filter: PhraseFilter) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (filter == PhraseFilter.LOCAL) "还没有本地乐句" else "这个分类下还没有乐句",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "点右上角「导入」选择转换好的 JSON，或把文件推到本机后刷新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun difficultyDots(difficulty: Int): String {
    val filled = difficulty.coerceIn(1, 5)
    return "●".repeat(filled) + "○".repeat(5 - filled)
}

private fun metaLine(phrase: Phrase): String {
    val bpm =
        phrase.bars
            .firstOrNull()
            ?.tempoBpm
            ?.roundToInt() ?: 120
    val seconds = phrase.approximateDurationSeconds.roundToInt()
    val duration = "%d:%02d".format(seconds / 60, seconds % 60)
    return "${phrase.bars.size} 小节 · $bpm BPM · $duration"
}

private const val MESSAGE_MILLIS = 5_000L
