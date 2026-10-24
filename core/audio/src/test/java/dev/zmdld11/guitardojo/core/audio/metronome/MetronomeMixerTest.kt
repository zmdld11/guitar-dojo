package dev.zmdld11.guitardojo.core.audio.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MetronomeMixerTest {
    private val sampleRate = 48_000
    private val blockFrames = 256

    @Test
    fun `一秒内 120 BPM 恰好触发两次主拍`() {
        val mixer = MetronomeMixer(sampleRate, MetronomeConfig(bpm = 120))
        val buffer = ShortArray(blockFrames)
        val pulses =
            buildList {
                repeat(sampleRate / blockFrames) { addAll(mixer.render(buffer)) }
            }
        assertEquals(listOf(0L, 24_000L), pulses.map { it.frame })
        assertTrue(pulses.all { it.isMainBeat })
    }

    @Test
    fun `拍点落在缓冲区的实际偏移上`() {
        val mixer = MetronomeMixer(sampleRate, MetronomeConfig(bpm = 120))
        val buffer = ShortArray(blockFrames)

        // 第 0 块：起拍声，点击时长 50ms 约覆盖 2400 帧（约 10 块）
        val first = mixer.render(buffer)
        assertEquals(1, first.size)
        assertTrue("首块应包含起拍声", buffer.maxOf { abs(it.toInt()) } > 1_000)

        repeat(9) { mixer.render(buffer) }

        // 第 10-92 块（帧 2560-23807）应完全静音
        repeat(83) {
            mixer.render(buffer)
            assertTrue("静音块应全为零", buffer.all { it == 0.toShort() })
        }

        // 第 93 块覆盖帧 23808-24063，拍点在 24000，即偏移 192
        val second = mixer.render(buffer)
        assertEquals(1, second.size)
        assertEquals(24_000L, second.first().frame)
        assertTrue("声音应从偏移 192 开始", buffer.copyOfRange(0, 192).all { it == 0.toShort() })
        assertTrue("该块应包含第二拍", buffer.copyOfRange(192, blockFrames).maxOf { abs(it.toInt()) } > 1_000)
    }

    @Test
    fun `细分使触发次数按倍数增加`() {
        val mixer = MetronomeMixer(sampleRate, MetronomeConfig(bpm = 120, subdivisions = 4))
        val buffer = ShortArray(blockFrames)
        val pulses =
            buildList {
                repeat(sampleRate / blockFrames) { addAll(mixer.render(buffer)) }
            }
        // 120 BPM 四分音符 2 拍/秒，四细分即 8 个触发，其中主拍 2 个
        assertEquals(8, pulses.size)
        assertEquals(2, pulses.count { it.isMainBeat })
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3), pulses.map { it.subIndex })
    }

    @Test
    fun `重音只出现在小节第一拍`() {
        val mixer = MetronomeMixer(sampleRate, MetronomeConfig(bpm = 120, beatsPerBar = 4))
        val buffer = ShortArray(blockFrames)
        val pulses =
            buildList {
                // 4 秒 = 8 拍 = 两个 4/4 小节
                repeat(sampleRate * 4 / blockFrames) { addAll(mixer.render(buffer)) }
            }
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3), pulses.map { it.beatInBar })
        assertEquals(listOf(0L, 96_000L), pulses.filter { it.isDownbeat }.map { it.frame })
    }

    @Test
    fun `运行中改速度下一个拍点保持原时刻，之后按新速度`() {
        val mixer = MetronomeMixer(sampleRate, MetronomeConfig(bpm = 120))
        val buffer = ShortArray(blockFrames)
        val pulses = mutableListOf<BeatPulse>()

        // 94 块 = 24064 帧，已包含 0 与 24000 两个拍点
        repeat(94) { pulses += mixer.render(buffer) }
        assertEquals(listOf(0L, 24_000L), pulses.map { it.frame })

        mixer.updateConfig(MetronomeConfig(bpm = 60))
        repeat(480) { pulses += mixer.render(buffer) }

        // 已排定的 48000 不移动，之后按 60 BPM（48000 帧/拍）继续
        assertEquals(listOf(0L, 24_000L, 48_000L, 96_000L, 144_000L), pulses.map { it.frame })
    }
}
