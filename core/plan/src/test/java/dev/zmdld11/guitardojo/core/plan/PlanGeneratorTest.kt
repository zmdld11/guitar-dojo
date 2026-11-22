package dev.zmdld11.guitardojo.core.plan

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanGeneratorTest {
    private val generator = PlanGenerator()

    // 2026-11-21 是周六（ISO 6），2026-11-16 是周一（ISO 1）
    private val saturday = LocalDate.of(2026, 11, 21).toEpochDay()
    private val monday = LocalDate.of(2026, 11, 16).toEpochDay()

    private val phrases =
        mapOf(
            CHROMATIC_BASIC to PhraseInfo(CHROMATIC_BASIC, "爬格子 · 基础四连音", listOf("热身", "爬格子"), 8, 80.0, "爬格子"),
            CHROMATIC_REVERSE to PhraseInfo(CHROMATIC_REVERSE, "爬格子 · 反向四连音", listOf("爬格子"), 8, 80.0, "爬格子"),
            SCALE_C to PhraseInfo(SCALE_C, "C 大调音阶 · 两个八度", listOf("音阶"), 4, 80.0, "音阶"),
            RIFF_E5 to PhraseInfo(RIFF_E5, "E5 强力和弦 Riff", listOf("Riff", "强力和弦"), 8, 90.0, "Riff"),
        )

    private val curriculum =
        Curriculum(
            id = "builtin.curriculum.starter",
            title = "入门 4 周",
            weeks =
                listOf(
                    CurriculumWeek(1, "左手独立与 1-2-3-4 指序", listOf(CHROMATIC_BASIC, SCALE_C), 80.0, listOf("theory.string-names")),
                    CurriculumWeek(2, "反向与跨弦", listOf(CHROMATIC_REVERSE, RIFF_E5), 80.0),
                ),
        )

    @Test
    fun `epochDay 转星期几`() {
        assertEquals(4, epochDayToWeekdayIso(0L))
        assertEquals(6, epochDayToWeekdayIso(saturday))
        assertEquals(1, epochDayToWeekdayIso(monday))
    }

    @Test
    fun `空历史 15 分钟档生成完整流程`() {
        val plan = generator.generate(input(minutes = 15))

        assertEquals(DayKind.GUITAR, plan.kind)
        assertEquals(listOf("tune", "setup", "warmup", "practice", "assess", "cooldown"), plan.tasks.map { kindOf(it) })
        assertEquals(15, plan.estimatedMinutes)

        val warmup = plan.tasks.filterIsInstance<PlanTask.Warmup>().single()
        assertEquals(SCALE_C, warmup.phraseId)
        assertEquals(40.0, warmup.bpm, 1e-9)
        assertEquals(2, warmup.loopBarCount)

        val practice = plan.tasks.filterIsInstance<PlanTask.Practice>().first { !it.isFullRun }
        assertEquals(CHROMATIC_BASIC, practice.phraseId)
        assertEquals(40.0, practice.bpm, 1e-9)
        assertEquals(0.5, practice.speed, 1e-9)
        assertEquals(1, practice.blocks)

        val assess = plan.tasks.filterIsInstance<PlanTask.Assess>().single()
        assertEquals(CHROMATIC_BASIC, assess.phraseId)
        assertEquals(40.0, assess.targetBpm, 1e-9)
    }

    @Test
    fun `60 分钟档排到期复习与完整跑`() {
        val progress =
            PhraseProgress(
                phraseId = CHROMATIC_BASIC,
                currentBpm = 80.0,
                targetBpm = 80.0,
                mastery = Mastery.MASTERED,
                nextReviewEpochDay = saturday,
                lastPracticedEpochDay = saturday - 5,
            )

        val plan = generator.generate(input(progress = mapOf(CHROMATIC_BASIC to progress)))

        assertEquals(
            listOf("tune", "setup", "warmup", "practice", "review", "practice", "assess", "cooldown"),
            plan.tasks.map { kindOf(it) },
        )
        assertEquals(60, plan.estimatedMinutes)

        val warmup = plan.tasks.filterIsInstance<PlanTask.Warmup>().single()
        assertEquals(CHROMATIC_BASIC, warmup.phraseId)
        assertEquals(80.0, warmup.bpm, 1e-9)
        assertTrue(warmup.reason.contains("上次练习 5 天前"))

        val review = plan.tasks.filterIsInstance<PlanTask.Review>().single()
        assertEquals(CHROMATIC_BASIC, review.phraseId)
        assertEquals(80.0, review.bpm, 1e-9)
        assertTrue(review.reason.contains("到期"))

        val fullRun = plan.tasks.filterIsInstance<PlanTask.Practice>().last()
        assertTrue(fullRun.isFullRun)
        assertEquals(4, fullRun.loopBarCount)
        assertEquals(2, fullRun.blocks)

        val assess = plan.tasks.filterIsInstance<PlanTask.Assess>().single()
        assertEquals(SCALE_C, assess.phraseId)
        assertEquals(40.0, assess.targetBpm, 1e-9)
    }

    @Test
    fun `昨天首次失败今天同句降速 10 percent 继续`() {
        val progress =
            PhraseProgress(
                phraseId = CHROMATIC_BASIC,
                currentBpm = 60.0,
                targetBpm = 80.0,
                consecutiveFails = 1,
                failDays = 1,
                lastFailEpochDay = saturday - 1,
                lastPracticedEpochDay = saturday - 1,
            )

        val plan = generator.generate(input(progress = mapOf(CHROMATIC_BASIC to progress)))

        val practice = plan.tasks.filterIsInstance<PlanTask.Practice>().first { !it.isFullRun }
        assertEquals(CHROMATIC_BASIC, practice.phraseId)
        assertEquals(54.0, practice.bpm, 1e-9)
        assertTrue(practice.reason.contains("降速 10%"))
    }

    @Test
    fun `连续 3 个训练日失败换同技术点替代句`() {
        val progress =
            PhraseProgress(
                phraseId = CHROMATIC_BASIC,
                currentBpm = 45.0,
                targetBpm = 80.0,
                consecutiveFails = 4,
                failDays = 3,
                lastFailEpochDay = saturday - 1,
                lastPracticedEpochDay = saturday - 1,
            )

        val plan = generator.generate(input(progress = mapOf(CHROMATIC_BASIC to progress)))

        val practice = plan.tasks.filterIsInstance<PlanTask.Practice>().first { !it.isFullRun }
        assertEquals(CHROMATIC_REVERSE, practice.phraseId)
        assertTrue(practice.reason.contains("换同技术点"))
    }

    @Test
    fun `没有替代乐句时拆成 2 小节并保持降速`() {
        val progress =
            PhraseProgress(
                phraseId = CHROMATIC_BASIC,
                currentBpm = 45.0,
                targetBpm = 80.0,
                consecutiveFails = 4,
                failDays = 3,
                lastFailEpochDay = saturday - 1,
                lastPracticedEpochDay = saturday - 1,
            )
        val singleTagCurriculum =
            Curriculum(
                id = "builtin.curriculum.starter",
                title = "入门 4 周",
                weeks = listOf(CurriculumWeek(1, "左手独立", listOf(CHROMATIC_BASIC, SCALE_C), 80.0)),
            )

        val plan =
            generator.generate(
                input(progress = mapOf(CHROMATIC_BASIC to progress), curriculum = singleTagCurriculum),
            )

        val practice = plan.tasks.filterIsInstance<PlanTask.Practice>().first { !it.isFullRun }
        assertEquals(CHROMATIC_BASIC, practice.phraseId)
        assertEquals(45.0, practice.bpm, 1e-9)
        assertEquals(2, practice.loopBarCount)
        assertTrue(practice.reason.contains("前 2 小节"))
    }

    @Test
    fun `在校日只排无琴任务`() {
        val plan =
            generator.generate(
                input(
                    today = monday,
                    theoryCardId = "theory.string-names",
                    practiceCardId = "practice.slow-first",
                ),
            )

        assertEquals(DayKind.NO_GUITAR, plan.kind)
        assertEquals(
            listOf("theory_card", "practice_card", "rhythm_game", "mental", "note"),
            plan.tasks.map { kindOf(it) },
        )
        assertEquals(
            1,
            plan.tasks
                .filterIsInstance<PlanTask.RhythmGame>()
                .single()
                .weekIndex,
        )
        assertEquals(
            "theory.string-names",
            plan.tasks
                .filterIsInstance<PlanTask.TheoryCard>()
                .single()
                .cardId,
        )
        assertEquals(
            CHROMATIC_BASIC,
            plan.tasks
                .filterIsInstance<PlanTask.MentalPractice>()
                .single()
                .phraseId,
        )
        assertEquals(10, plan.estimatedMinutes)
        assertTrue(plan.tasks.none { it is PlanTask.Tune || it is PlanTask.Practice })
    }

    @Test
    fun `课程引用的乐句缺失时跳过并给出 warning`() {
        val withMissing =
            Curriculum(
                id = "builtin.curriculum.starter",
                title = "入门 4 周",
                weeks = listOf(CurriculumWeek(1, "左手独立", listOf("local.gone", CHROMATIC_BASIC), 80.0)),
            )

        val plan = generator.generate(input(curriculum = withMissing))

        assertTrue(plan.warnings.any { it.contains("local.gone") })
        val practice = plan.tasks.filterIsInstance<PlanTask.Practice>().first { !it.isFullRun }
        assertEquals(CHROMATIC_BASIC, practice.phraseId)
    }

    @Test
    fun `考核昨天的主练句而不是今天的新句`() {
        val progress =
            PhraseProgress(
                phraseId = CHROMATIC_BASIC,
                currentBpm = 80.0,
                targetBpm = 80.0,
                mastery = Mastery.MASTERED,
                lastPracticedEpochDay = saturday - 1,
            )
        val attempts =
            listOf(
                AttemptRecord(
                    phraseId = CHROMATIC_BASIC,
                    epochDay = saturday - 1,
                    bpm = 80.0,
                    clean = true,
                    selfRating = SelfRating.OK,
                    reachedTargetSpeed = true,
                ),
            )

        val plan =
            generator.generate(
                input(progress = mapOf(CHROMATIC_BASIC to progress), attempts = attempts),
            )

        val assess = plan.tasks.filterIsInstance<PlanTask.Assess>().single()
        assertEquals(CHROMATIC_BASIC, assess.phraseId)
        assertEquals(80.0, assess.targetBpm, 1e-9)
        val practice = plan.tasks.filterIsInstance<PlanTask.Practice>().first { !it.isFullRun }
        assertEquals(SCALE_C, practice.phraseId)
    }

    @Test
    fun `同一输入生成同一份计划`() {
        val first = generator.generate(input())
        val second = generator.generate(input())

        assertEquals(first, second)
    }

    @Test
    fun `计划快照可以 JSON 往返`() {
        val plan = generator.generate(input(theoryCardId = "theory.string-names"))

        val json = Json.encodeToString(plan)
        val decoded = Json.decodeFromString<DailyPlan>(json)

        assertEquals(plan, decoded)
    }

    // ---- fixtures ----

    private fun input(
        today: Long = saturday,
        minutes: Int = 60,
        progress: Map<String, PhraseProgress> = emptyMap(),
        attempts: List<AttemptRecord> = emptyList(),
        theoryCardId: String? = null,
        practiceCardId: String? = null,
        curriculum: Curriculum = this.curriculum,
        phrases: Map<String, PhraseInfo> = this.phrases,
    ) = PlanInput(
        todayEpochDay = today,
        profile = TrainingProfile(minutesPerDay = minutes),
        curriculum = curriculum,
        phrases = phrases,
        progress = progress,
        recentAttempts = attempts,
        todayTheoryCardId = theoryCardId,
        todayPracticeCardId = practiceCardId,
    )

    private fun kindOf(task: PlanTask): String =
        when (task) {
            is PlanTask.Tune -> "tune"
            is PlanTask.Setup -> "setup"
            is PlanTask.Warmup -> "warmup"
            is PlanTask.Practice -> "practice"
            is PlanTask.Review -> "review"
            is PlanTask.Assess -> "assess"
            is PlanTask.TheoryCard -> "theory_card"
            is PlanTask.PracticeCard -> "practice_card"
            is PlanTask.MentalPractice -> "mental"
            is PlanTask.RhythmGame -> "rhythm_game"
            is PlanTask.NoteEntry -> "note"
            is PlanTask.Cooldown -> "cooldown"
        }

    private companion object {
        const val CHROMATIC_BASIC = "builtin.chromatic.basic"
        const val CHROMATIC_REVERSE = "builtin.chromatic.reverse"
        const val SCALE_C = "builtin.scale.c-major"
        const val RIFF_E5 = "builtin.riff.e5-power"
    }
}
