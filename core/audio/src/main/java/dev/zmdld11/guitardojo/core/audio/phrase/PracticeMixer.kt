package dev.zmdld11.guitardojo.core.audio.phrase

import dev.zmdld11.guitardojo.core.audio.metronome.ClickSynth
import kotlin.math.tanh

/**
 * 把 [PracticePlan] 渲染成 PCM：拨弦音 + 拍点咔哒声在同一条流里，
 * 因此乐句与节拍器的拍点天然对齐，不存在两个 AudioTrack 的启动相位差。
 */
class PracticeMixer(
    private val sampleRate: Int,
    initialTimbre: PhraseTimbre = PhraseTimbre.DEFAULT,
) {
    /** 当前音色；热切换由 [requestTimbre] 排队，在音频线程内生效。 */
    var timbre: PhraseTimbre = initialTimbre
        private set
    private var voices: Array<PhraseVoice> = Array(MAX_VOICES) { timbre.createVoice(sampleRate) }
    private val clickVoices = Array(MAX_CLICK_VOICES) { ClickVoice() }
    private var effects = PhraseEffects(sampleRate, timbre)
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
    private var lastAttackFrame = -1L
    private var strumIndex = 0
    private var pendingTimbre: PhraseTimbre? = null
    private var swapFadeRemaining = 0

    private val fade = FadeController()

    val currentPlayhead: Long get() = playhead

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
        lastAttackFrame = -1L
        strumIndex = 0
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
        lastAttackFrame = -1L
        strumIndex = 0
    }

    fun requestFadeOut() {
        fade.request()
    }

    /**
     * 热切换音色：播放头、计划与游标都不动，只把音乐总线在几毫秒内淡出，
     * 下一块换成新音色的 voice 与效果链。正在延音的尾音会被收掉，之后按原时间轴继续。
     */
    fun requestTimbre(newTimbre: PhraseTimbre) {
        if (newTimbre == timbre) {
            pendingTimbre = null
            swapFadeRemaining = 0
            return
        }
        if (newTimbre == pendingTimbre) return
        pendingTimbre = newTimbre
        swapFadeRemaining = SWAP_FADE_SAMPLES
    }

    /**
     * 渲染一个块。返回的脉冲带绝对流帧号，交给轮询线程在播放头越过时再发给 UI。
     */
    fun render(
        out: ShortArray,
        pulses: MutableList<PracticePulse>,
        notePulses: MutableList<PracticeNotePulse>? = null,
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
            val room =
                (activePlan.loopFrames - playhead).coerceIn(1L, (out.size - offset).toLong()).toInt()
            val sectionStart = playhead
            val sectionEnd = playhead + room
            spawnNotes(activePlan, sectionStart, sectionEnd, offset, notePulses)
            emitClicksAndBeats(activePlan, sectionStart, sectionEnd, offset, pulses)
            playhead += room
            offset += room
            if (playhead >= activePlan.loopFrames) {
                playhead = 0
                noteCursor = 0
                clickCursor = 0
                beatCursor = 0
                lastAttackFrame = -1L
                strumIndex = 0
            }
        }

        voices.forEach { it.render(floatBuffer, out.size) }
        if (timbre.mixGain != 1f) {
            for (index in 0 until out.size) floatBuffer[index] *= timbre.mixGain
        }
        effects.process(floatBuffer, out.size)
        pendingTimbre?.let { pending ->
            var remaining = swapFadeRemaining
            var index = 0
            while (index < out.size && remaining > 0) {
                remaining--
                floatBuffer[index] *= remaining / SWAP_FADE_SAMPLES.toFloat()
                index++
            }
            swapFadeRemaining = remaining
            if (remaining == 0) {
                timbre = pending
                voices = Array(MAX_VOICES) { timbre.createVoice(sampleRate) }
                effects = PhraseEffects(sampleRate, timbre)
                pendingTimbre = null
            }
        }
        // 节拍器走在效果链之后，保持干脆，也不会被混响拖出尾巴
        clickVoices.forEach { it.render(floatBuffer, out.size) }

        fade.apply(floatBuffer, out.size)
        for (index in 0 until out.size) {
            val sample = (tanh(floatBuffer[index] * MASTER_GAIN) * Short.MAX_VALUE).toInt()
            out[index] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        framesRendered += out.size
    }

    private fun spawnNotes(
        activePlan: PracticePlan,
        sectionStart: Long,
        sectionEnd: Long,
        blockOffset: Int,
        notePulses: MutableList<PracticeNotePulse>?,
    ) {
        while (noteCursor < activePlan.notes.size) {
            val note = activePlan.notes[noteCursor]
            if (note.startFrame >= sectionEnd) break
            if (note.startFrame >= sectionStart) {
                // 同一帧上的多个音按顺序错开几毫秒，模仿扫弦；否则和弦会像机关枪一样齐响
                strumIndex = if (note.startFrame == lastAttackFrame) strumIndex + 1 else 0
                lastAttackFrame = note.startFrame
                val strumOffset = (strumIndex * STRUM_SPACING_SAMPLES).coerceAtMost(MAX_STRUM_SAMPLES)
                val offsetSamples = blockOffset + (note.startFrame - sectionStart).toInt()
                spawnNote(note, offsetSamples, strumOffset)
                if (note.phraseIndex >= 0) {
                    notePulses?.add(
                        PracticeNotePulse(
                            streamFrame = framesRendered + offsetSamples,
                            phraseIndex = note.phraseIndex,
                        ),
                    )
                }
            }
            noteCursor++
        }
    }

    private fun emitClicksAndBeats(
        activePlan: PracticePlan,
        sectionStart: Long,
        sectionEnd: Long,
        blockOffset: Int,
        pulses: MutableList<PracticePulse>,
    ) {
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
        strumOffset: Int,
    ) {
        val voice = chooseVoice()
        nextNoteAge++
        voice.start(
            midi = note.midi,
            velocity = note.velocity,
            muted = note.muted,
            dead = note.dead,
            startOffsetSamples = blockOffset + strumOffset,
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

    private fun chooseVoice(): PhraseVoice {
        voices.firstOrNull { !it.isActive }?.let { return it }
        return voices.minBy { it.noteAge() }
    }

    private companion object {
        const val MAX_VOICES = 16
        const val MAX_CLICK_VOICES = 6
        const val MASTER_GAIN = 1.1f
        const val STRUM_SPACING_SAMPLES = 432
        const val MAX_STRUM_SAMPLES = 1_440
        const val SWAP_FADE_SAMPLES = 192

        fun toFloat(samples: ShortArray): FloatArray = FloatArray(samples.size) { samples[it] / 32768f }
    }
}
