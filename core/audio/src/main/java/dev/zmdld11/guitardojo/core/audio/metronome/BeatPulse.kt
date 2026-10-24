package dev.zmdld11.guitardojo.core.audio.metronome

/**
 * 一次拍点触发。
 *
 * @property frame 触发的绝对帧号（从引擎启动算起）。
 * @property beatInBar 该拍在小节内的序号，0 起。
 * @property subIndex 该拍内部的细分序号，0 为主拍。
 * @property isDownbeat 是否为小节第一拍。
 */
data class BeatPulse(
    val frame: Long,
    val beatInBar: Int,
    val subIndex: Int,
    val isDownbeat: Boolean,
) {
    /** 主拍（小节的整拍位置），细分音为 false。 */
    val isMainBeat: Boolean get() = subIndex == 0
}
