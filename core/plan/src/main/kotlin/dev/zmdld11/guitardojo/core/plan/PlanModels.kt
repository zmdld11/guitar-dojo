package dev.zmdld11.guitardojo.core.plan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 掌握度状态：学习 → 巩固 → 掌握（掌握后进入间隔复习队列）。 */
@Serializable
enum class Mastery { LEARNING, CONSOLIDATING, MASTERED }

/** 单次考核后的自评。M5 前通过判定 = 非 HARD 且速度达标。 */
@Serializable
enum class SelfRating { EASY, OK, HARD }

/** 当日类型：琴上日 / 无琴日。 */
@Serializable
enum class DayKind { GUITAR, NO_GUITAR }

/** 一次记录的类型：主练/热身/考核算 PRACTICE，到期复习算 REVIEW。 */
@Serializable
enum class AttemptKind { PRACTICE, REVIEW }

/** 设备调试步骤的音箱预设，按今日练习类型给出。 */
@Serializable
enum class AmpPreset { CLEAN, LIGHT_DRIVE, DRIVE }

/**
 * 用户训练配置。「我的」页可改；当日计划快照生成后，当天改配置次日才生效。
 */
@Serializable
data class TrainingProfile(
    val minutesPerDay: Int = 60,
    val noGuitarWeekdays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val allowSkipTuning: Boolean = true,
    val weeklyGoal: Int = 4,
    val graceDaysPerMonth: Int = 2,
)

/**
 * 单条乐句的练习状态。由 [ProgressEngine] 纯函数计算并整体写回，引擎本身不写库。
 */
@Serializable
data class PhraseProgress(
    val phraseId: String,
    val currentBpm: Double,
    val targetBpm: Double,
    val cleanStreak: Int = 0,
    val consecutiveFails: Int = 0,
    val passDays: Int = 0,
    val failDays: Int = 0,
    val lastPassEpochDay: Long? = null,
    val lastFailEpochDay: Long? = null,
    val speedUpMarker: Int = 0,
    val lastPracticedEpochDay: Long? = null,
    val nextReviewEpochDay: Long? = null,
    val reviewStage: Int = 0,
    val inMaintenance: Boolean = false,
    val mastery: Mastery = Mastery.LEARNING,
) {
    /** 连续 3 个训练日失败后，计划改排同技术点替代句或拆小节。 */
    val switchPending: Boolean get() = failDays >= 3

    /** 当前练习速度相对目标速度的倍率。 */
    val speedRatio: Double get() = if (targetBpm > 0.0) currentBpm / targetBpm else 1.0
}

/**
 * 一次考核/练习的结果。写入只发生在用户完成考核或自评时。
 */
@Serializable
data class AttemptRecord(
    val phraseId: String,
    val epochDay: Long,
    val kind: AttemptKind = AttemptKind.PRACTICE,
    val bpm: Double,
    val clean: Boolean,
    val selfRating: SelfRating,
    val reachedTargetSpeed: Boolean,
    val tuningPassed: Boolean = true,
)

/**
 * 规则引擎需要的乐句元数据。App 侧从乐句库构建，避免 :core:plan 依赖 :core:phrase。
 */
@Serializable
data class PhraseInfo(
    val id: String,
    val title: String = "",
    val tags: List<String> = emptyList(),
    val barCount: Int = 1,
    val tempoBpm: Double = 80.0,
    /** 技术点标签（如 爬格子 / 音阶 / Riff），用于换句替代与复习闸门。 */
    val techniqueTag: String? = null,
)

@Serializable
data class CurriculumWeek(
    val index: Int,
    val focus: String,
    val phraseIds: List<String>,
    val targetBpm: Double,
    val theoryCardIds: List<String> = emptyList(),
    /** 每周讲解：这个技术点在练什么、合格判据、常见坑。2–3 句，主练步展示。 */
    val focusNotes: String = "",
)

@Serializable
data class Curriculum(
    val schemaVersion: Int = 1,
    val id: String,
    val title: String,
    val weeks: List<CurriculumWeek>,
)

/**
 * 计划生成输入。全部显式传入，引擎不读库、不读时钟。
 */
data class PlanInput(
    val todayEpochDay: Long,
    val profile: TrainingProfile = TrainingProfile(),
    val curriculum: Curriculum,
    /** 可用乐句的元数据；curriculum 里引用但不在表中的 id 会被跳过并记 warning。 */
    val phrases: Map<String, PhraseInfo> = emptyMap(),
    val progress: Map<String, PhraseProgress> = emptyMap(),
    /** 近期尝试记录，按 epochDay 升序；用于「考核昨天的主练句」。 */
    val recentAttempts: List<AttemptRecord> = emptyList(),
    /** 今日一卡的 id（App 侧由 CardScheduler 选出）；无琴日任务用到。 */
    val todayTheoryCardId: String? = null,
    val todayPracticeCardId: String? = null,
)

/** 计划中的一个任务，顺序即向导步骤顺序。 */
@Serializable
sealed interface PlanTask {
    val reason: String

    @Serializable
    @SerialName("tune")
    data class Tune(
        override val reason: String,
    ) : PlanTask

    @Serializable
    @SerialName("setup")
    data class Setup(
        val ampPreset: AmpPreset,
        override val reason: String,
    ) : PlanTask

    @Serializable
    @SerialName("warmup")
    data class Warmup(
        val phraseId: String,
        val bpm: Double,
        val speed: Double,
        val loopStartBar: Int,
        val loopBarCount: Int,
        override val reason: String,
    ) : PlanTask

    @Serializable
    @SerialName("practice")
    data class Practice(
        val phraseId: String,
        val bpm: Double,
        val speed: Double,
        val loopStartBar: Int,
        val loopBarCount: Int,
        /** 5 分钟练习块数量，块间提示休息。 */
        val blocks: Int,
        /** true 表示整段拼接/完整跑（60 分钟档收尾用）。 */
        val isFullRun: Boolean = false,
        override val reason: String,
        /** 当周技术点讲解，来自 curriculum 的 focusNotes；空表示本周无讲解。 */
        val focusNotes: String = "",
    ) : PlanTask

    @Serializable
    @SerialName("review")
    data class Review(
        val phraseId: String,
        val bpm: Double,
        val speed: Double,
        val blocks: Int,
        override val reason: String,
    ) : PlanTask

    @Serializable
    @SerialName("assess")
    data class Assess(
        val phraseId: String,
        val targetBpm: Double,
        override val reason: String,
    ) : PlanTask

    @Serializable
    @SerialName("theory_card")
    data class TheoryCard(
        val cardId: String,
        override val reason: String,
    ) : PlanTask

    @Serializable
    @SerialName("practice_card")
    data class PracticeCard(
        val cardId: String,
        override val reason: String,
    ) : PlanTask

    @Serializable
    @SerialName("mental")
    data class MentalPractice(
        val phraseId: String?,
        val minutes: Int,
        override val reason: String,
    ) : PlanTask

    /**
     * 无琴日的节奏对拍小游戏（冰与火之舞式，#33）：球转到圆心的瞬间点屏。
     * [weekIndex] 来自课程当前周，游戏侧据此挑内置关卡。
     */
    @Serializable
    @SerialName("rhythm_game")
    data class RhythmGame(
        val weekIndex: Int?,
        val minutes: Int,
        override val reason: String,
    ) : PlanTask

    @Serializable
    @SerialName("note")
    data class NoteEntry(
        override val reason: String,
    ) : PlanTask

    @Serializable
    @SerialName("cooldown")
    data class Cooldown(
        val deckId: String,
        override val reason: String,
    ) : PlanTask
}

/**
 * 一天的计划。tasks 顺序 = 向导步骤顺序；plan_json 快照存整份 DailyPlan。
 */
@Serializable
data class DailyPlan(
    val dateEpochDay: Long,
    val kind: DayKind,
    val minutesPerDay: Int,
    val tasks: List<PlanTask>,
    val estimatedMinutes: Int,
    val warnings: List<String> = emptyList(),
)

/** 1970-01-01 是周四（ISO 4）；epochDay → ISO 星期几（1=周一 … 7=周日）。 */
fun epochDayToWeekdayIso(epochDay: Long): Int = ((epochDay % 7 + 3 + 7) % 7).toInt() + 1
