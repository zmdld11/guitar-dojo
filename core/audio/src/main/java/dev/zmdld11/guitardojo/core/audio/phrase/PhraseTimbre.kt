package dev.zmdld11.guitardojo.core.audio.phrase

/**
 * 可选的乐句音色。id 用于本地持久化，改 id 会让旧设置失效。
 *
 * [mixGain] 是总线音量修正：以清音吉他为 0 dB 基准，按试听片段整体 RMS 对齐，
 * 切换音色时不会突然变响或变轻；它只影响音量，不改变失真度与音色。
 */
enum class PhraseTimbre(
    val id: String,
    val label: String,
    val mixGain: Float = 1f,
) {
    ELECTRIC_PIANO("epiano", "电钢琴", 1.19f),
    PIANO("piano", "钢琴", 1.12f),
    SYNTH_LEAD("synth", "合成器", 1.46f),
    CHIP("chip", "8-bit", 1.62f),
    MARIMBA("marimba", "马林巴", 1.23f),
    CLEAN_GUITAR("guitar", "清音吉他"),
    CRUNCH_GUITAR("crunch", "过载吉他", 0.81f),
    DISTORTION_GUITAR("distortion", "失真吉他", 0.44f),
    ;

    fun createVoice(sampleRate: Int): PhraseVoice =
        when (this) {
            ELECTRIC_PIANO -> ElectricPianoVoice(sampleRate)
            PIANO -> PianoVoice(sampleRate)
            SYNTH_LEAD -> SynthLeadVoice(sampleRate)
            CHIP -> ChipVoice(sampleRate)
            MARIMBA -> MarimbaVoice(sampleRate)
            CLEAN_GUITAR -> PluckedStringVoice(sampleRate)
            CRUNCH_GUITAR -> DistortedGuitarVoice(sampleRate, drive = 3.5f, outputGain = 0.30f)
            DISTORTION_GUITAR -> DistortedGuitarVoice(sampleRate, drive = 16f, outputGain = 0.20f)
        }

    companion object {
        val DEFAULT = MARIMBA

        fun fromId(id: String?): PhraseTimbre = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
