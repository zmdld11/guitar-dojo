package dev.zmdld11.guitardojo.core.audio.phrase

import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.phrase.PhraseBar
import dev.zmdld11.guitardojo.core.phrase.PhraseTechniques
import kotlin.math.roundToLong

/** 一个已经换算成音频帧的播发音符。 */
data class PlanNote(
    val startFrame: Long,
    val endFrame: Long,
    val midi: Int,
    val velocity: Float,
    val muted: Boolean,
    val dead: Boolean,
)

/** 与乐句同一条流的拍点。 */
data class PlanClick(
    val frame: Long,
    val accent: Boolean,
)

/** 供 UI 高亮的小节/拍点事件。 */
data class PracticePulse(
    val loopFrame: Long,
    val streamFrame: Long,
    val barIndex: Int,
    val beatInBar: Int,
)

/**
 * 一个循环区间内的帧级时间轴。
 *
 * 变速/换循环区间时整体重建，而不是改采样率——音高因此保持不变。
 */
class PracticePlan(
    val loopFrames: Long,
    val notes: List<PlanNote>,
    val clicks: List<PlanClick>,
    val barStartFrames: LongArray,
    val barStartTicks: LongArray,
    val barIndices: IntArray,
    val beatFrames: LongArray,
    val beatBarIndices: IntArray,
    val beatInBar: IntArray,
    val barFramesPerTick: DoubleArray,
    val loopStartTick: Long,
    val loopEndTick: Long,
    val sampleRate: Int,
    val speed: Double,
) {
    fun frameForTick(tick: Long): Long {
        if (barStartTicks.isEmpty()) return 0
        val clamped = tick.coerceIn(loopStartTick, loopEndTick)
        var index = barStartTicks.size - 1
        for (i in barStartTicks.indices) {
            if (clamped < barStartTicks[i]) {
                index = (i - 1).coerceAtLeast(0)
                break
            }
        }
        val frame = barStartFrames[index] + (clamped - barStartTicks[index]) * barFramesPerTick[index]
        return frame.roundToLong().coerceIn(0, loopFrames)
    }

    fun tickForFrame(frame: Long): Long {
        if (barStartFrames.isEmpty()) return loopStartTick
        val clamped = frame.coerceIn(0, loopFrames)
        var index = barStartFrames.size - 1
        for (i in barStartFrames.indices) {
            if (clamped < barStartFrames[i]) {
                index = (i - 1).coerceAtLeast(0)
                break
            }
        }
        val tick = barStartTicks[index] + (clamped - barStartFrames[index]) / barFramesPerTick[index]
        return tick.roundToLong().coerceIn(loopStartTick, loopEndTick)
    }

    fun pulseAt(
        beatIndex: Int,
        streamFrame: Long,
    ): PracticePulse? {
        if (beatIndex !in beatFrames.indices) return null
        return PracticePulse(
            loopFrame = beatFrames[beatIndex],
            streamFrame = streamFrame,
            barIndex = beatBarIndices[beatIndex],
            beatInBar = beatInBar[beatIndex],
        )
    }
}

object PhraseScheduler {
    const val MIN_SPEED = 0.25
    const val MAX_SPEED = 1.5

    fun plan(
        phrase: Phrase,
        sampleRate: Int,
        speed: Double = 1.0,
        loopStartBar: Int? = null,
        loopEndBar: Int? = null,
        clickEnabled: Boolean = true,
    ): PracticePlan {
        require(sampleRate > 0) { "sampleRate 必须为正" }
        require(phrase.bars.isNotEmpty()) { "乐句没有小节" }
        val clampedSpeed = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        val first = (loopStartBar ?: 0).coerceIn(0, phrase.bars.lastIndex)
        val last = (loopEndBar ?: phrase.bars.lastIndex).coerceIn(first, phrase.bars.lastIndex)
        val layout = layoutBars(phrase, phrase.bars.subList(first, last + 1), sampleRate, clampedSpeed)
        val beats = buildBeats(phrase, layout, clickEnabled)

        return PracticePlan(
            loopFrames = layout.loopFrames,
            notes = buildNotes(phrase, layout),
            clicks = beats.clicks,
            barStartFrames = layout.barStartFrames,
            barStartTicks = layout.barStartTicks,
            barIndices = layout.barIndices,
            beatFrames = beats.frames,
            beatBarIndices = beats.barIndices,
            beatInBar = beats.beatsInBar,
            barFramesPerTick = layout.framesPerTick,
            loopStartTick = layout.loopStartTick,
            loopEndTick = layout.loopEndTick,
            sampleRate = sampleRate,
            speed = clampedSpeed,
        )
    }

    private fun layoutBars(
        phrase: Phrase,
        bars: List<PhraseBar>,
        sampleRate: Int,
        speed: Double,
    ): BarLayout {
        val barStartFrames = LongArray(bars.size)
        val barStartTicks = LongArray(bars.size)
        val barIndices = IntArray(bars.size)
        val framesPerTick = DoubleArray(bars.size)

        var frameCursor = 0.0
        bars.forEachIndexed { index, bar ->
            barStartFrames[index] = frameCursor.roundToLong()
            barStartTicks[index] = bar.startTick
            barIndices[index] = bar.index
            framesPerTick[index] = sampleRate * 60.0 / (bar.tempoBpm * phrase.ppq) / speed
            frameCursor += bar.lengthTicks * framesPerTick[index]
        }
        return BarLayout(
            bars = bars,
            barStartFrames = barStartFrames,
            barStartTicks = barStartTicks,
            barIndices = barIndices,
            framesPerTick = framesPerTick,
            loopFrames = frameCursor.roundToLong().coerceAtLeast(1),
            loopStartTick = bars.first().startTick,
            loopEndTick = bars.last().startTick + bars.last().lengthTicks,
        )
    }

    private fun buildNotes(
        phrase: Phrase,
        layout: BarLayout,
    ): List<PlanNote> {
        val notes = ArrayList<PlanNote>()
        phrase.notes.forEach { note ->
            if (note.tick < layout.loopStartTick || note.tick >= layout.loopEndTick) return@forEach
            val barOffset = barIndexForTick(layout.bars, note.tick)
            val startFrame =
                layout.barStartFrames[barOffset] +
                    ((note.tick - layout.bars[barOffset].startTick) * layout.framesPerTick[barOffset]).roundToLong()
            val endFrame = frameForTick(note.tick + note.durationTicks, layout)
            notes +=
                PlanNote(
                    startFrame = startFrame.coerceIn(0, layout.loopFrames),
                    endFrame = endFrame.coerceAtLeast(startFrame + 1),
                    midi = note.midi,
                    velocity = note.velocity,
                    muted = PhraseTechniques.PALM_MUTE in note.techniques,
                    dead = PhraseTechniques.DEAD_NOTE in note.techniques,
                )
        }
        return notes.sortedBy { it.startFrame }
    }

    private fun buildBeats(
        phrase: Phrase,
        layout: BarLayout,
        clickEnabled: Boolean,
    ): BeatData {
        val clicks = ArrayList<PlanClick>()
        val frames = ArrayList<Long>()
        val barIndices = ArrayList<Int>()
        val beatsInBar = ArrayList<Int>()

        layout.bars.forEachIndexed { index, bar ->
            val beats = bar.timeSignature.firstOrNull()?.coerceAtLeast(1) ?: 4
            val denominator = bar.timeSignature.getOrNull(1)?.coerceAtLeast(1) ?: 4
            val beatTicks = phrase.ppq.toLong() * 4 / denominator
            for (beat in 0 until beats) {
                val frame =
                    layout.barStartFrames[index] +
                        (beat * beatTicks * layout.framesPerTick[index]).roundToLong()
                val loopFrame = frame.coerceIn(0, layout.loopFrames)
                if (frames.isEmpty() || loopFrame > frames.last()) {
                    frames += loopFrame
                    barIndices += bar.index
                    beatsInBar += beat
                    if (clickEnabled) clicks += PlanClick(loopFrame, beat == 0)
                }
            }
        }
        return BeatData(clicks, frames.toLongArray(), barIndices.toIntArray(), beatsInBar.toIntArray())
    }

    private fun barIndexForTick(
        bars: List<PhraseBar>,
        tick: Long,
    ): Int {
        bars.forEachIndexed { index, bar ->
            if (tick >= bar.startTick && tick < bar.startTick + bar.lengthTicks) return index
        }
        return bars.lastIndex
    }

    private fun frameForTick(
        tick: Long,
        layout: BarLayout,
    ): Long {
        val index = barIndexForTick(layout.bars, tick)
        return (
            layout.barStartFrames[index] +
                (tick - layout.barStartTicks[index]) * layout.framesPerTick[index]
        ).roundToLong()
    }

    private class BarLayout(
        val bars: List<PhraseBar>,
        val barStartFrames: LongArray,
        val barStartTicks: LongArray,
        val barIndices: IntArray,
        val framesPerTick: DoubleArray,
        val loopFrames: Long,
        val loopStartTick: Long,
        val loopEndTick: Long,
    )

    private class BeatData(
        val clicks: List<PlanClick>,
        val frames: LongArray,
        val barIndices: IntArray,
        val beatsInBar: IntArray,
    )
}
