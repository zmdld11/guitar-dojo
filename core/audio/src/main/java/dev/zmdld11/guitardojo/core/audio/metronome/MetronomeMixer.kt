package dev.zmdld11.guitardojo.core.audio.metronome

/**
 * 把时间轴上的拍点混进 PCM 缓冲区。
 *
 * 纯计算、无 Android 依赖：输入的 [ShortArray] 就是交给 AudioTrack 的缓冲，
 * 因此「调度是否准时」可以在 JVM 单测里逐帧校验。
 */
class MetronomeMixer(
    private val sampleRate: Int,
    initialConfig: MetronomeConfig,
) {
    private val timeline = BeatTimeline(sampleRate, initialConfig)
    private val accentClick = ClickSynth.generate(sampleRate, 1760.0, 50, 0.9)
    private val mainClick = ClickSynth.generate(sampleRate, 1174.0, 40, 0.7)
    private val subdivisionClick = ClickSynth.generate(sampleRate, 880.0, 24, 0.32)
    private val voices = ArrayList<Voice>(4)

    private var framePosition: Long = 0L

    /** 已经渲染出去的帧数，等于下一次 render 的起始帧号。 */
    val renderedFrames: Long get() = framePosition

    fun updateConfig(config: MetronomeConfig) {
        timeline.reconfigure(config)
    }

    /**
     * 渲染一块缓冲，返回落在这一块里的拍点。
     * 缓冲区会被整体覆盖，调用方无需预先清零。
     */
    fun render(buffer: ShortArray): List<BeatPulse> {
        buffer.fill(0)

        val blockStart = framePosition
        val blockEnd = blockStart + buffer.size
        val pulses = timeline.drainsBefore(blockEnd)

        pulses.forEach { pulse ->
            val click =
                when {
                    !pulse.isMainBeat -> subdivisionClick
                    pulse.isDownbeat && timeline.config.accentFirstBeat -> accentClick
                    else -> mainClick
                }
            voices += Voice(click, offsetInBlock = (pulse.frame - blockStart).toInt())
        }

        mixVoices(buffer)
        framePosition = blockEnd
        return pulses
    }

    private fun mixVoices(buffer: ShortArray) {
        val iterator = voices.iterator()
        while (iterator.hasNext()) {
            val voice = iterator.next()
            var bufferIndex = voice.offsetInBlock
            var sampleIndex = voice.sampleIndex
            while (bufferIndex < buffer.size && sampleIndex < voice.samples.size) {
                val mixed = buffer[bufferIndex] + voice.samples[sampleIndex]
                buffer[bufferIndex] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                bufferIndex++
                sampleIndex++
            }
            voice.offsetInBlock = 0
            voice.sampleIndex = sampleIndex
            if (sampleIndex >= voice.samples.size) iterator.remove()
        }
    }

    private class Voice(
        val samples: ShortArray,
        var offsetInBlock: Int,
        var sampleIndex: Int = 0,
    )
}
