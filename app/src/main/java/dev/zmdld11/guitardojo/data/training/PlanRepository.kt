package dev.zmdld11.guitardojo.data.training

import android.content.Context
import dev.zmdld11.guitardojo.core.plan.AttemptKind
import dev.zmdld11.guitardojo.core.plan.AttemptRecord
import dev.zmdld11.guitardojo.core.plan.Curriculum
import dev.zmdld11.guitardojo.core.plan.DailyPlan
import dev.zmdld11.guitardojo.core.plan.DayKind
import dev.zmdld11.guitardojo.core.plan.PhraseInfo
import dev.zmdld11.guitardojo.core.plan.PlanCodec
import dev.zmdld11.guitardojo.core.plan.PlanGenerator
import dev.zmdld11.guitardojo.core.plan.PlanInput
import dev.zmdld11.guitardojo.core.plan.ProgressEngine
import dev.zmdld11.guitardojo.core.plan.SelfRating
import dev.zmdld11.guitardojo.core.plan.TrainingProfile
import dev.zmdld11.guitardojo.data.cards.BuiltInCards
import dev.zmdld11.guitardojo.data.cards.CardDeck
import dev.zmdld11.guitardojo.data.cards.CardProgress
import dev.zmdld11.guitardojo.data.cards.CardProgressStore
import dev.zmdld11.guitardojo.data.cards.CardScheduler
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 一步完成时 App 要写的内容。规则引擎本身不写库；所有写操作都发生在用户完成任务时。 */
sealed interface StepResult {
    /** 调音完成或跳过。 */
    data class TuneDone(val passed: Boolean) : StepResult

    /** 练习类步骤完成；seconds 是这一步累计的有效练习时长。 */
    data class PracticeDone(val seconds: Long) : StepResult

    /** 考核/复习跟弹后的自评结果；stopped 表示中途停过（视为不干净）。 */
    data class Assessed(
        val phraseId: String,
        val kind: AttemptKind,
        val bpm: Double,
        val phraseTempoBpm: Double,
        val stopped: Boolean,
        val rating: SelfRating,
    ) : StepResult

    /** 卡片看完后的自评。 */
    data class CardRated(
        val cardId: String,
        val remembered: Boolean,
    ) : StepResult

    /** 只标记完成（设备卡、心理练习、一句记录、保养清单）。 */
    data object Simple : StepResult
}

/** 今日页展示用的统计。 */
data class TrainingStats(
    val streakDays: Int,
    val weekSessions: Int,
    val weekGoal: Int,
    val totalMinutes: Int,
    val todayFinished: Boolean,
)

/** 当日计划与断点状态。 */
data class TrainingDaySnapshot(
    val entity: TrainingDayEntity,
    val plan: DailyPlan,
    val completedSteps: List<Int>,
)

/**
 * 训练记录仓库：生成当日计划（幂等）、记录步骤完成、写考核结果并更新乐句进度。
 * 计划快照存 plan_json，当天重复进入读现有行；改设置次日生效。
 */
class PlanRepository(
    context: Context,
    private val db: TrainingDatabase = TrainingDatabase.get(context),
    private val profileStore: TrainingProfileStore = TrainingProfileStore(context),
    private val cardStore: CardProgressStore = CardProgressStore(context),
) {
    private val engine = ProgressEngine()
    private val generator = PlanGenerator()
    private val scheduler = CardScheduler()

    suspend fun loadOrCreate(
        today: Long,
        curriculum: Curriculum,
        phrases: Map<String, PhraseInfo>,
    ): TrainingDaySnapshot =
        withContext(Dispatchers.IO) {
            val existing = db.trainingDayDao().get(today)
            if (existing != null) {
                return@withContext TrainingDaySnapshot(
                    entity = existing,
                    plan = PlanCodec.decodePlan(existing.planJson),
                    completedSteps = PlanCodec.decodeCompletedSteps(existing.completedSteps),
                )
            }
            val profile = profileStore.load()
            val progress = db.phraseProgressDao().all().associate { it.phraseId to it.toModel() }
            val attempts = db.attemptDao().since(today - ATTEMPT_LOOKBACK_DAYS).map { it.toModel() }
            val plan =
                generator.generate(
                    PlanInput(
                        todayEpochDay = today,
                        profile = profile,
                        curriculum = curriculum,
                        phrases = phrases,
                        progress = progress,
                        recentAttempts = attempts,
                        todayTheoryCardId = dailyCard(CardDeck.THEORY, today),
                        todayPracticeCardId = dailyCard(CardDeck.PRACTICE, today),
                    ),
                )
            val entity =
                TrainingDayEntity(
                    epochDay = today,
                    kind = plan.kind.name,
                    planJson = PlanCodec.encodePlan(plan),
                    currentStep = 0,
                    completedSteps = PlanCodec.encodeCompletedSteps(emptyList()),
                    practiceSeconds = 0,
                    tuningPassed = plan.kind != DayKind.GUITAR,
                    startedAtMillis = System.currentTimeMillis(),
                    finishedAtMillis = null,
                )
            db.trainingDayDao().upsert(entity)
            TrainingDaySnapshot(entity, plan, emptyList())
        }

    fun profile(): TrainingProfile = profileStore.load()

    fun saveProfile(profile: TrainingProfile) = profileStore.save(profile)

    suspend fun completeStep(
        today: Long,
        stepIndex: Int,
        result: StepResult,
    ): Unit =
        withContext(Dispatchers.IO) {
            val entity = db.trainingDayDao().get(today) ?: return@withContext
            val plan = PlanCodec.decodePlan(entity.planJson)
            val completed = PlanCodec.decodeCompletedSteps(entity.completedSteps).toMutableList()
            if (stepIndex !in completed) completed += stepIndex

            var practiceSeconds = entity.practiceSeconds
            var tuningPassed = entity.tuningPassed
            when (result) {
                is StepResult.PracticeDone -> practiceSeconds += result.seconds.coerceAtLeast(0)
                is StepResult.TuneDone -> tuningPassed = result.passed
                is StepResult.CardRated -> recordCard(today, result)
                is StepResult.Assessed -> recordAssessment(today, entity.tuningPassed, result)
                StepResult.Simple -> Unit
            }
            val completedSet = completed.toSet()
            val finished =
                entity.finishedAtMillis
                    ?: if (plan.tasks.indices.all { it in completedSet }) System.currentTimeMillis() else null
            db.trainingDayDao().upsert(
                entity.copy(
                    currentStep = (stepIndex + 1).coerceAtMost(plan.tasks.size),
                    completedSteps = PlanCodec.encodeCompletedSteps(completed.sorted()),
                    practiceSeconds = practiceSeconds,
                    tuningPassed = tuningPassed,
                    finishedAtMillis = finished,
                ),
            )
        }

    suspend fun stats(
        today: Long,
        profile: TrainingProfile,
    ): TrainingStats =
        withContext(Dispatchers.IO) {
            val finished = db.trainingDayDao().finished()
            val date = LocalDate.ofEpochDay(today)
            val weekStart = date.minusDays((date.dayOfWeek.value - 1).toLong()).toEpochDay()
            val weekSessions =
                finished.count { it.epochDay in weekStart..(weekStart + 6) && isValidSession(it) }
            TrainingStats(
                streakDays = countStreak(today, finished.map { it.epochDay }.toSet()),
                weekSessions = weekSessions,
                weekGoal = profile.weeklyGoal,
                totalMinutes = (finished.sumOf { it.practiceSeconds } / 60).toInt(),
                todayFinished = finished.any { it.epochDay == today },
            )
        }

    suspend fun daysInRange(
        from: Long,
        to: Long,
    ): List<TrainingDayEntity> =
        withContext(Dispatchers.IO) {
            db.trainingDayDao().range(from, to)
        }

    suspend fun attemptsByDay(epochDay: Long): List<AttemptEntity> =
        withContext(Dispatchers.IO) {
            db.attemptDao().byDay(epochDay)
        }

    /** 重装前的简易导出；包含训练日、乐句进度、考核记录与卡片进度。 */
    suspend fun exportJson(): String =
        withContext(Dispatchers.IO) {
            buildJsonObject {
                put("schemaVersion", 1)
                put("exportedAt", System.currentTimeMillis())
                put("days", JsonArray(db.trainingDayDao().range(0, Long.MAX_VALUE).map { dayJson(it) }))
                put("phraseProgress", JsonArray(db.phraseProgressDao().all().map { progressJson(it) }))
                put("attempts", JsonArray(db.attemptDao().all().map { attemptJson(it) }))
                put("cards", JsonArray(cardStore.load().values.map { cardJson(it) }))
            }.toString()
        }

    private fun dailyCard(
        deck: CardDeck,
        today: Long,
    ): String? = scheduler.daily(BuiltInCards.byDeck(deck), cardStore.load(), today)?.id

    private suspend fun recordCard(
        today: Long,
        result: StepResult.CardRated,
    ) {
        val next =
            scheduler.review(
                cardStore.load()[result.cardId],
                result.cardId,
                today,
                result.remembered,
            )
        cardStore.save(next)
    }

    private suspend fun recordAssessment(
        today: Long,
        tuningPassed: Boolean,
        result: StepResult.Assessed,
    ) {
        val entity = db.phraseProgressDao().get(result.phraseId)
        val base = entity?.toModel() ?: engine.initialProgress(result.phraseId, result.phraseTempoBpm)
        val attempt =
            AttemptRecord(
                phraseId = result.phraseId,
                epochDay = today,
                kind = result.kind,
                bpm = result.bpm,
                clean = !result.stopped,
                selfRating = result.rating,
                reachedTargetSpeed = !result.stopped,
                tuningPassed = tuningPassed,
            )
        db.phraseProgressDao().upsert(engine.apply(base, attempt).toEntity())
        db.attemptDao().insert(attempt.toEntity(tuningPassed))
    }

    private fun isValidSession(entity: TrainingDayEntity): Boolean =
        entity.kind == DayKind.NO_GUITAR.name || entity.practiceSeconds >= VALID_SESSION_SECONDS

    private fun countStreak(
        today: Long,
        finishedDays: Set<Long>,
    ): Int {
        var day = if (today in finishedDays) today else today - 1
        var streak = 0
        while (day in finishedDays) {
            streak += 1
            day -= 1
        }
        return streak
    }

    private fun dayJson(entity: TrainingDayEntity): JsonObject =
        buildJsonObject {
            put("epochDay", entity.epochDay)
            put("kind", entity.kind)
            put("currentStep", entity.currentStep)
            put("practiceSeconds", entity.practiceSeconds)
            put("tuningPassed", entity.tuningPassed)
            put("startedAtMillis", entity.startedAtMillis)
            put("finishedAtMillis", entity.finishedAtMillis)
        }

    private fun progressJson(entity: PhraseProgressEntity): JsonObject =
        buildJsonObject {
            put("phraseId", entity.phraseId)
            put("currentBpm", entity.currentBpm)
            put("targetBpm", entity.targetBpm)
            put("passDays", entity.passDays)
            put("failDays", entity.failDays)
            put("mastery", entity.mastery)
            put("nextReviewEpochDay", entity.nextReviewEpochDay)
        }

    private fun attemptJson(entity: AttemptEntity): JsonObject =
        buildJsonObject {
            put("phraseId", entity.phraseId)
            put("epochDay", entity.epochDay)
            put("kind", entity.kind)
            put("bpm", entity.bpm)
            put("clean", entity.clean)
            put("selfRating", entity.selfRating)
            put("tuningPassed", entity.tuningPassed)
        }

    private fun cardJson(progress: CardProgress): JsonObject =
        buildJsonObject {
            put("cardId", progress.cardId)
            put("box", progress.box)
            put("nextReviewEpochDay", progress.nextReviewEpochDay)
            put("lastReviewedEpochDay", progress.lastReviewedEpochDay)
        }

    private companion object {
        const val ATTEMPT_LOOKBACK_DAYS = 14L
        const val VALID_SESSION_SECONDS = 5L * 60
    }
}
