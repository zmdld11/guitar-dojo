package dev.zmdld11.guitardojo.ui.screens.session

import dev.zmdld11.guitardojo.core.plan.AmpPreset
import dev.zmdld11.guitardojo.core.plan.PlanTask

/** 任务短标签，向导进度与首页卡片共用。 */
internal fun taskLabel(task: PlanTask): String =
    when (task) {
        is PlanTask.Tune -> "调音"
        is PlanTask.Setup -> "设备"
        is PlanTask.Warmup -> "热身"
        is PlanTask.Practice -> if (task.isFullRun) "完整跑" else "练习"
        is PlanTask.Review -> "复习"
        is PlanTask.Assess -> "考核"
        is PlanTask.TheoryCard -> "乐理卡"
        is PlanTask.PracticeCard -> "练法卡"
        is PlanTask.MentalPractice -> "心理练习"
        is PlanTask.NoteEntry -> "记录"
        is PlanTask.Cooldown -> "收尾"
    }

internal fun ampPresetLabel(preset: AmpPreset): String =
    when (preset) {
        AmpPreset.CLEAN -> "清音"
        AmpPreset.LIGHT_DRIVE -> "轻失真"
        AmpPreset.DRIVE -> "失真"
    }
