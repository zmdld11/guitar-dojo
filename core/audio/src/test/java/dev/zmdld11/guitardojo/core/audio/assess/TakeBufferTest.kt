package dev.zmdld11.guitardojo.core.audio.assess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeBufferTest {
    @Test
    fun `append 累积帧并保持内容`() {
        val buffer = TakeBuffer()
        val first = floatArrayOf(0.1f, 0.2f, 0.3f)
        val second = floatArrayOf(-0.5f, 0.25f)

        buffer.append(first)
        buffer.append(second)

        assertEquals(5L, buffer.frameCount)
        assertTrue(buffer.snapshot().contentEquals(floatArrayOf(0.1f, 0.2f, 0.3f, -0.5f, 0.25f)))
    }

    @Test
    fun `append 复制帧 采集器复用数组时不被覆盖`() {
        val buffer = TakeBuffer()
        val reused = floatArrayOf(1f, 2f)

        buffer.append(reused)
        reused[0] = 9f

        assertEquals(1f, buffer.snapshot()[0])
    }

    @Test
    fun `stop 之后 append 被忽略 clear 重新接受`() {
        val buffer = TakeBuffer()
        buffer.append(floatArrayOf(1f))
        buffer.stop()
        buffer.append(floatArrayOf(2f))

        assertEquals(1L, buffer.frameCount)

        buffer.clear()
        buffer.append(floatArrayOf(3f, 4f))

        assertEquals(2L, buffer.frameCount)
        assertTrue(buffer.snapshot().contentEquals(floatArrayOf(3f, 4f)))
    }
}
