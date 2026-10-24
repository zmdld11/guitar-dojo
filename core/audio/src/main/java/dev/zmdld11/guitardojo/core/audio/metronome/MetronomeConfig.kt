package dev.zmdld11.guitardojo.core.audio.metronome

/**
 * 节拍器配置。
 *
 * @property bpm 每分钟拍数，1-300。
 * @property beatsPerBar 每小节拍数，1-12。
 * @property subdivisions 每拍细分数：1=四分，2=八分，3=三连音，4=十六分。
 * @property accentFirstBeat 是否重读每小节第一拍。
 */
data class MetronomeConfig(
    val bpm: Int = 80,
    val beatsPerBar: Int = 4,
    val subdivisions: Int = 1,
    val accentFirstBeat: Boolean = true,
) {
    init {
        require(bpm in MIN_BPM..MAX_BPM) { "BPM 必须在 $MIN_BPM-$MAX_BPM 之间，实际为 $bpm" }
        require(beatsPerBar in MIN_BEATS_PER_BAR..MAX_BEATS_PER_BAR) {
            "每小节拍数必须在 $MIN_BEATS_PER_BAR-$MAX_BEATS_PER_BAR 之间，实际为 $beatsPerBar"
        }
        require(subdivisions in MIN_SUBDIVISIONS..MAX_SUBDIVISIONS) {
            "细分数必须在 $MIN_SUBDIVISIONS-$MAX_SUBDIVISIONS 之间，实际为 $subdivisions"
        }
    }

    companion object {
        const val MIN_BPM = 1
        const val MAX_BPM = 300
        const val MIN_BEATS_PER_BAR = 1
        const val MAX_BEATS_PER_BAR = 12
        const val MIN_SUBDIVISIONS = 1
        const val MAX_SUBDIVISIONS = 4
    }
}
