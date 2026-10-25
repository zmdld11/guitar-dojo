package dev.zmdld11.guitardojo.core.audio.phrase

/**
 * 乐句播放用的单音发声器。混音器只依赖这个接口，因此可以在拨弦、电钢、
 * 合成器等音色之间切换，而不用改时间轴或音频线程。
 */
interface PhraseVoice {
    val isActive: Boolean

    /** 最近一次起音的序号，混音器用它做声部抢占。 */
    fun noteAge(): Long

    fun start(
        midi: Int,
        velocity: Float,
        muted: Boolean,
        dead: Boolean,
        startOffsetSamples: Int = 0,
        noteAge: Long = 0L,
    )

    fun render(
        out: FloatArray,
        count: Int,
    )

    fun stop()
}

/** 所有合成音色共用的音高换算。 */
internal fun midiFrequency(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)
