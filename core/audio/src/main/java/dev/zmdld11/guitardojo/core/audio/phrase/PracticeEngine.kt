package dev.zmdld11.guitardojo.core.audio.phrase

import android.media.AudioTrack
import android.os.Process
import dev.zmdld11.guitardojo.core.phrase.Phrase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 乐句播放引擎：和节拍器一样以音频帧为时间基准，播放头轮询只用于 UI 同步。
 *
 * 变速与换循环区间在音频线程内重建 [PracticePlan]，不改变采样率，所以音高不变。
 */
class PracticeEngine(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
) {
    private val _pulses =
        MutableSharedFlow<PracticePulse>(
            extraBufferCapacity = 128,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /** 已经由扬声器播放出来的小节/拍点。 */
    val pulses: SharedFlow<PracticePulse> = _pulses.asSharedFlow()

    @Volatile
    private var running = false

    @Volatile
    private var desiredPhrase: Phrase? = null

    @Volatile
    private var desiredSpeed: Double = 1.0

    @Volatile
    private var desiredLoopStart: Int? = null

    @Volatile
    private var desiredLoopEnd: Int? = null

    @Volatile
    private var desiredClickEnabled: Boolean = true

    @Volatile
    private var fadeRequested = false

    private val configDirty = AtomicBoolean(false)

    private var audioThread: Thread? = null
    private var pollThread: Thread? = null
    private var track: AudioTrack? = null

    @Volatile
    private var activeMixer: PracticeMixer? = null

    /** 实际使用的采样率，可能与构造参数不同（设备不支持时回退）。 */
    var activeSampleRate: Int = sampleRate
        private set

    val isRunning: Boolean get() = running

    val underrunCount: Int
        get() = runCatching { track?.underrunCount ?: 0 }.getOrDefault(0)

    fun setPhrase(
        phrase: Phrase,
        speed: Double = 1.0,
        loopStartBar: Int? = null,
        loopEndBar: Int? = null,
        clickEnabled: Boolean = true,
    ) {
        desiredPhrase = phrase
        desiredSpeed = speed.coerceIn(PhraseScheduler.MIN_SPEED, PhraseScheduler.MAX_SPEED)
        desiredLoopStart = loopStartBar
        desiredLoopEnd = loopEndBar
        desiredClickEnabled = clickEnabled
        configDirty.set(true)
    }

    fun setSpeed(speed: Double) {
        val clamped = speed.coerceIn(PhraseScheduler.MIN_SPEED, PhraseScheduler.MAX_SPEED)
        if (clamped == desiredSpeed) return
        desiredSpeed = clamped
        configDirty.set(true)
    }

    fun setLoop(
        startBar: Int?,
        endBar: Int?,
    ) {
        if (startBar == desiredLoopStart && endBar == desiredLoopEnd) return
        desiredLoopStart = startBar
        desiredLoopEnd = endBar
        configDirty.set(true)
    }

    fun setClickEnabled(enabled: Boolean) {
        if (enabled == desiredClickEnabled) return
        desiredClickEnabled = enabled
        configDirty.set(true)
    }

    fun start() {
        if (running) return
        if (desiredPhrase == null) return
        running = true
        fadeRequested = false
        audioThread =
            Thread({ audioLoop() }, "phrase-audio").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
    }

    fun stop() {
        if (!running) return
        fadeRequested = true
        activeMixer?.requestFadeOut()
        runCatching { Thread.sleep(FADE_MILLIS) }
        running = false
        audioThread?.join(1_000)
        pollThread?.join(1_000)
        audioThread = null
        pollThread = null
        activeMixer = null
    }

    private fun audioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val mixer = PracticeMixer(activeSampleRate)
        activeMixer = mixer
        val buffer = ShortArray(BLOCK_FRAMES)
        val pending = ConcurrentLinkedQueue<PracticePulse>()
        val blockPulses = ArrayList<PracticePulse>(8)

        val created = PhraseAudioTrackFactory.create(activeSampleRate)
        if (created == null) {
            running = false
            return
        }
        activeSampleRate = created.sampleRate
        val audioTrack = created.track
        track = audioTrack
        startPolling(audioTrack, pending)
        audioTrack.play()

        var appliedPhrase: Phrase? = null
        var appliedPlan: PracticePlan? = null

        while (running) {
            applyPendingConfig(mixer, appliedPhrase, appliedPlan)?.let { applied ->
                appliedPhrase = applied.phrase
                appliedPlan = applied.plan
                pending.clear()
            }

            blockPulses.clear()
            mixer.render(buffer, blockPulses)
            blockPulses.forEach(pending::add)
            audioTrack.write(buffer, 0, buffer.size)
        }

        if (fadeRequested) {
            // 再渲染一小段静音，确保淡出被写进 AudioTrack
            buffer.fill(0)
            audioTrack.write(buffer, 0, buffer.size)
        }
        audioTrack.stop()
        audioTrack.release()
        track = null
    }

    /**
     * 配置变化时重建时间轴；返回 null 表示本轮无事可做。
     * 换乐句从 0 开始，变速/换循环保留当前 tick，避免听感中断。
     */
    private fun applyPendingConfig(
        mixer: PracticeMixer,
        appliedPhrase: Phrase?,
        appliedPlan: PracticePlan?,
    ): AppliedConfig? {
        if (!configDirty.getAndSet(false)) return null
        val phrase = desiredPhrase ?: return null
        val newPlan =
            PhraseScheduler.plan(
                phrase = phrase,
                sampleRate = activeSampleRate,
                speed = desiredSpeed,
                loopStartBar = desiredLoopStart,
                loopEndBar = desiredLoopEnd,
                clickEnabled = desiredClickEnabled,
            )
        if (phrase !== appliedPhrase) {
            mixer.startPlan(newPlan, 0)
        } else {
            val tick = appliedPlan?.tickForFrame(mixer.currentPlayhead) ?: newPlan.loopStartTick
            val keep = tick in newPlan.loopStartTick until newPlan.loopEndTick
            mixer.reusePlan(newPlan, if (keep) newPlan.frameForTick(tick) else 0)
        }
        return AppliedConfig(phrase, newPlan)
    }

    private data class AppliedConfig(
        val phrase: Phrase,
        val plan: PracticePlan,
    )

    private fun startPolling(
        audioTrack: AudioTrack,
        pending: ConcurrentLinkedQueue<PracticePulse>,
    ) {
        pollThread =
            Thread({
                var framesPlayedBase = 0L
                var lastRawHead = 0
                while (running) {
                    val rawHead = audioTrack.playbackHeadPosition
                    if (rawHead < lastRawHead) {
                        framesPlayedBase += WRAP_FRAMES
                    }
                    lastRawHead = rawHead
                    val head = framesPlayedBase + (rawHead.toLong() and 0xFFFF_FFFFL)
                    while (true) {
                        val pulse = pending.peek() ?: break
                        if (pulse.streamFrame > head) break
                        pending.poll()
                        _pulses.tryEmit(pulse)
                    }
                    Thread.sleep(POLL_INTERVAL_MILLIS)
                }
            }, "phrase-poll").apply { start() }
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000
        const val BLOCK_FRAMES = 256
        const val POLL_INTERVAL_MILLIS = 4L
        const val WRAP_FRAMES = 0x1_0000_0000L
        const val FADE_MILLIS = 40L
    }
}
