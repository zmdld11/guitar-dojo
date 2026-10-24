package dev.zmdld11.guitardojo.core.audio.pitch

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** 一个音名与它相对标准音高的音分偏差。 */
data class MusicalNote(
    val midi: Int,
    val name: String,
    val octave: Int,
    val cents: Double,
    val frequencyHz: Double,
) {
    val label: String get() = "$name$octave"

    /** 偏差是否在可接受范围内（默认 ±5 音分）。 */
    fun isInTune(toleranceCents: Double = DEFAULT_TOLERANCE_CENTS): Boolean = abs(cents) <= toleranceCents

    companion object {
        const val DEFAULT_TOLERANCE_CENTS = 5.0
    }
}

/** 频率与音名、音分之间的换算。 */
object NoteNaming {
    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun fromFrequency(
        frequencyHz: Double,
        a4: Double = 440.0,
    ): MusicalNote? {
        if (frequencyHz <= 0.0) return null
        val midiFloat = 69.0 + 12.0 * ln(frequencyHz / a4) / ln(2.0)
        val midi = midiFloat.roundToInt()
        val cents = (midiFloat - midi) * 100.0
        return MusicalNote(
            midi = midi,
            name = NAMES[((midi % 12) + 12) % 12],
            octave = midi / 12 - 1,
            cents = cents,
            frequencyHz = frequencyHz,
        )
    }

    fun frequencyOf(
        midi: Int,
        a4: Double = 440.0,
    ): Double = a4 * 2.0.pow((midi - 69) / 12.0)

    /** 两个频率相差多少音分。 */
    fun centsBetween(
        fromHz: Double,
        toHz: Double,
    ): Double = if (fromHz <= 0.0 || toHz <= 0.0) 0.0 else 1200.0 * ln(toHz / fromHz) / ln(2.0)
}
