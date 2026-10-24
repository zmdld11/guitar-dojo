package dev.zmdld11.guitardojo.core.audio.phrase

/** 停止播放时的短淡出，避免在波形中途截断产生爆音。 */
internal class FadeController {
    private var requested = false
    private var done = false
    private var remaining = 0

    val isFinished: Boolean get() = requested && done

    fun request() {
        requested = true
    }

    fun apply(
        buffer: FloatArray,
        count: Int,
    ) {
        if (!requested) return
        if (done) {
            buffer.fill(0f, 0, count)
            return
        }
        if (remaining <= 0) remaining = FADE_SAMPLES
        for (index in 0 until count) {
            if (remaining <= 0) {
                buffer.fill(0f, index, count)
                done = true
                return
            }
            buffer[index] *= ((remaining - 1).coerceAtLeast(0)).toFloat() / FADE_SAMPLES
            remaining--
        }
        if (remaining <= 0) done = true
    }

    private companion object {
        const val FADE_SAMPLES = 256
    }
}
