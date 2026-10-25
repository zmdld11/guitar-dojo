package dev.zmdld11.guitardojo.core.audio.phrase

import kotlin.math.floor
import kotlin.math.sin

/**
 * 4096 点正弦查找表。钢琴音色每帧要算 8 个谐波，直接调 Math.sin 在低端机上
 * 会成为瓶颈；查表加线性插值的误差 < 0.001，听不出来。
 */
internal object SineTable {
    private const val SIZE = 4096
    private val values = FloatArray(SIZE + 1) { sin(2.0 * Math.PI * it / SIZE).toFloat() }

    /** [turns] 以「圈」为单位，0.25 表示四分之一周期。 */
    fun sinTurns(turns: Double): Float {
        val wrapped = turns - floor(turns)
        val position = wrapped * SIZE
        val index = position.toInt()
        val fraction = (position - index).toFloat()
        return values[index] + (values[index + 1] - values[index]) * fraction
    }
}
