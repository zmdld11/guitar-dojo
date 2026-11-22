package dev.zmdld11.guitardojo.core.plan

import kotlinx.serialization.json.Json

/** 课程文件的校验问题；加载失败时一次性展示。 */
class CurriculumException(
    val issues: List<String>,
    cause: Throwable? = null,
) : Exception(issues.joinToString("\n"), cause)

object CurriculumCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

    fun decode(text: String): Curriculum {
        val curriculum =
            try {
                json.decodeFromString<Curriculum>(text)
            } catch (error: IllegalArgumentException) {
                throw CurriculumException(listOf("JSON 格式或字段错误：${error.message ?: "无法解析"}"), error)
            }
        val issues = CurriculumValidator.validate(curriculum)
        if (issues.isNotEmpty()) throw CurriculumException(issues)
        return curriculum
    }

    fun encode(curriculum: Curriculum): String = json.encodeToString(Curriculum.serializer(), curriculum)
}

object CurriculumValidator {
    private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$")

    fun validate(curriculum: Curriculum): List<String> {
        val issues = mutableListOf<String>()

        if (curriculum.schemaVersion != 1) {
            issues += "schemaVersion 必须是 1，当前为 ${curriculum.schemaVersion}"
        }
        if (!idPattern.matches(curriculum.id)) {
            issues += "id 只能包含字母、数字、点、下划线与短横线，长度 3-64：${curriculum.id}"
        }
        if (curriculum.title.isBlank()) issues += "title 不能为空"
        if (curriculum.weeks.isEmpty()) issues += "weeks 不能为空"

        curriculum.weeks.forEachIndexed { i, week ->
            val label = "weeks[${i + 1}]"
            if (week.index != i + 1) issues += "$label.index 应为 ${i + 1}，当前为 ${week.index}"
            if (week.focus.isBlank()) issues += "$label.focus 不能为空"
            if (week.phraseIds.isEmpty()) issues += "$label.phraseIds 不能为空"
            if (week.targetBpm !in 20.0..300.0) issues += "$label.targetBpm 必须在 20-300：${week.targetBpm}"
            week.phraseIds.forEachIndexed { j, id ->
                if (id.isBlank()) issues += "$label.phraseIds[${j + 1}] 不能为空"
            }
        }
        // 跨周复用是刻意的（巩固周重弹已学句、新旧句并行）；只禁止同一周内重复。
        val weekDuplicates =
            curriculum.weeks
                .flatMap { week ->
                    week.phraseIds
                        .groupingBy { it }
                        .eachCount()
                        .filterValues { it > 1 }
                        .keys
                }.filter { it.isNotEmpty() }
        if (weekDuplicates.isNotEmpty()) issues += "同一周内乐句重复：${weekDuplicates.joinToString()}"

        return issues
    }
}
