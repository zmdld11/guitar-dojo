package dev.zmdld11.guitardojo.core.phrase

import kotlinx.serialization.json.Json

/** 一个乐句文件里的全部校验问题；加载失败时一次性展示给用户。 */
class PhraseException(
    val issues: List<String>,
    cause: Throwable? = null,
) : Exception(issues.joinToString("\n"), cause)

object PhraseCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

    fun decode(text: String): Phrase {
        val phrase =
            try {
                json.decodeFromString<Phrase>(text)
            } catch (error: IllegalArgumentException) {
                throw PhraseException(listOf("JSON 格式或字段错误：${error.message ?: "无法解析"}"), error)
            }
        val issues = PhraseValidator.validate(phrase)
        if (issues.isNotEmpty()) throw PhraseException(issues)
        return phrase
    }

    fun encode(phrase: Phrase): String = json.encodeToString(Phrase.serializer(), phrase)
}

object PhraseValidator {
    private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$")

    fun validate(phrase: Phrase): List<String> {
        val issues = mutableListOf<String>()

        if (phrase.schemaVersion != 1) {
            issues += "schemaVersion 必须是 1，当前为 ${phrase.schemaVersion}"
        }
        if (!idPattern.matches(phrase.id)) {
            issues += "id 只能包含字母、数字、点、下划线与短横线，长度 3-64：${phrase.id}"
        }
        if (phrase.title.isBlank()) issues += "title 不能为空"
        if (phrase.difficulty !in 1..5) issues += "difficulty 必须在 1-5：${phrase.difficulty}"
        if (phrase.ppq !in 96..3840) issues += "ppq 必须在 96-3840：${phrase.ppq}"
        if (phrase.tuning.size !in 4..8) {
            issues += "tuning 必须有 4-8 根弦：${phrase.tuning.size}"
        } else {
            phrase.tuning.forEachIndexed { index, midi ->
                if (midi !in 0..127) issues += "tuning 第 ${index + 1} 根弦音高越界：$midi"
                if (index > 0 && midi <= phrase.tuning[index - 1]) {
                    issues += "tuning 必须从低到高排列：第 ${index + 1} 根弦 $midi 不大于前一根"
                }
            }
        }
        if (phrase.capo !in 0..12) issues += "capo 必须在 0-12：${phrase.capo}"
        if (phrase.bars.isEmpty()) issues += "bars 不能为空"

        validateBars(phrase, issues)
        validateNotes(phrase, issues)
        return issues
    }

    private fun validateBars(
        phrase: Phrase,
        issues: MutableList<String>,
    ) {
        var expectedStart = 0L
        phrase.bars.forEachIndexed { position, bar ->
            val label = "第 ${position + 1} 小节"
            if (bar.index != position) issues += "$label：index 应为 $position，当前为 ${bar.index}"
            if (bar.startTick != expectedStart) {
                issues += "$label：startTick 应为 $expectedStart，当前为 ${bar.startTick}"
            }
            if (bar.lengthTicks <= 0) issues += "$label：lengthTicks 必须大于 0"

            val signature = bar.timeSignature
            if (signature.size != 2) {
                issues += "$label：timeSignature 必须是 [分子, 分母]"
            } else {
                val numerator = signature[0]
                val denominator = signature[1]
                if (numerator !in 1..32) issues += "$label：拍号分子超出范围：$numerator"
                if (denominator !in 1..32 || Integer.bitCount(denominator) != 1) {
                    issues += "$label：拍号分母必须是 2 的幂：$denominator"
                }
            }
            if (bar.tempoBpm !in 20.0..400.0) issues += "$label：tempoBpm 超出 20-400：${bar.tempoBpm}"
            expectedStart += bar.lengthTicks
        }
    }

    private fun validateNotes(
        phrase: Phrase,
        issues: MutableList<String>,
    ) {
        if (phrase.notes.isEmpty()) {
            issues += "notes 不能为空"
            return
        }
        val total = phrase.totalTicks
        var previousTick = -1L
        val occupied = HashSet<Pair<Int, Long>>()

        phrase.notes.forEachIndexed { index, note ->
            val label = "第 ${index + 1} 个音符"
            if (note.tick < previousTick) issues += "$label：notes 必须按 tick 升序排列"
            previousTick = note.tick
            validateTiming(note, label, total, issues)
            validatePosition(phrase, note, label, issues)
            if (!occupied.add(note.string to note.tick)) {
                issues += "$label：第 ${note.string} 弦在 tick ${note.tick} 上重复发声"
            }
        }
    }

    private fun validateTiming(
        note: PhraseNote,
        label: String,
        total: Long,
        issues: MutableList<String>,
    ) {
        if (note.durationTicks <= 0) issues += "$label：durationTicks 必须大于 0"
        if (note.tick < 0 || note.tick + note.durationTicks > total) {
            issues += "$label：tick 超出乐句总长度 $total"
        }
    }

    private fun validatePosition(
        phrase: Phrase,
        note: PhraseNote,
        label: String,
        issues: MutableList<String>,
    ) {
        if (note.string !in 1..phrase.tuning.size) {
            issues += "$label：弦号 ${note.string} 超出 tuning 范围"
            return
        }
        if (note.fret !in 0..24) issues += "$label：品位必须在 0-24：${note.fret}"
        if (note.midi !in 0..127) issues += "$label：midi 越界：${note.midi}"
        if (note.velocity !in 0f..1f) issues += "$label：velocity 必须在 0-1：${note.velocity}"

        validateBar(phrase, note, label, issues)
        note.techniques.filterNot { it in PhraseTechniques.all }.forEach {
            issues += "$label：未知技巧标记 $it"
        }
        validateSoundingPitch(phrase, note, label, issues)
    }

    private fun validateBar(
        phrase: Phrase,
        note: PhraseNote,
        label: String,
        issues: MutableList<String>,
    ) {
        val bar = phrase.bars.getOrNull(note.bar)
        if (bar == null) {
            issues += "$label：小节序号 ${note.bar} 不存在"
            return
        }
        val barEnd = bar.startTick + bar.lengthTicks
        if (note.tick < bar.startTick || note.tick >= barEnd) {
            issues += "$label：tick ${note.tick} 不在第 ${note.bar + 1} 小节内"
        }
    }

    private fun validateSoundingPitch(
        phrase: Phrase,
        note: PhraseNote,
        label: String,
        issues: MutableList<String>,
    ) {
        val sounding =
            phrase.tuning
                .getOrNull(phrase.tuning.size - note.string)
                ?.plus(phrase.capo)
                ?.plus(note.fret)
        if (sounding != null && sounding != note.midi) {
            issues +=
                "$label：弦 ${note.string} 品 ${note.fret} 应为 $sounding 音高，与 midi=${note.midi} 不一致"
        }
    }
}
