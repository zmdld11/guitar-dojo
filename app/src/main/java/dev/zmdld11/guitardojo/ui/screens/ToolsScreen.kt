package dev.zmdld11.guitardojo.ui.screens

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
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zmdld11.guitardojo.data.cards.BuiltInCards
import dev.zmdld11.guitardojo.data.cards.CardDeck
import dev.zmdld11.guitardojo.data.cards.CardProgress
import dev.zmdld11.guitardojo.metronome.MetronomeHolder
import dev.zmdld11.guitardojo.ui.screens.cards.rememberCardProgress

@Composable
fun ToolsScreen(
    onOpenMetronome: () -> Unit,
    onOpenTuner: () -> Unit,
    onOpenRhythm: () -> Unit,
    onOpenCards: (CardDeck) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metronomePlaying by MetronomeHolder.isPlaying.collectAsStateWithLifecycle()
    val cardProgress = rememberCardProgress()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            text = "工具",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "练习要用到的家伙。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        ToolCard(
            title = "节拍器",
            description = "稳住拍子。",
            milestone = if (metronomePlaying) "播放中" else null,
            icon = Icons.Outlined.Timer,
            onClick = onOpenMetronome,
        )
        Spacer(Modifier.height(12.dp))
        ToolCard(
            title = "节奏对拍",
            description = "球到圆心的瞬间点下去。",
            milestone = null,
            icon = Icons.Outlined.MusicNote,
            onClick = onOpenRhythm,
        )
        Spacer(Modifier.height(12.dp))
        ToolCard(
            title = "调音器 / 拾音器",
            description = "把六根弦调准。",
            milestone = null,
            icon = Icons.Outlined.GraphicEq,
            onClick = onOpenTuner,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = "卡片",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        CardDeckCard(
            deck = CardDeck.THEORY,
            icon = Icons.Outlined.AutoStories,
            progress = cardProgress,
            onClick = { onOpenCards(CardDeck.THEORY) },
        )
        Spacer(Modifier.height(12.dp))
        CardDeckCard(
            deck = CardDeck.PRACTICE,
            icon = Icons.Outlined.Timer,
            progress = cardProgress,
            onClick = { onOpenCards(CardDeck.PRACTICE) },
        )
        Spacer(Modifier.height(12.dp))
        CardDeckCard(
            deck = CardDeck.SETUP,
            icon = Icons.Outlined.Speaker,
            progress = cardProgress,
            onClick = { onOpenCards(CardDeck.SETUP) },
        )
        Spacer(Modifier.height(12.dp))
        CardDeckCard(
            deck = CardDeck.MAINTENANCE,
            icon = Icons.Outlined.CleaningServices,
            progress = cardProgress,
            onClick = { onOpenCards(CardDeck.MAINTENANCE) },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CardDeckCard(
    deck: CardDeck,
    icon: ImageVector,
    progress: Map<String, CardProgress>,
    onClick: () -> Unit,
) {
    val cards = remember(deck) { BuiltInCards.byDeck(deck) }
    val seen = cards.count { progress[it.id]?.reviewed == true }
    ToolCard(
        title = "${deck.title}卡片",
        description = deck.subtitle,
        milestone = "$seen/${cards.size}",
        icon = icon,
        onClick = onClick,
    )
}

@Composable
private fun ToolCard(
    title: String,
    description: String,
    milestone: String?,
    icon: ImageVector,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cardModifier = modifier.fillMaxWidth()
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier =
                        Modifier
                            .padding(12.dp)
                            .size(26.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (milestone != null) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = milestone,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (onClick != null) {
        OutlinedCard(
            onClick = onClick,
            modifier = cardModifier,
            shape = MaterialTheme.shapes.large,
        ) { content() }
    } else {
        OutlinedCard(
            modifier = cardModifier,
            shape = MaterialTheme.shapes.large,
        ) { content() }
    }
}
