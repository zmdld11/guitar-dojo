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

private const val BLOCK_SECONDS = 5L * 60

private const val REST_SECONDS = 60L

/** 练习步骤的计时行：显示当前分钟与块号。 */
internal fun practiceTimerText(
    elapsedSeconds: Long,
    blocks: Int,
): String {
    if (blocks <= 1) return "已 ${elapsedSeconds / 60} 分钟"
    val block = (elapsedSeconds / BLOCK_SECONDS).toInt() + 1
    return "已 ${elapsedSeconds / 60} 分钟 · 第 ${block.coerceAtMost(blocks)}/$blocks 块"
}

/** 处于 5 分钟块之间的 1 分钟休息窗口。 */
internal fun inRestWindow(
    elapsedSeconds: Long,
    blocks: Int,
): Boolean =
    blocks > 1 && elapsedSeconds > 0 && elapsedSeconds % BLOCK_SECONDS < REST_SECONDS && elapsedSeconds / BLOCK_SECONDS < blocks
