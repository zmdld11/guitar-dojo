package dev.zmdld11.guitardojo.core.audio.input

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build

/**
 * 麦克风采集：把 AudioRecord 的 16 位单声道 PCM 转成 -1..1 的浮点帧。
 *
 * 优先使用 UNPROCESSED 音源，避免系统 AGC / 降噪把泛音削掉——调音器靠的就是原始波形。
 * 设备不支持时依次回退到 VOICE_RECOGNITION、MIC。
 */
class MicrophoneCapture(
    private val sampleRate: Int = 48_000,
    private val frameSize: Int = 2048,
) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    val isRecording: Boolean get() = running

    /**
     * 打开麦克风并开始回调。
     * 调用方（界面层）必须已经获得 RECORD_AUDIO 权限；本模块不持有 Context，无法自行申请。
     */
    @SuppressLint("MissingPermission")
    fun start(onFrame: (FloatArray) -> Unit): Boolean {
        if (running) return true

        val minBufferBytes =
            AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBufferBytes <= 0) return false

        val audioRecord = createRecord(minBufferBytes) ?: return false
        record = audioRecord
        running = true
        audioRecord.startRecording()

        thread = Thread({ captureLoop(audioRecord, onFrame) }, "mic-capture").apply { start() }
        return true
    }

    fun stop() {
        if (!running) return
        running = false
        thread?.join(500)
        thread = null
        record?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }
        record = null
    }

    private fun captureLoop(
        audioRecord: AudioRecord,
        onFrame: (FloatArray) -> Unit,
    ) {
        val pcm = ShortArray(frameSize)
        val frame = FloatArray(frameSize)
        while (running) {
            val read = audioRecord.read(pcm, 0, frameSize)
            if (read <= 0) continue
            for (i in 0 until read) {
                frame[i] = pcm[i] / 32768f
            }
            if (read < frameSize) continue
            onFrame(frame)
        }
    }

    @SuppressLint("MissingPermission")
    private fun createRecord(minBufferBytes: Int): AudioRecord? {
        val bufferBytes = maxOf(minBufferBytes, frameSize * 2 * 4)
        for (source in audioSources()) {
            val created =
                runCatching {
                    AudioRecord
                        .Builder()
                        .setAudioSource(source)
                        .setAudioFormat(
                            AudioFormat
                                .Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build(),
                        ).setBufferSizeInBytes(bufferBytes)
                        .build()
                }.getOrNull() ?: continue

            if (created.state == AudioRecord.STATE_INITIALIZED) return created
            created.release()
        }
        return null
    }

    private fun audioSources(): List<Int> =
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                add(MediaRecorder.AudioSource.UNPROCESSED)
            }
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }
}
