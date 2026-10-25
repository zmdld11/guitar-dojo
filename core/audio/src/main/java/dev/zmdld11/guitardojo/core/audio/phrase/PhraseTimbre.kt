package dev.zmdld11.guitardojo.core.audio.phrase

/** 可选的乐句音色。id 用于本地持久化，改 id 会让旧设置失效。 */
enum class PhraseTimbre(
    val id: String,
    val label: String,
) {
    ELECTRIC_PIANO("epiano", "电钢琴"),
    PIANO("piano", "钢琴"),
    SYNTH_LEAD("synth", "合成器"),
    CHIP("chip", "8-bit"),
    MARIMBA("marimba", "马林巴"),
    CLEAN_GUITAR("guitar", "清音吉他"),
    CRUNCH_GUITAR("crunch", "过载吉他"),
    DISTORTION_GUITAR("distortion", "失真吉他"),
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
