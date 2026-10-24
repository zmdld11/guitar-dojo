package dev.zmdld11.guitardojo.core.audio.phrase

import dev.zmdld11.guitardojo.core.audio.metronome.ClickSynth

/**
 * 把 [PracticePlan] 渲染成 PCM：拨弦音 + 拍点咔哒声在同一条流里，
 * 因此乐句与节拍器的拍点天然对齐，不存在两个 AudioTrack 的启动相位差。
 */
class PracticeMixer(
    private val sampleRate: Int,
) {
    private val voices = Array(MAX_VOICES) { PluckedStringVoice(sampleRate) }
    private val clickVoices = Array(MAX_CLICK_VOICES) { ClickVoice() }
    private val accentClick =
        toFloat(ClickSynth.generate(sampleRate, 1760.0, 45, 0.45, decayRatio = 0.18))
    private val mainClick =
        toFloat(ClickSynth.generate(sampleRate, 1174.0, 36, 0.32, decayRatio = 0.2))

    private var floatBuffer = FloatArray(0)
    private var plan: PracticePlan? = null
    private var playhead = 0L
    private var noteCursor = 0
    private var clickCursor = 0
    private var beatCursor = 0
    private var framesRendered = 0L
    private var nextNoteAge = 0L

    private val fade = FadeController()

    val currentPlayhead: Long get() = playhead

    fun currentTick(): Long = plan?.tickForFrame(playhead) ?: 0L

    /** 新乐句：清空所有正在响的音。 */
    fun startPlan(
        newPlan: PracticePlan,
        startFrame: Long = 0L,
    ) {
        voices.forEach { it.stop() }
        clickVoices.forEach { it.stop() }
        plan = newPlan
        playhead = startFrame.coerceIn(0, (newPlan.loopFrames - 1).coerceAtLeast(0))
        noteCursor = newPlan.notes.indexOfFirst { it.startFrame >= playhead }.takeIf { it >= 0 } ?: newPlan.notes.size
        clickCursor = newPlan.clicks.indexOfFirst { it.frame >= playhead }.takeIf { it >= 0 } ?: newPlan.clicks.size
        beatCursor = newPlan.beatFrames.indexOfFirst { it >= playhead }.takeIf { it >= 0 } ?: newPlan.beatFrames.size
    }

    /** 变速/换循环区间：保留正在响的尾音，只换时间轴。 */
    fun reusePlan(
        newPlan: PracticePlan,
        startFrame: Long,
    ) {
        plan = newPlan
        playhead = startFrame.coerceIn(0, (newPlan.loopFrames - 1).coerceAtLeast(0))
        noteCursor = newPlan.notes.indexOfFirst { it.startFrame >= playhead }.takeIf { it >= 0 } ?: newPlan.notes.size
        clickCursor = newPlan.clicks.indexOfFirst { it.frame >= playhead }.takeIf { it >= 0 } ?: newPlan.clicks.size
        beatCursor = newPlan.beatFrames.indexOfFirst { it >= playhead }.takeIf { it >= 0 } ?: newPlan.beatFrames.size
    }

    fun requestFadeOut() {
        fade.request()
    }

    val isFadeFinished: Boolean get() = fade.isFinished

    /**
     * 渲染一个块。返回的脉冲带绝对流帧号，交给轮询线程在播放头越过时再发给 UI。
     */
    fun render(
        out: ShortArray,
        pulses: MutableList<PracticePulse>,
    ) {
        val activePlan = plan
        if (activePlan == null) {
            out.fill(0)
            return
        }
        if (floatBuffer.size < out.size) floatBuffer = FloatArray(out.size)
        floatBuffer.fill(0f)

        var offset = 0
        while (offset < out.size) {
            val room = (activePlan.loopFrames - playhead).coerceAtMost((out.size - offset).toLong()).toInt()
            renderSection(activePlan, offset, room, pulses)
            playhead += room
            offset += room
            if (playhead >= activePlan.loopFrames) {
                playhead = 0
                noteCursor = 0
                clickCursor = 0
                beatCursor = 0
            }
        }

        voices.forEach { it.render(floatBuffer, out.size) }
        clickVoices.forEach { it.render(floatBuffer, out.size) }

        fade.apply(floatBuffer, out.size)
        for (index in 0 until out.size) {
            val sample = (floatBuffer[index] * Short.MAX_VALUE).toInt()
            out[index] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        framesRendered += out.size
    }

    private fun renderSection(
        activePlan: PracticePlan,
        blockOffset: Int,
        count: Int,
        pulses: MutableList<PracticePulse>,
    ) {
        if (count <= 0) return
        val sectionStart = playhead
        val sectionEnd = playhead + count

        while (noteCursor < activePlan.notes.size) {
            val note = activePlan.notes[noteCursor]
            if (note.startFrame >= sectionEnd) break
            if (note.startFrame >= sectionStart) {
                spawnNote(
                    note = note,
                    blockOffset = blockOffset + (note.startFrame - sectionStart).toInt(),
                )
            }
            noteCursor++
        }

        while (clickCursor < activePlan.clicks.size) {
            val click = activePlan.clicks[clickCursor]
            if (click.frame >= sectionEnd) break
            if (click.frame >= sectionStart) {
                spawnClick(
                    accent = click.accent,
                    offset = blockOffset + (click.frame - sectionStart).toInt(),
                )
            }
            clickCursor++
        }

        while (beatCursor < activePlan.beatFrames.size) {
            val frame = activePlan.beatFrames[beatCursor]
            if (frame >= sectionEnd) break
            if (frame >= sectionStart) {
                val streamFrame = framesRendered + blockOffset + (frame - sectionStart)
                activePlan.pulseAt(beatCursor, streamFrame)?.let(pulses::add)
            }
            beatCursor++
        }
    }

    private fun spawnNote(
        note: PlanNote,
        blockOffset: Int,
    ) {
        val voice = chooseVoice()
        nextNoteAge++
        voice.start(
            midi = note.midi,
            velocity = note.velocity,
            muted = note.muted,
            dead = note.dead,
            startOffsetSamples = blockOffset,
            noteAge = nextNoteAge,
        )
    }

    private fun spawnClick(
        accent: Boolean,
        offset: Int,
    ) {
        val voice = clickVoices.firstOrNull { !it.isActive } ?: clickVoices[0]
        voice.start(if (accent) accentClick else mainClick, offset)
    }

    private fun chooseVoice(): PluckedStringVoice {
        voices.firstOrNull { !it.isActive }?.let { return it }
        return voices.minBy { it.age }
    }

    private companion object {
        const val MAX_VOICES = 16
        const val MAX_CLICK_VOICES = 6

        fun toFloat(samples: ShortArray): FloatArray = FloatArray(samples.size) { samples[it] / 32768f }
    }
}
