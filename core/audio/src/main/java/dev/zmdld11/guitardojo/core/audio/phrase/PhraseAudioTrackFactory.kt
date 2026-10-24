package dev.zmdld11.guitardojo.core.audio.phrase

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

internal class CreatedTrack(
    val track: AudioTrack,
    val sampleRate: Int,
)

/** 乐句播放用的低延迟 AudioTrack；48kHz 不可用时回退 44.1kHz。 */
internal object PhraseAudioTrackFactory {
    fun create(preferredSampleRate: Int): CreatedTrack? {
        val minBufferBytes =
            AudioTrack.getMinBufferSize(
                preferredSampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBufferBytes > 0) {
            createAt(preferredSampleRate, minBufferBytes * 2)?.let {
                return CreatedTrack(it, preferredSampleRate)
            }
        }
        return createAt(FALLBACK_SAMPLE_RATE, BLOCK_FRAMES * 2 * 4)?.let {
            CreatedTrack(it, FALLBACK_SAMPLE_RATE)
        }
    }

    private fun createAt(
        rate: Int,
        bufferBytes: Int,
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
                .setBufferSizeInBytes(bufferBytes)
                .build()
        }.getOrNull()

    private const val FALLBACK_SAMPLE_RATE = 44_100
    private const val BLOCK_FRAMES = 256
}
