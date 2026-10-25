package dev.zmdld11.guitardojo.core.audio.phrase

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.tanh

/**
 * 过载/失真吉他：拨弦建模进波形整形器，再过两级低通模拟箱体。
 *
 * 高增益会把拨弦的细节放大成持续的方波感，所以箱体低通和收紧低频都要在
 * 这个 voice 里完成，不能只靠总线 EQ。
 */
class DistortedGuitarVoice(
    private val sampleRate: Int,
    private val drive: Float,
    private val outputGain: Float,
) : PhraseVoice {
    private val string = PluckedStringVoice(sampleRate)
    private val block = FloatArray(BLOCK_SAMPLES)
    private var lowpassA = 0f
    private var lowpassB = 0f
    private var highpassInput = 0f
    private var highpassOutput = 0f
    private val lowpassAlpha = (1.0 - exp(-2.0 * PI * CABINET_HZ / sampleRate)).toFloat()
    private val highpassAlpha = (1.0 / (1.0 + 2.0 * PI * TIGHTEN_HZ / sampleRate)).toFloat()

    override val isActive: Boolean get() = string.isActive

    override fun noteAge(): Long = string.noteAge()

    override fun start(
        midi: Int,
        velocity: Float,
        muted: Boolean,
        dead: Boolean,
        startOffsetSamples: Int,
        noteAge: Long,
    ) {
        string.start(midi, velocity, muted, dead, startOffsetSamples, noteAge)
        lowpassA = 0f
        lowpassB = 0f
        highpassInput = 0f
        highpassOutput = 0f
    }

    override fun render(
        out: FloatArray,
        count: Int,
    ) {
        if (!string.isActive) return
        var offset = 0
        while (offset < count) {
            val frames = minOf(BLOCK_SAMPLES, count - offset)
            block.fill(0f, 0, frames)
            string.render(block, frames)
            for (index in 0 until frames) {
                val input = block[index]
                val tightened = highpassAlpha * (highpassOutput + input - highpassInput)
                highpassInput = input
                highpassOutput = tightened
                val driven = tanh(tightened * drive) * outputGain
                lowpassA += lowpassAlpha * (driven - lowpassA)
                lowpassB += lowpassAlpha * (lowpassA - lowpassB)
                out[offset + index] += lowpassB
            }
            offset += frames
        }
    }

    override fun stop() {
        string.stop()
    }

    private companion object {
        const val BLOCK_SAMPLES = 4096
        const val CABINET_HZ = 4_200.0
        const val TIGHTEN_HZ = 65.0
    }
}
