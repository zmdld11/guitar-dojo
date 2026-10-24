package dev.zmdld11.guitardojo.core.phrase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 标准调弦 EADGBE，低音弦在前（MIDI 音高）。 */
val STANDARD_TUNING = listOf(40, 45, 50, 55, 59, 64)

@Serializable
enum class PhraseOrigin {
    @SerialName("builtin")
    BUILTIN,

    @SerialName("local")
    LOCAL,
}

/**
 * 乐句 JSON Schema v1。
 *
 * 时间单位是 tick（[ppq] 个 tick 为一个四分音符），不存秒——变速时只重建时间轴，
 * 音高与谱面都不变。一个文件只描述一条轨道。
 */
@Serializable
data class Phrase(
    val schemaVersion: Int = 1,
    val id: String,
    val title: String,
    val description: String = "",
    val difficulty: Int = 1,
    val tags: List<String> = emptyList(),
    val origin: PhraseOrigin = PhraseOrigin.LOCAL,
    val tuning: List<Int> = STANDARD_TUNING,
    val capo: Int = 0,
    val ppq: Int = 960,
    val bars: List<PhraseBar> = emptyList(),
    val notes: List<PhraseNote> = emptyList(),
    val trackName: String = "",
    val source: PhraseSource? = null,
) {
    val totalTicks: Long
        get() = bars.lastOrNull()?.let { it.startTick + it.lengthTicks } ?: 0L

    /** 按各小节速度估算的总时长，只用于列表展示。 */
    val approximateDurationSeconds: Double
        get() =
            bars.sumOf { bar ->
                if (bar.tempoBpm <= 0.0) 0.0 else bar.lengthTicks * 60.0 / (bar.tempoBpm * ppq)
            }

    val hasLoopableBars: Boolean get() = bars.isNotEmpty()

    fun barAt(tick: Long): PhraseBar? = bars.firstOrNull { tick >= it.startTick && tick < it.startTick + it.lengthTicks }

    fun barOrNull(index: Int): PhraseBar? = bars.getOrNull(index)

    /** 一拍的 tick 数（四分音符为一拍）。 */
    fun beatTicksOf(bar: PhraseBar): Long {
        val denominator = bar.timeSignature.getOrElse(1) { 4 }.coerceAtLeast(1)
        return ppq.toLong() * 4 / denominator
    }

    fun notesInBars(
        startBar: Int,
        endBar: Int,
    ): List<PhraseNote> {
        if (bars.isEmpty()) return emptyList()
        val from = bars.getOrNull(startBar.coerceAtLeast(0))?.startTick ?: return emptyList()
        val end = bars.getOrNull(endBar.coerceAtMost(bars.lastIndex)) ?: return emptyList()
        val to = end.startTick + end.lengthTicks
        return notes.filter { it.tick >= from && it.tick < to }
    }
}

@Serializable
data class PhraseBar(
    val index: Int,
    val startTick: Long,
    val lengthTicks: Long,
    /** [分子, 分母]；分母必须是 2 的幂。 */
    val timeSignature: List<Int> = listOf(4, 4),
    val tempoBpm: Double = 120.0,
) {
    val beatsPerBar: Int get() = timeSignature.getOrElse(0) { 4 }
}

@Serializable
data class PhraseNote(
    val bar: Int,
    val tick: Long,
    val durationTicks: Long,
    val midi: Int,
    val string: Int,
    val fret: Int,
    val velocity: Float = 0.8f,
    val techniques: List<String> = emptyList(),
) {
    val endTick: Long get() = tick + durationTicks
}

/** 来源信息只用于显示与排错，不参与播放。 */
@Serializable
data class PhraseSource(
    val generator: String = "",
    val file: String = "",
    val trackIndex: Int = -1,
    val fingeringRewritten: Boolean = false,
    val exportedAt: String = "",
)

object PhraseTechniques {
    const val HAMMER_ON = "hammer_on"
    const val PULL_OFF = "pull_off"
    const val SLIDE = "slide"
    const val BEND = "bend"
    const val VIBRATO = "vibrato"
    const val PALM_MUTE = "palm_mute"
    const val HARMONIC = "harmonic"
    const val DEAD_NOTE = "dead_note"
    const val ACCENT = "accent"

    val all: Set<String> =
        setOf(HAMMER_ON, PULL_OFF, SLIDE, BEND, VIBRATO, PALM_MUTE, HARMONIC, DEAD_NOTE, ACCENT)
}
