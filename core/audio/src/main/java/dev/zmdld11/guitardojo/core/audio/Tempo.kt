package dev.zmdld11.guitardojo.core.audio

/**
 * 节拍速度。BPM 取值范围与需求一致：1-300。
 *
 * @property bpm 每分钟拍数。
 */
@JvmInline
value class Tempo(
    val bpm: Int,
) {
    init {
        require(bpm in MIN_BPM..MAX_BPM) { "BPM 必须在 $MIN_BPM-$MAX_BPM 之间，实际为 $bpm" }
    }

    /** 每拍时长，单位毫秒。 */
    val beatIntervalMillis: Double
        get() = MILLIS_PER_MINUTE / bpm

    companion object {
        const val MIN_BPM = 1
        const val MAX_BPM = 300

        private const val MILLIS_PER_MINUTE = 60_000.0
    }
}
