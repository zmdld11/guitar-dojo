package dev.zmdld11.guitardojo.core.plan

/**
 * 每日计划生成器。纯函数：同输入同输出（幂等），只读历史与配置，写入只发生在用户完成任务时。
 * 任务顺序即向导步骤顺序；单日流程见 docs/M4-每日训练流设计.md §2。
 */
class PlanGenerator(
    private val rules: PlanRules = PlanRules(),
) {
    private val selector = PhraseSelector(rules)

    fun generate(input: PlanInput): DailyPlan {
        val weekday = epochDayToWeekdayIso(input.todayEpochDay)
        val kind = if (weekday in input.profile.noGuitarWeekdays) DayKind.NO_GUITAR else DayKind.GUITAR
        val warnings = mutableListOf<String>()
        val tasks = if (kind == DayKind.NO_GUITAR) noGuitarTasks(input, warnings) else guitarTasks(input, warnings)
        val estimated =
            if (kind == DayKind.NO_GUITAR) {
                NO_GUITAR_MINUTES
            } else {
                SessionTier.forMinutes(input.profile.minutesPerDay).minutes
            }
        return DailyPlan(
            dateEpochDay = input.todayEpochDay,
            kind = kind,
            minutesPerDay = input.profile.minutesPerDay,
            tasks = tasks,
            estimatedMinutes = estimated,
            warnings = warnings,
        )
    }

    // ---- 琴上日 ----

    private fun guitarTasks(
        input: PlanInput,
        warnings: MutableList<String>,
    ): List<PlanTask> {
        val tier = SessionTier.forMinutes(input.profile.minutesPerDay)
        val tasks = mutableListOf<PlanTask>()
        tasks += PlanTask.Tune(reason = "先调音，再练手；不调音会记为未调音")
        val main = selector.selectMain(input, warnings)
        tasks +=
            PlanTask.Setup(
                ampPreset = ampPresetFor(input, main),
                reason = "按今日练习内容设置音箱与拾音器",
            )
        selector.warmup(input, main, warnings)?.let { pick -> tasks += warmupTask(input, pick) }
        main?.let { tasks += practiceTask(input, it, tier.practiceBlocks, isFullRun = false, reason = it.reason) }
        if (tier.reviewBlocks > 0) {
            selector.review(input, main)?.let { pick -> tasks += reviewTask(input, pick, tier.reviewBlocks) }
        }
        if (tier.fullRunBlocks > 0 && main != null) {
            tasks += practiceTask(input, main, tier.fullRunBlocks, isFullRun = true, reason = FULL_RUN_REASON)
        }
        selector.assess(input, main)?.let { pick ->
            tasks += PlanTask.Assess(phraseId = pick.phraseId, targetBpm = pick.targetBpm, reason = pick.reason)
        }
        tasks += PlanTask.Cooldown(deckId = DECK_MAINTENANCE, reason = "保养收尾：擦弦、归位、关音箱")
        return tasks
    }

    // ---- 无琴日 ----

    private fun noGuitarTasks(
        input: PlanInput,
        warnings: MutableList<String>,
    ): List<PlanTask> {
        val tasks = mutableListOf<PlanTask>()
        val theory = input.todayTheoryCardId
        if (theory != null) {
            tasks += PlanTask.TheoryCard(theory, "今日一卡：先回答，再看答案")
        } else {
            warnings += "今日没有到期或新学的乐理卡片"
        }
        input.todayPracticeCardId?.let { tasks += PlanTask.PracticeCard(it, "把这张卡的方法用在下次琴上练习") }
        tasks +=
            PlanTask.RhythmGame(
                weekIndex = selector.currentCurriculumEntry(input, warnings)?.first?.index,
                minutes = RHYTHM_MINUTES,
                reason = "节奏对拍：球转到圆心的瞬间点下去",
            )
        tasks +=
            PlanTask.MentalPractice(
                phraseId = selector.currentCurriculumEntry(input, warnings)?.second,
                minutes = MENTAL_MINUTES,
                reason = "闭眼过一遍指法、把位和音",
            )
        tasks += PlanTask.NoteEntry(reason = "写一句下次要注意什么")
        return tasks
    }

    // ---- 任务渲染 ----

    private fun warmupTask(
        input: PlanInput,
        pick: WarmupPick,
    ): PlanTask.Warmup {
        val info = input.phrases.getValue(pick.phraseId)
        return PlanTask.Warmup(
            phraseId = pick.phraseId,
            bpm = pick.bpm,
            speed = speedFor(pick.bpm, info),
            loopStartBar = 0,
            loopBarCount = loopBarCount(info),
            reason = pick.reason,
        )
    }

    private fun reviewTask(
        input: PlanInput,
        pick: ReviewPick,
        blocks: Int,
    ): PlanTask.Review {
        val info = input.phrases.getValue(pick.phraseId)
        return PlanTask.Review(
            phraseId = pick.phraseId,
            bpm = pick.bpm,
            speed = speedFor(pick.bpm, info),
            blocks = blocks,
            reason = pick.reason,
        )
    }

    private fun practiceTask(
        input: PlanInput,
        main: SelectedMain,
        blocks: Int,
        isFullRun: Boolean,
        reason: String,
    ): PlanTask.Practice {
        val info = input.phrases.getValue(main.phraseId)
        val barCount = if (isFullRun) info.barCount else loopBarCount(info)
        return PlanTask.Practice(
            phraseId = main.phraseId,
            bpm = main.bpm,
            speed = speedFor(main.bpm, info),
            loopStartBar = 0,
            loopBarCount = barCount,
            blocks = blocks,
            isFullRun = isFullRun,
            focusNotes = main.focusNotes,
            reason = reason,
        )
    }

    private fun ampPresetFor(
        input: PlanInput,
        main: SelectedMain?,
    ): AmpPreset {
        val tags = main?.let { input.phrases[it.phraseId]?.tags }.orEmpty()
        return if (tags.any { it.contains("riff", ignoreCase = true) }) AmpPreset.DRIVE else AmpPreset.CLEAN
    }

    private fun loopBarCount(info: PhraseInfo): Int = minOf(rules.loopBarCount, info.barCount)

    private fun speedFor(
        bpm: Double,
        info: PhraseInfo,
    ): Double = if (info.tempoBpm > 0.0) (bpm / info.tempoBpm).coerceIn(MIN_SPEED, MAX_SPEED) else 1.0

    companion object {
        const val DECK_MAINTENANCE = "maintenance"
        const val NO_GUITAR_MINUTES = 10
        const val RHYTHM_MINUTES = 2
        const val MENTAL_MINUTES = 2
        const val MIN_SPEED = 0.25
        const val MAX_SPEED = 1.5
        const val FULL_RUN_REASON = "拼接回全段：从第 1 小节完整走一遍，速度保持主练速度"
    }
}
