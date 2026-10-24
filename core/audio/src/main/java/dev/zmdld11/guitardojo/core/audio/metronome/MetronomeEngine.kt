package dev.zmdld11.guitardojo.core.audio.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 节拍器引擎。
 *
 * 时间基准是 **音频帧** 而不是系统时钟：拍点由 [BeatTimeline] 换算成帧号，
 * 混音后写进 AudioTrack；UI 的拍点事件从播放头位置（[AudioTrack.getPlaybackHeadPosition]）
 * 追上来时才发出。因此即使线程被调度延迟，节奏也不会漂移。
 */
class MetronomeEngine(
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
) {
    private val _pulses =
        MutableSharedFlow<BeatPulse>(
            extraBufferCapacity = 128,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /** 已在扬声器上播放出的拍点，供 UI 做视觉同步。 */
    val pulses: SharedFlow<BeatPulse> = _pulses.asSharedFlow()

    @Volatile
    private var running = false

    @Volatile
    private var desiredConfig = MetronomeConfig()

    private var audioThread: Thread? = null
    private var pollThread: Thread? = null
    private var track: AudioTrack? = null

    /** 实际使用的采样率，可能与构造参数不同（设备不支持时回退）。 */
    var activeSampleRate: Int = sampleRate
        private set

    /**
     * 调试用钩子：拍点被扬声器播放出来时回调（拍点、`SystemClock.elapsedRealtimeNanos()`）。
     * 在轮询线程上执行，只用于漂移测量；异常会被吞掉以免影响播放。
     */
    @Volatile
    var pulseObserver: ((BeatPulse, Long) -> Unit)? = null

    /** AudioTrack 欠载次数。长期非零说明缓冲太小或线程被抢占。 */
    val underrunCount: Int
        get() = runCatching { track?.underrunCount ?: 0 }.getOrDefault(0)

    val isRunning: Boolean get() = running

    fun setConfig(config: MetronomeConfig) {
        desiredConfig = config
    }

    fun start() {
        if (running) return
        running = true
        audioThread =
            Thread({ audioLoop() }, "metronome-audio").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
    }

    fun stop() {
        if (!running) return
        running = false
        audioThread?.join(1_000)
        pollThread?.join(1_000)
        audioThread = null
        pollThread = null
    }

    private fun audioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val mixer = MetronomeMixer(activeSampleRate, desiredConfig)
        val buffer = ShortArray(BLOCK_FRAMES)
        var appliedConfig = desiredConfig
        val pending = ConcurrentLinkedQueue<BeatPulse>()

        val audioTrack = createTrack()
        if (audioTrack == null) {
            running = false
            return
        }
        track = audioTrack

        startPolling(audioTrack, pending)
        audioTrack.play()

        while (running) {
            val latest = desiredConfig
            if (latest != appliedConfig) {
                mixer.updateConfig(latest)
                appliedConfig = latest
            }
            mixer.render(buffer).forEach { pending += it }
            audioTrack.write(buffer, 0, buffer.size)
        }

        audioTrack.stop()
        audioTrack.release()
        track = null
    }

    private fun startPolling(
        audioTrack: AudioTrack,
        pending: ConcurrentLinkedQueue<BeatPulse>,
    ) {
        pollThread =
            Thread({
                var framesPlayedBase = 0L
                var lastRawHead = 0
                while (running) {
                    val rawHead = audioTrack.playbackHeadPosition
                    if (rawHead < lastRawHead) {
                        // playbackHeadPosition 是 32 位无符号帧计数，约 12 小时后回绕
                        framesPlayedBase += WRAP_FRAMES
                    }
                    lastRawHead = rawHead
                    val head = framesPlayedBase + (rawHead.toLong() and 0xFFFF_FFFFL)
                    emitPlayedPulses(pending, head)
                    Thread.sleep(POLL_INTERVAL_MILLIS)
                }
            }, "metronome-poll").apply { start() }
    }

    /** 把播放头已经越过的拍点发出去；到头就返回，等下一轮轮询。 */
    private fun emitPlayedPulses(
        pending: ConcurrentLinkedQueue<BeatPulse>,
        head: Long,
    ) {
        while (true) {
            val pulse = pending.peek() ?: return
            if (pulse.frame > head) return
            pending.poll()
            _pulses.tryEmit(pulse)
            pulseObserver?.let { observer ->
                runCatching { observer(pulse, SystemClock.elapsedRealtimeNanos()) }
            }
        }
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
    ): AudioTrack? {
        val bufferBytes =
            if (minBufferBytes > 0) {
                minBufferBytes * 2
            } else {
                BLOCK_FRAMES * 2 * 4
            }
        return runCatching {
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
                .setBufferSizeInBytes(bufferBytes)
                .build()
        }.getOrNull()
    }

    /** 供调试与漂移测量使用：当前音频通路的输出延迟估计。 */
    fun estimatedOutputLatencyMillis(): Double {
        if (track == null) return 0.0
        return runCatching {
            val minBuffer =
                AudioTrack.getMinBufferSize(
                    activeSampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            minBuffer.toDouble() / 2 / activeSampleRate * 1_000
        }.getOrDefault(0.0)
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000
        private const val FALLBACK_SAMPLE_RATE = 44_100
        private const val BLOCK_FRAMES = 256
        private const val POLL_INTERVAL_MILLIS = 4L
        private const val WRAP_FRAMES = 0x1_0000_0000L
    }
}
