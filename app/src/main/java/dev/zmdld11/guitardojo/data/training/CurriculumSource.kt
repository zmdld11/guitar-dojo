package dev.zmdld11.guitardojo.data.training

import android.content.Context
import dev.zmdld11.guitardojo.core.plan.Curriculum
import dev.zmdld11.guitardojo.core.plan.CurriculumCodec
import dev.zmdld11.guitardojo.core.plan.PhraseInfo
import dev.zmdld11.guitardojo.data.PhraseLibrary

/** 课程资产加载：assets/curriculum.json 是唯一事实来源。 */
object CurriculumSource {
    fun load(context: Context): Curriculum {
        val text =
            context
                .assets
                .open(ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
        return CurriculumCodec.decode(text)
    }

    private const val ASSET_PATH = "curriculum.json"
}

/**
 * 规则引擎只认 PhraseInfo；从乐句库构建。
 * 技术点标签取第一个非「热身」标签，用于换句替代与复习闸门。
 */
fun buildPhraseInfos(library: PhraseLibrary): Map<String, PhraseInfo> =
    library.phrases.associate { phrase ->
        val techniqueTag = phrase.tags.firstOrNull { it != WARMUP_TAG } ?: phrase.tags.firstOrNull()
        phrase.id to
            PhraseInfo(
                id = phrase.id,
                title = phrase.title,
                tags = phrase.tags,
                barCount = phrase.bars.size,
                tempoBpm = phrase.bars.first().tempoBpm,
                techniqueTag = techniqueTag,
            )
    }

private const val WARMUP_TAG = "热身"
