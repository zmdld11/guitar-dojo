package dev.zmdld11.guitardojo.core.plan

/** 主练句选择结果。 */
internal data class SelectedMain(
    val phraseId: String,
    val bpm: Double,
    val reason: String,
    val focusNotes: String = "",
)

/** 热身句选择结果。 */
internal data class WarmupPick(
    val phraseId: String,
    val bpm: Double,
    val reason: String,
)

/** 复习句选择结果。 */
internal data class ReviewPick(
    val phraseId: String,
    val bpm: Double,
    val reason: String,
)

/** 考核句选择结果。 */
internal data class AssessPick(
    val phraseId: String,
    val targetBpm: Double,
    val reason: String,
)

/** 课程里按周序展开的全部乐句 id；跨周复用句去重。 */
internal fun curriculumOrder(curriculum: Curriculum): List<String> =
    curriculum
        .weeks
        .sortedBy { it.index }
        .flatMap { it.phraseIds }
        .distinct()

/**
 * 从课程与历史里挑今天练什么。只读输入，不写任何状态。
 * 优先级：昨天未通过 → 复习失败重练 → 课程新句，依据 docs/M4-每日训练流设计.md §3.3；
 * 理由必须来自历史数据，见 docs/训练计划设计.md §4.5。
 */
private fun PhraseProgress.isFailing(input: PlanInput): Boolean =
    consecutiveFails > 0 && mastery != Mastery.MASTERED && input.phrases.containsKey(phraseId)

private fun PhraseProgress.isRelearnDue(input: PlanInput): Boolean =
    mastery != Mastery.MASTERED &&
        nextReviewEpochDay != null &&
        nextReviewEpochDay <= input.todayEpochDay &&
        input.phrases.containsKey(phraseId)

private fun PhraseProgress.isOverdueReview(
    input: PlanInput,
    main: SelectedMain?,
): Boolean =
    mastery == Mastery.MASTERED &&
        nextReviewEpochDay != null &&
        nextReviewEpochDay <= input.todayEpochDay &&
        phraseId != main?.phraseId &&
        input.phrases.containsKey(phraseId)

/** 热身/爬格子标签命中即视为热身句。 */
private val warmupTags = listOf("热身", "爬格子")

internal class PhraseSelector(
    private val rules: PlanRules = PlanRules(),
) {
    fun selectMain(
        input: PlanInput,
        warnings: MutableList<String>,
    ): SelectedMain? {
        val failing =
            input.progress.values
                .filter { it.isFailing(input) }
                .maxByOrNull { it.lastPracticedEpochDay ?: Long.MIN_VALUE }
        failing?.let { return selectFailing(input, it) }

        val relearn =
            input.progress.values
                .filter { it.isRelearnDue(input) }
                .minByOrNull { it.nextReviewEpochDay ?: Long.MAX_VALUE }
        relearn?.let {
            return SelectedMain(it.phraseId, it.currentBpm, "上次复习未通过，今天在 ${it.currentBpm.toInt()} BPM 重练")
        }

        val entry =
            currentCurriculumEntry(input, warnings) ?: run {
                warnings += "课程乐句已全部掌握，今天只排热身与复习"
                return null
            }
        val (week, phraseId) = entry
        val info = input.phrases.getValue(phraseId)
        val progress = input.progress[phraseId]
        val bpm = progress?.currentBpm ?: info.tempoBpm * rules.startSpeedRatio
        val reason =
            if (progress == null) {
                "本周技术点「${week.focus}」；${info.title.ifEmpty { phraseId }} 首次练习，从 ${(rules.startSpeedRatio * 100).toInt()}% 速度起步"
            } else {
                "本周技术点「${week.focus}」；${info.title.ifEmpty { phraseId }} 继续在 ${bpm.toInt()} BPM 练"
            }
        return SelectedMain(phraseId, bpm, reason, focusNotes = week.focusNotes)
    }

    /** 热身：已掌握的热身/爬格子句取最久没练的；没有则用课程第一条（与主练句不同时）。 */
    fun warmup(
        input: PlanInput,
        main: SelectedMain?,
        warnings: MutableList<String>,
    ): WarmupPick? {
        val picked =
            input.progress.values
                .filter { it.mastery == Mastery.MASTERED && input.phrases.containsKey(it.phraseId) }
                .filter {
                    input.phrases
                        .getValue(it.phraseId)
                        .tags
                        .any { tag -> tag in warmupTags }
                }.minByOrNull { it.lastPracticedEpochDay ?: Long.MIN_VALUE }
        if (picked != null) {
            val info = input.phrases.getValue(picked.phraseId)
            val idleDays = input.todayEpochDay - (picked.lastPracticedEpochDay ?: input.todayEpochDay)
            return WarmupPick(
                info.id,
                info.tempoBpm,
                "热身：${info.title}，1.0x，前 ${loopBarCount(info)} 小节循环；上次练习 $idleDays 天前",
            )
        }
        val fallback =
            curriculumOrder(input.curriculum)
                .firstOrNull { it != main?.phraseId && input.phrases.containsKey(it) }
        if (fallback == null) {
            warnings += "没有可用的热身乐句，已跳过热身"
            return null
        }
        val info = input.phrases.getValue(fallback)
        return WarmupPick(
            info.id,
            info.tempoBpm * rules.startSpeedRatio,
            "还没有已掌握的热身句：先用${info.title.ifEmpty { fallback }}慢速活动手指",
        )
    }

    /** 复习：到期复习优先；没有到期时，主练句过了学习期才混入 1 条不同技术点的旧句。 */
    fun review(
        input: PlanInput,
        main: SelectedMain?,
    ): ReviewPick? = overdueReview(input, main) ?: gateReview(input, main)

    /** 考核昨天的主练句，避免「刚学完立刻考」；没有昨日记录时考核今日主练句。 */
    fun assess(
        input: PlanInput,
        main: SelectedMain?,
    ): AssessPick? {
        val yesterday = input.todayEpochDay - 1
        val yesterdayPhrase =
            input.recentAttempts
                .lastOrNull {
                    it.epochDay == yesterday && it.kind == AttemptKind.PRACTICE && input.phrases.containsKey(it.phraseId)
                }?.phraseId
        if (yesterdayPhrase != null && yesterdayPhrase != main?.phraseId) {
            val bpm = input.progress[yesterdayPhrase]?.currentBpm ?: input.phrases.getValue(yesterdayPhrase).tempoBpm
            return AssessPick(yesterdayPhrase, bpm, "考核昨天的主练句")
        }
        main?.let { return AssessPick(it.phraseId, it.bpm, "考核今日主练句（暂无昨日记录）") }
        return null
    }

    /** 课程里第一个未掌握的乐句；引用缺失的记 warning 并跳过。 */
    fun currentCurriculumEntry(
        input: PlanInput,
        warnings: MutableList<String>,
    ): Pair<CurriculumWeek, String>? =
        input.curriculum.weeks.sortedBy { it.index }.firstNotNullOfOrNull { week ->
            week.phraseIds.firstNotNullOfOrNull { phraseId ->
                if (!input.phrases.containsKey(phraseId)) {
                    if (warnings.none { it.contains(phraseId) }) {
                        warnings += "课程引用的乐句不在库中，已跳过：$phraseId"
                    }
                    null
                } else if (input.progress[phraseId]?.mastery != Mastery.MASTERED) {
                    week to phraseId
                } else {
                    null
                }
            }
        }

    private fun selectFailing(
        input: PlanInput,
        failing: PhraseProgress,
    ): SelectedMain {
        if (failing.switchPending) {
            alternativeFor(input, failing)?.let { alternative ->
                val info = input.phrases.getValue(alternative)
                val progress = input.progress[alternative]
                val bpm = progress?.currentBpm ?: info.tempoBpm * rules.startSpeedRatio
                return SelectedMain(
                    alternative,
                    bpm,
                    "连续 ${failing.failDays} 个训练日未通过，换同技术点的${info.title.ifEmpty { alternative }}",
                )
            }
            return SelectedMain(
                failing.phraseId,
                failing.currentBpm,
                "连续 ${failing.failDays} 个训练日未通过；没有替代乐句，今天只练前 ${rules.loopBarCount} 小节并保持降速",
            )
        }
        // 引擎在连续 2 次失败时已降速；这里只对「昨天首次失败」的句子先降 10%
        val cut = failing.consecutiveFails == 1
        val bpm = if (cut) failing.currentBpm * rules.failSessionSpeedRatio else failing.currentBpm
        val note = if (cut) "昨天未通过，今天降速 10% 继续" else "昨天未通过，今天继续"
        return SelectedMain(failing.phraseId, bpm, note)
    }

    /** 换句：同技术点、未掌握、按课程顺序取第一个。 */
    private fun alternativeFor(
        input: PlanInput,
        failing: PhraseProgress,
    ): String? {
        val tag = input.phrases[failing.phraseId]?.techniqueTag
        return curriculumOrder(input.curriculum)
            .filter { it != failing.phraseId && input.phrases.containsKey(it) }
            .filter { tag == null || input.phrases[it]?.techniqueTag == tag }
            .firstOrNull { id -> input.progress[id]?.mastery != Mastery.MASTERED }
    }

    private fun overdueReview(
        input: PlanInput,
        main: SelectedMain?,
    ): ReviewPick? {
        val overdue =
            input.progress.values
                .filter { it.isOverdueReview(input, main) }
                .minWithOrNull(compareBy({ it.inMaintenance }, { it.nextReviewEpochDay ?: Long.MAX_VALUE }))
        if (overdue == null) return null
        val info = input.phrases.getValue(overdue.phraseId)
        val idleDays = input.todayEpochDay - (overdue.lastPracticedEpochDay ?: input.todayEpochDay)
        return ReviewPick(overdue.phraseId, overdue.currentBpm, "间隔复习到期：${info.title}，上次练习 $idleDays 天前")
    }

    private fun gateReview(
        input: PlanInput,
        main: SelectedMain?,
    ): ReviewPick? {
        val mainPhrase = main?.phraseId ?: return null
        val mainProgress = input.progress[mainPhrase] ?: return null
        if (mainProgress.mastery == Mastery.LEARNING) return null
        val mainTag = input.phrases[mainPhrase]?.techniqueTag
        val old =
            input.progress.values
                .filter {
                    it.phraseId != mainPhrase &&
                        input.phrases.containsKey(it.phraseId) &&
                        it.lastPracticedEpochDay != null &&
                        input.phrases.getValue(it.phraseId).techniqueTag != mainTag
                }.minByOrNull { it.lastPracticedEpochDay ?: Long.MIN_VALUE } ?: return null
        val info = input.phrases.getValue(old.phraseId)
        return ReviewPick(old.phraseId, old.currentBpm, "巩固期：混入不同技术点的${info.title}")
    }

    private fun loopBarCount(info: PhraseInfo): Int = minOf(rules.loopBarCount, info.barCount)
}
