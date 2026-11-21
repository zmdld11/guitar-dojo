package dev.zmdld11.guitardojo.core.audio.assess

import dev.zmdld11.guitardojo.core.audio.phrase.PracticePlan
import dev.zmdld11.guitardojo.core.phrase.Phrase

/** 考核判定需要的期望事件集合：期望音符与期望拍点（都在播放流帧上）。 */
data class AssessmentTargets(
    val notes: List<ExpectedNote>,
    val clickSamples: List<Long>,
)

/** 从乐句与练习计划推导期望事件；循环区间之外的音不参与判定。 */
fun assessmentTargets(
    phrase: Phrase,
    plan: PracticePlan,
): AssessmentTargets {
    val notes =
        phrase.notes
            .mapIndexed { index, note ->
                if (note.tick < plan.loopStartTick || note.tick >= plan.loopEndTick) {
                    null
                } else {
                    val start = plan.frameForTick(note.tick)
                    ExpectedNote(
                        phraseIndex = index,
                        barIndex = note.bar,
                        midi = note.midi,
                        startSample = start,
                        endSample = plan.frameForTick(note.tick + note.durationTicks).coerceAtLeast(start + 1),
                    )
                }
            }.filterNotNull()
            .sortedBy { it.startSample }
    return AssessmentTargets(notes = notes, clickSamples = plan.clicks.map { it.frame })
}
