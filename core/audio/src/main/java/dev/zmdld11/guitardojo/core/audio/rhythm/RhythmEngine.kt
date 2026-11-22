package dev.zmdld11.guitardojo.core.audio.rhythm

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import dev.zmdld11.guitardojo.core.audio.metronome.ClickSynth
import dev.zmdld11.guitardojo.core.audio.phrase.PhraseScheduler
import dev.zmdld11.guitardojo.core.audio.phrase.PhraseTimbre
import dev.zmdld11.guitardojo.core.audio.phrase.PracticeMixer
import dev.zmdld11.guitardojo.core.audio.phrase.PracticePlan
import dev.zmdld11.guitardojo.core.audio.phrase.PracticePulse
import dev.zmdld11.guitardojo.core.phrase.Phrase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 节奏游戏引擎（冰与火之舞式）。
 *
 * 时间基准是 **音频帧**：tile 拍点由 [RhythmTimeline] 换算成帧号后渲染咔哒，
 * 玩家命中的「拍手音」按帧号注入渲染块（点击到出声约一个渲染块，几毫秒）。
 * 播放头越过未点的 tile 记 miss；连续 miss 三次自动失败结束。
 * PHRASE 模式用 [PracticeMixer] 渲染伴奏乐句（自动循环）叠在同一流里。
 */
class RhythmEngine(
    sampleRate: Int = DEFAULT_SAMPLE_RATE,
    private val level: RhythmLevel,
    private val speed: Double = 1.0,
    private val phrase: Phrase? = null,
    private val timbre: PhraseTimbre = PhraseTimbre.DEFAULT,
) {
    private val _events =
        MutableSharedFlow<RhythmEvent>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /** 对局事件：tile 滑过（miss）、走完、失败。 */
    val events: SharedFlow<RhythmEvent> = _events.asSharedFlow()

    @Volatile
    private var running = false

    @Volatile
    private var ready = false

    @Volatile
    var timeline: RhythmTimeline? = null
        private set

    private var session: RhythmSession? = null
    private val hitInjections = ConcurrentLinkedQueue<Long>()
    private val audioThreadStarted = AtomicBoolean(false)

    private var audioThread: Thread? = null
    private var pollThread: Thread? = null
    private var track: AudioTrack? = null

    var activeSampleRate: Int = sampleRate
        private set

    val isRunning: Boolean get() = running

    /** 播放头流内帧号（从 0 起）；引擎未就绪时返回 0。点击判定与 UI 渲染共用。 */
    fun headFrame(): Long =
        if (!ready) {
            0L
        } else {
            runCatching { track?.playbackHeadPosition?.toLong() ?: 0L }.getOrDefault(0L)
        }

    fun start() {
        if (running || !audioThreadStarted.compareAndSet(false, true)) return
        running = true
        audioThread =
            Thread({ audioLoop() }, "rhythm-audio").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
    }

    /** 等引擎就绪（时间轴与混音器建好）；超时返回 false。 */
    fun awaitReady(timeoutMillis: Long = 800): Boolean {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (!ready && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        return ready
    }

    /** 玩家点击；返回判定（UI 立即显示）。引擎未就绪或已结束时返回 null。 */
    fun hit(): HitVerdict? {
        val activeSession = session ?: return null
        val verdict = activeSession.hit(headFrame())
        if (verdict?.grade == HitGrade.PERFECT || verdict?.grade == HitGrade.GOOD) {
            // 缓冲已预写约一个缓冲深度，精确帧回不去；统一注入下一块开头，
            // 与 tile 咔哒同样经过缓冲，相对延迟不超过一个渲染块（约 5ms）。
            hitInjections.offer(headFrame())
        }
        return verdict
    }

    fun stop() {
        if (!running) return
        running = false
        audioThread?.join(1_000)
        pollThread?.join(1_000)
        audioThread = null
        pollThread = null
        track = null
        ready = false
    }

    private fun audioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val audioTrack = createTrack()
        if (audioTrack == null) {
            running = false
            return
        }
        track = audioTrack

        val activeTimeline = RhythmTimeline(activeSampleRate, level.bpm, speed, level.beats, level.repeats)
        val activeSession = RhythmSession(activeTimeline, activeSampleRate)
        timeline = activeTimeline
        session = activeSession
        val mixer =
            phrase?.let {
                val plan: PracticePlan =
                    PhraseScheduler.plan(it, activeSampleRate, speed, loopStartBar = null, loopEndBar = null, clickEnabled = false)
                PracticeMixer(activeSampleRate, timbre).also { m -> m.startPlan(plan) }
            }
        val mainClick = ClickSynth.generate(activeSampleRate, 1174.0, 36, clickGain(level), decayRatio = 0.2)
        val accentClick = ClickSynth.generate(activeSampleRate, 1760.0, 45, clickGain(level) * 1.3, decayRatio = 0.18)
        val hitSound = ClickSynth.generate(activeSampleRate, 1400.0, 30, 0.55, decayRatio = 0.25)

        ready = true
        startPolling(audioTrack, activeSession)
        audioTrack.play()

        val buffer = ShortArray(BLOCK_FRAMES)
        val pulses = ArrayList<PracticePulse>(8)
        var streamFrame = 0L
        while (running && !activeSession.failed && !activeSession.finished) {
            if (mixer == null) buffer.fill(0) else mixer.render(buffer, pulses)
            val frameBase = streamFrame
            val frameEnd = frameBase + buffer.size
            // tile 拍点：每 4 拍一个重音，给 4/4 律动感
            var tileIndex = activeTimeline.tileFrames.indexOfFirst { it >= frameBase }
            if (tileIndex < 0) tileIndex = activeTimeline.tileFrames.size
            while (tileIndex < activeTimeline.tileFrames.size && activeTimeline.tileFrames[tileIndex] < frameEnd) {
                val offset = (activeTimeline.tileFrames[tileIndex] - frameBase).toInt()
                overlay(buffer, offset, if (tileIndex % 4 == 0) accentClick else mainClick)
                tileIndex++
            }
            // 命中音注入：点击发生在缓冲预写之后，统一叠在当前块开头（见 hit() 注释）
            while (hitInjections.poll() != null) {
                overlay(buffer, 0, hitSound)
            }
            audioTrack.write(buffer, 0, buffer.size)
            streamFrame = frameEnd
        }

        audioTrack.stop()
        audioTrack.release()
        track = null
        ready = false
    }

    private fun startPolling(
        audioTrack: AudioTrack,
        activeSession: RhythmSession,
    ) {
        pollThread =
            Thread({
                while (running && !activeSession.failed && !activeSession.finished) {
                    val head = audioTrack.playbackHeadPosition.toLong()
                    activeSession.advance(head).forEach { event ->
                        _events.tryEmit(event)
                    }
                    Thread.sleep(POLL_INTERVAL_MILLIS)
                }
            }, "rhythm-poll").apply { start() }
    }

    private fun createTrack(): AudioTrack? {
        val minBufferBytes =
            AudioTrack.getMinBufferSize(
                activeSampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBufferBytes <= 0) {
            activeSampleRate = FALLBACK_SAMPLE_RATE
            return createTrackAt(FALLBACK_SAMPLE_RATE)
        }
        return createTrackAt(activeSampleRate, minBufferBytes)
    }

    private fun createTrackAt(
        rate: Int,
        minBufferBytes: Int = 0,
    ): AudioTrack? =
        runCatching {
            AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                ).setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(minBufferBytes * 2)
                .build()
        }.getOrNull()

    companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000
        private const val FALLBACK_SAMPLE_RATE = 44_100
        private const val BLOCK_FRAMES = 256
        private const val POLL_INTERVAL_MILLIS = 4L

        /** PHRASE 模式把拍点压轻，别盖过伴奏乐句。 */
        private fun clickGain(level: RhythmLevel): Double = if (level.audioMode == RhythmAudioMode.PHRASE) 0.16 else 0.34

        private fun overlay(
            buffer: ShortArray,
            offset: Int,
            sample: ShortArray,
        ) {
            for (i in sample.indices) {
                val index = offset + i
                if (index >= buffer.size) break
                val mixed = buffer[index] + sample[i].toInt()
                buffer[index] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }
}
