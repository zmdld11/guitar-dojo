package dev.zmdld11.guitardojo.core.audio.rhythm

/** 音源模式：纯拍点咔哒，或内置乐句当伴奏（tile 恒为节拍格）。 */
enum class RhythmAudioMode {
    CLICK,
    PHRASE,
}

/**
 * 节奏关卡（冰与火之舞式）：[beats] 是相邻两拍的间隔序列，以四分音符为 1.0
 * ——1.0 = 180° 直行，0.5 = 90° 半拍，1/3 = 60° 三连。序列循环 [repeats] 遍。
 */
data class RhythmLevel(
    val id: String,
    val title: String,
    val description: String,
    val bpm: Double,
    val beats: List<Double>,
    val repeats: Int = 2,
    val audioMode: RhythmAudioMode = RhythmAudioMode.CLICK,
    /** PHRASE 模式的伴奏乐句 id（builtin.*）。 */
    val phraseId: String? = null,
    /** 绑定的课程周次（12 周骨架），无琴日按周挑关卡用。 */
    val weekHint: Int? = null,
)

/** 内置关卡：全部自编节奏型，按 12 周课程递进。 */
object BuiltInRhythmLevels {
    val all: List<RhythmLevel> =
        listOf(
            RhythmLevel(
                id = "rhythm.w1-quarters",
                title = "第一关 · 四分直线",
                description = "全部 180° 直行，稳定的一拍一声。先把「拍在哪」的手感立住。",
                bpm = 60.0,
                beats = List(8) { 1.0 },
                weekHint = 1,
            ),
            RhythmLevel(
                id = "rhythm.w2-long-short",
                title = "第二关 · 长短交替",
                description = "两拍一次 360° 大回环：数「走、走、转一圈」，别赶第二声。",
                bpm = 66.0,
                beats = listOf(1.0, 1.0, 2.0, 1.0, 1.0, 1.0, 2.0, 1.0),
                weekHint = 2,
            ),
            RhythmLevel(
                id = "rhythm.w3-eighths",
                title = "第三关 · 八分混拍",
                description = "一拍一声与半拍一声交替：90° 转角要连着点两下。",
                bpm = 72.0,
                beats = listOf(1.0, 0.5, 0.5, 1.0, 0.5, 0.5, 1.0, 1.0),
                weekHint = 3,
            ),
            RhythmLevel(
                id = "rhythm.w4-eighth-run",
                title = "第四关 · 八分连段",
                description = "连续八分推进：小角度连续转，手腕放松跟着走。",
                bpm = 76.0,
                beats = listOf(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1.0, 1.0),
                weekHint = 4,
            ),
            RhythmLevel(
                id = "rhythm.w5-triplets",
                title = "第五关 · 三连音",
                description = "60° 三连转角：三个音挤进一拍，别把最后一个赶早。",
                bpm = 72.0,
                beats = listOf(1.0, TRIPOD, TRIPOD, TRIPOD, 1.0, TRIPOD, TRIPOD, TRIPOD),
                weekHint = 5,
            ),
            RhythmLevel(
                id = "rhythm.w9-groove",
                title = "第六关 · riff 律动",
                description = "强力和弦的节奏骨架：重拍稳、弱拍轻，休止处手别抢。",
                bpm = 88.0,
                beats = listOf(1.0, 1.0, 0.5, 0.5, 1.0, 0.5, 0.5, 1.0),
                weekHint = 9,
            ),
            RhythmLevel(
                id = "rhythm.w10-syncopation",
                title = "第七关 · 切分",
                description = "附点切分 0.75+0.25：先到 135° 再补一个小 45°，落点在拍子反面上。",
                bpm = 84.0,
                beats = listOf(0.75, 0.25, 1.0, 0.75, 0.25, 1.0, 1.0, 1.0),
                weekHint = 10,
            ),
        )

    const val TRIPOD = 1.0 / 3.0

    fun byId(id: String): RhythmLevel? = all.firstOrNull { it.id == id }

    /** 无琴日按课程周次挑关：映射不到的就近回退，没有更早的就回第一关。 */
    fun forWeek(weekIndex: Int?): RhythmLevel {
        if (weekIndex == null) return all.first()
        return all.firstOrNull { it.weekHint == weekIndex }
            ?: all.filter { (it.weekHint ?: 0) < weekIndex }.maxByOrNull { it.weekHint ?: 0 }
            ?: all.first()
    }
}
