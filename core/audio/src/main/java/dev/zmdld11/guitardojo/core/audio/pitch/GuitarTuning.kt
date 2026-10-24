package dev.zmdld11.guitardojo.core.audio.pitch

/** 标准调弦下的一根弦。 */
data class GuitarString(
    val number: Int,
    val midi: Int,
    val noteLabel: String,
) {
    /** 目标频率。 */
    fun targetFrequencyHz(a4: Double = 440.0): Double = NoteNaming.frequencyOf(midi, a4)
}

/** 吉他调弦：标准 E 调弦，6 弦到 1 弦。 */
object GuitarTuning {
    val standard: List<GuitarString> =
        listOf(
            GuitarString(number = 6, midi = 40, noteLabel = "E2"),
            GuitarString(number = 5, midi = 45, noteLabel = "A2"),
            GuitarString(number = 4, midi = 50, noteLabel = "D3"),
            GuitarString(number = 3, midi = 55, noteLabel = "G3"),
            GuitarString(number = 2, midi = 59, noteLabel = "B3"),
            GuitarString(number = 1, midi = 64, noteLabel = "E4"),
        )

    /**
     * 找与给定频率最接近的弦。
     * 允许 ±300 音分（3 个半音），超出说明弹的不是这个调弦里的音。
     */
    fun nearestString(
        frequencyHz: Double,
        a4: Double = 440.0,
    ): GuitarString? {
        val candidate =
            standard.minByOrNull { string ->
                kotlin.math.abs(NoteNaming.centsBetween(string.targetFrequencyHz(a4), frequencyHz))
            } ?: return null
        val cents = NoteNaming.centsBetween(candidate.targetFrequencyHz(a4), frequencyHz)
        return if (kotlin.math.abs(cents) <= MAX_MATCH_CENTS) candidate else null
    }

    private const val MAX_MATCH_CENTS = 300.0
}
