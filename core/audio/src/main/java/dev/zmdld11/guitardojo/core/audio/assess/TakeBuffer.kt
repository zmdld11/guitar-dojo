package dev.zmdld11.guitardojo.core.audio.assess

/**
 * 一次考核的原始录音缓冲。
 *
 * 采集线程连续调用 [append]（采集器复用同一数组，这里必须复制）；分析前先 [stop]，之后只读。
 * 录音流与播放流的同步不在这里做：对齐用拍点瞬态做锚（见 [ClickAnchor]），缓冲本身不记时间戳。
 */
class TakeBuffer {
    private val chunks = ArrayList<FloatArray>()

    private var frameCountValue = 0L

    @Volatile
    private var sealed = false

    val frameCount: Long
        get() =
            synchronized(chunks) {
                frameCountValue
            }

    /** 停止接受新样本；之后 [append] 的调用会被忽略。 */
    fun stop() {
        sealed = true
    }

    /** 清空并重新接受样本（重考时用）。 */
    fun clear() {
        synchronized(chunks) {
            chunks.clear()
            frameCountValue = 0
            sealed = false
        }
    }

    fun append(frame: FloatArray) {
        if (sealed) return
        synchronized(chunks) {
            chunks += frame.copyOf()
            frameCountValue += frame.size
        }
    }

    /** 拼接全部样本；应在 [stop] 之后调用。 */
    fun snapshot(): FloatArray =
        synchronized(chunks) {
            val out = FloatArray(frameCountValue.toInt())
            var offset = 0
            chunks.forEach { chunk ->
                chunk.copyInto(out, offset)
                offset += chunk.size
            }
            out
        }
}
