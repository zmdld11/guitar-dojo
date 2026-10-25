package dev.zmdld11.guitardojo.ui.screens.cards

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.zmdld11.guitardojo.data.cards.BuiltInCards
import dev.zmdld11.guitardojo.data.cards.CardDeck
import dev.zmdld11.guitardojo.data.cards.CardProgressStore
import dev.zmdld11.guitardojo.data.cards.CardScheduler
import dev.zmdld11.guitardojo.data.cards.TrainingCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    deck: CardDeck,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { CardProgressStore(context) }
    val scheduler = remember { CardScheduler() }
    val cards = remember(deck) { BuiltInCards.byDeck(deck) }
    val today = remember { LocalDate.now().toEpochDay() }
    val initialProgress = remember(store) { store.load() }
    val queue = remember(deck, initialProgress, today) { scheduler.order(cards, initialProgress, today) }

    var progress by remember(deck) { mutableStateOf(initialProgress) }
    var index by rememberSaveable(deck) { mutableStateOf(0) }
    var revealed by rememberSaveable(deck) { mutableStateOf(false) }
    var finished by rememberSaveable(deck) { mutableStateOf(false) }

    val reviewedCount = cards.count { progress[it.id]?.reviewed == true }
    val masteredCount = cards.count { scheduler.isMastered(progress[it.id]) }
    val current = queue.getOrNull(index)

    fun rate(remembered: Boolean) {
        val card = current ?: return
        val updated = scheduler.review(progress[card.id], card.id, today, remembered)
        store.save(updated)
        progress = progress + (card.id to updated)
        if (index >= queue.lastIndex) {
            finished = true
        } else {
            index += 1
            revealed = false
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${deck.title}卡片")
                        Text(
                            text = "${cards.size} 张",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
                    .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (finished) "完成" else "第 ${index + 1} / ${queue.size} 张",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "看过 $reviewedCount · 掌握 $masteredCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (queue.isEmpty()) 0f else (index + 1f) / queue.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            if (finished || current == null) {
                CompletionState(
                    reviewed = reviewedCount,
                    total = cards.size,
                    onRestart = {
                        index = 0
                        revealed = false
                        finished = false
                    },
                    onBack = onBack,
                    modifier = Modifier.weight(1f),
                )
            } else {
                CardFace(
                    card = current,
                    revealed = revealed,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.height(16.dp))
                if (revealed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { rate(false) },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text("还没记住")
                        }
                        Button(
                            onClick = { rate(true) },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text("记住了")
                        }
                    }
                } else {
                    Button(
                        onClick = { revealed = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text("看答案")
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CardFace(
    card: TrainingCard,
    revealed: Boolean,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
        ) {
            Text(
                text = card.deck.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = card.prompt,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (revealed) {
                Spacer(Modifier.height(22.dp))
                HorizontalDivider()
                Spacer(Modifier.height(22.dp))
                Text(
                    text = card.answer,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (card.detail.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = card.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                card.figure?.let { path ->
                    CardFigure(path = path, description = card.title)
                }
            }
        }
    }
}

@Composable
private fun CompletionState(
    reviewed: Int,
    total: Int,
    onRestart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "这一组看完啦 🎸",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "看过 $reviewed / $total 张",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRestart) { Text("再看一遍") }
        TextButton(onClick = onBack) { Text("返回") }
    }
}

@Composable
private fun CardFigure(
    path: String,
    description: String,
) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, path) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open(path).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
    }

    image?.let {
        Spacer(Modifier.height(20.dp))
        Image(
            bitmap = it,
            contentDescription = description,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Fit,
        )
    }
}
