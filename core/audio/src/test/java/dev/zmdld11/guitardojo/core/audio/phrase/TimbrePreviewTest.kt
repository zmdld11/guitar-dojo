package dev.zmdld11.guitardojo.core.audio.phrase

import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.phrase.PhraseBar
import dev.zmdld11.guitardojo.core.phrase.PhraseNote
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class TimbrePreviewTest {
    @Test
    fun renderPreviews() {
        val rendered =
            renderAll(sampleRate = 44_100, seconds = 8.0, directory = "build/timbre", phrase = previewPhrase())
        val reference = rms(rendered.getValue(PhraseTimbre.CLEAN_GUITAR))
        rendered.forEach { (timbre, samples) ->
            val deviationDb = 20.0 * log10(rms(samples) / reference)
            assertTrue(
                "${timbre.label} 响度偏差 ${"%.1f".format(deviationDb)} dB",
                abs(deviationDb) <= LOUDNESS_TOLERANCE_DB,
            )
        }
    }

    /** 供内嵌试听页使用的短片段：22.05kHz、2.5 秒，控制文件体积。 */
    @Test
    fun renderShortPreviews() {
        renderAll(sampleRate = 22_050, seconds = 2.5, directory = "build/timbre/short", phrase = shortPhrase())
    }

    private fun renderAll(
        sampleRate: Int,
        seconds: Double,
        directory: String,
        phrase: Phrase,
    ): Map<PhraseTimbre, ShortArray> {
        val plan = PhraseScheduler.plan(phrase, sampleRate, clickEnabled = false)
        val outputDir = File(directory).apply { mkdirs() }
        println("timbre dir: ${outputDir.absolutePath}")
        val rendered = LinkedHashMap<PhraseTimbre, ShortArray>()

        PhraseTimbre.entries.forEach { timbre ->
            val mixer = PracticeMixer(sampleRate, timbre)
            mixer.startPlan(plan)
            val totalSamples = (sampleRate * seconds).toInt()
            val samples = ShortArray(totalSamples)
            val block = ShortArray(256)
            var position = 0
            while (position < totalSamples) {
                val count = minOf(block.size, totalSamples - position)
                val buffer = if (count == block.size) block else ShortArray(count)
                mixer.render(buffer, ArrayList())
                buffer.copyInto(samples, position, 0, count)
                position += count
            }
            val file = File(outputDir, "${timbre.id}.wav")
            writeWav(file, samples, sampleRate)
            assertTrue("${timbre.label} 没有输出声音", samples.any { it.toInt() != 0 })
            println("wrote ${file.name} ${file.length()} bytes")
            rendered[timbre] = samples
        }
        return rendered
    }

    private fun rms(samples: ShortArray): Double {
        var sum = 0.0
        samples.forEach { sample -> sum += sample.toDouble() * sample }
        return sqrt(sum / samples.size) / Short.MAX_VALUE
    }

    private companion object {
        const val LOUDNESS_TOLERANCE_DB = 3.0
    }

    private fun shortPhrase(): Phrase {
        val barTicks = 3_840L
        val notes =
            listOf(
                PhraseNote(bar = 0, tick = 0, durationTicks = 960, midi = 60, string = 2, fret = 1),
                PhraseNote(bar = 0, tick = 960, durationTicks = 960, midi = 64, string = 1, fret = 0),
                PhraseNote(bar = 0, tick = 1_920, durationTicks = 960, midi = 67, string = 1, fret = 3),
                PhraseNote(bar = 0, tick = 2_880, durationTicks = 960, midi = 72, string = 1, fret = 8),
            )
        return Phrase(
            id = "preview.short",
            title = "音色试听 · 短",
            ppq = 960,
            bars = listOf(PhraseBar(index = 0, startTick = 0, lengthTicks = barTicks, tempoBpm = 120.0)),
            notes = notes,
        )
    }

    private fun previewPhrase(): Phrase {
        val barTicks = 3_840L
        val bars =
            (0..2).map {
                PhraseBar(index = it, startTick = it * barTicks, lengthTicks = barTicks, tempoBpm = 100.0)
            }

        fun note(
            bar: Int,
            beat: Double,
            midi: Int,
            string: Int,
            fret: Int,
            durationBeats: Double = 1.0,
        ) = PhraseNote(
            bar = bar,
            tick = (bar * barTicks + (beat * 960).toLong()),
            durationTicks = (durationBeats * 960).toLong(),
            midi = midi,
            string = string,
            fret = fret,
        )

        val notes =
            listOf(
                note(0, 0.0, 60, 2, 1),
                note(0, 1.0, 62, 2, 3),
                note(0, 2.0, 64, 1, 0),
                note(0, 3.0, 67, 1, 3),
                note(1, 0.0, 69, 1, 5),
                note(1, 1.0, 67, 1, 3),
                note(1, 2.0, 64, 1, 0),
                note(1, 3.0, 62, 2, 3),
                note(2, 0.0, 60, 2, 1, 4.0),
                note(2, 0.0, 64, 1, 0, 4.0),
                note(2, 0.0, 67, 1, 3, 4.0),
            )
        return Phrase(
            id = "preview.timbre",
            title = "音色试听",
            difficulty = 1,
            ppq = 960,
            bars = bars,
            notes = notes,
        )
    }

    private fun writeWav(
        file: File,
        samples: ShortArray,
        sampleRate: Int,
    ) {
        val dataSize = samples.size * 2
        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
            out.writeBytes("RIFF")
            out.writeIntLittleEndian(36 + dataSize)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLittleEndian(16)
            out.writeShortLittleEndian(1)
            out.writeShortLittleEndian(1)
            out.writeIntLittleEndian(sampleRate)
            out.writeIntLittleEndian(sampleRate * 2)
            out.writeShortLittleEndian(2)
            out.writeShortLittleEndian(16)
            out.writeBytes("data")
            out.writeIntLittleEndian(dataSize)
            samples.forEach { out.writeShortLittleEndian(it.toInt()) }
        }
    }

    private fun DataOutputStream.writeIntLittleEndian(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
        writeByte((value ushr 16) and 0xFF)
        writeByte((value ushr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLittleEndian(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
    }
}
