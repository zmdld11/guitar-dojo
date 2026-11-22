package dev.zmdld11.guitardojo.ui.screens.rhythm

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.zmdld11.guitardojo.core.audio.rhythm.BuiltInRhythmLevels
import dev.zmdld11.guitardojo.core.audio.rhythm.RhythmAudioMode
import dev.zmdld11.guitardojo.core.audio.rhythm.RhythmLevel
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.phrase.PhraseOrigin
import dev.zmdld11.guitardojo.data.PhraseRepository
import dev.zmdld11.guitardojo.data.TimbrePreference

/** 工具页的节奏对拍：关卡列表（内置 + 乐句跟拍），选中后进入游戏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RhythmGameScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val timbre = remember(context) { TimbrePreference(context).load() }
    var selected by remember { mutableStateOf<RhythmLevel?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("节奏对拍") },
                navigationIcon = {
                    IconButton(onClick = { if (selected == null) onBack() else selected = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val level = selected
        if (level == null) {
            LevelList(
                onSelect = { selected = it },
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                RhythmGamePanel(level = level, speed = 1.0, phrase = phraseOf(level), timbre = timbre)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** 乐句跟拍关卡的伴奏乐句；内置 CLICK 关卡没有乐句。 */
@Composable
private fun phraseOf(level: RhythmLevel): Phrase? {
    val context = LocalContext.current
    if (level.audioMode != RhythmAudioMode.PHRASE) return null
    val library by produceState(initialValue = emptyList<Phrase>(), level.id) {
        value = PhraseRepository(context).load().phrases
    }
    return library.firstOrNull { it.id == level.phraseId }
}

@Composable
private fun LevelList(
    onSelect: (RhythmLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val phrases by produceState(initialValue = emptyList<Phrase>(), context) {
        value =
            PhraseRepository(context)
                .load()
                .phrases
                .filter { it.origin == PhraseOrigin.BUILTIN }
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("关卡", style = MaterialTheme.typography.titleMedium)
        BuiltInRhythmLevels.all.forEach { level ->
            LevelCard(level = level, subtitle = level.description, onClick = { onSelect(level) })
        }
        Spacer(Modifier.height(8.dp))
        Text("乐句跟拍", style = MaterialTheme.typography.titleMedium)
        phrases.forEach { phrase ->
            LevelCard(
                level = phraseLevel(phrase),
                subtitle = "每一拍点一下，${phrase.title} 当伴奏",
                onClick = { onSelect(phraseLevel(phrase)) },
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun phraseLevel(phrase: Phrase): RhythmLevel =
    RhythmLevel(
        id = "rhythm.phrase.${phrase.id}",
        title = "跟拍 · ${phrase.title}",
        description = "四分拍格，${phrase.title} 当伴奏循环。",
        bpm = phrase.bars.firstOrNull()?.tempoBpm ?: 80.0,
        beats = List(8) { 1.0 },
        repeats = 2,
        audioMode = RhythmAudioMode.PHRASE,
        phraseId = phrase.id,
    )

@Composable
private fun LevelCard(
    level: RhythmLevel,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(level.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 4.dp)) {
                Text(
                    text = "${level.bpm.toInt()} BPM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
