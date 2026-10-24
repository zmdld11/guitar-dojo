package dev.zmdld11.guitardojo.core.audio.phrase

/** 预渲染的节拍器咔哒声，按帧偏移叠加到混音块里。 */
internal class ClickVoice {
    private var samples: FloatArray = EMPTY
    private var position = 0
    private var pendingSilence = 0

    var isActive: Boolean = false
        private set

    fun start(
        sampleData: FloatArray,
        offset: Int,
    ) {
        samples = sampleData
        position = 0
        pendingSilence = offset.coerceAtLeast(0)
        isActive = samples.isNotEmpty()
    }

    fun stop() {
        isActive = false
        samples = EMPTY
    }

    fun render(
        out: FloatArray,
        count: Int,
    ) {
        if (!isActive) return
        var index = 0
        if (pendingSilence > 0) {
            val skip = minOf(pendingSilence, count)
            pendingSilence -= skip
            index = skip
        }
        while (index < count && position < samples.size) {
            out[index] += samples[position]
            position++
            index++
        }
        if (position >= samples.size) isActive = false
    }

    private companion object {
        val EMPTY = FloatArray(0)
    }
}
