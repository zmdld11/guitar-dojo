package dev.zmdld11.guitardojo.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TempoTest {
    @Test
    fun `120 BPM 每拍 500 毫秒`() {
        assertEquals(500.0, Tempo(120).beatIntervalMillis, 1e-9)
    }

    @Test
    fun `60 BPM 每拍 1000 毫秒`() {
        assertEquals(1000.0, Tempo(60).beatIntervalMillis, 1e-9)
    }

    @Test
    fun `超出范围抛出异常`() {
        assertThrows(IllegalArgumentException::class.java) { Tempo(0) }
        assertThrows(IllegalArgumentException::class.java) { Tempo(301) }
    }

    @Test
    fun `边界值可用`() {
        assertEquals(60_000.0, Tempo(1).beatIntervalMillis, 1e-9)
        assertEquals(200.0, Tempo(300).beatIntervalMillis, 1e-9)
    }
}
