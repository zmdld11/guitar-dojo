package dev.zmdld11.guitardojo.core.audio.assess

import android.media.AudioTrack
import dev.zmdld11.guitardojo.core.audio.phrase.PhraseAudioTrackFactory

/**
 * 回放考核录音（结果页「听我的录音」）。
 *
 * AudioTrack 与播放引擎同款低延迟配置；录音按 48 kHz 采集，设备回退到 44.1 kHz 时
 * 直接按设备采样率播放，音高会整体偏高约 9%，属已知限制。
 */
class TakePlayer {
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile
    private var playing = false

    val isPlaying: Boolean
        get() = playing

    fun start(
        samples: FloatArray,
        onEnd: () -> Unit = {},
    ) {
        if (playing || samples.isEmpty()) return
        val created = PhraseAudioTrackFactory.create(DEFAULT_SAMPLE_RATE) ?: return
        playing = true
        track = created.track
        thread =
            Thread({ playLoop(created.track, samples, onEnd) }, "take-playback")
                .apply { start() }
    }

    fun stop() {
        playing = false
        thread?.join(1_000)
        thread = null
        track = null
    }

    private fun playLoop(
        audioTrack: AudioTrack,
        samples: FloatArray,
        onEnd: () -> Unit,
    ) {
        var endedNaturally = true
        try {
            audioTrack.play()
            var offset = 0
            while (playing && offset < samples.size) {
                val count = minOf(BLOCK_FRAMES, samples.size - offset)
                val buffer = ShortArray(count)
                for (index in 0 until count) {
                    buffer[index] = (samples[offset + index] * Short.MAX_VALUE).toInt().toShort()
                }
                audioTrack.write(buffer, 0, count)
                offset += count
            }
            if (!playing) endedNaturally = false
        } finally {
            playing = false
            runCatching { audioTrack.stop() }
            audioTrack.release()
            this.track = null
        }
        if (endedNaturally) onEnd()
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000
        const val BLOCK_FRAMES = 512
    }
}
