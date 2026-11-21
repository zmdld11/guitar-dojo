package dev.zmdld11.guitardojo.data.training

import android.content.Context
import dev.zmdld11.guitardojo.core.plan.AttemptKind
import dev.zmdld11.guitardojo.core.plan.AttemptRecord
import dev.zmdld11.guitardojo.core.plan.Curriculum
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
import dev.zmdld11.guitardojo.data.cards.CardProgressStore
import dev.zmdld11.guitardojo.data.cards.CardScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    /** 无琴日的一句记录。 */
    data class NoteSaved(val text: String) : StepResult

    /** 只标记完成（设备卡、心理练习、保养清单）。 */
    data object Simple : StepResult
}

/** 当日计划与断点状态。 */
data class TrainingDaySnapshot(
    val entity: TrainingDayEntity,
    val plan: dev.zmdld11.guitardojo.core.plan.DailyPlan,
    val completedSteps: List<Int>,
)

/**
 * 训练记录仓库：生成当日计划（幂等）、记录步骤完成、写考核结果并更新乐句进度。
 * 计划快照存 plan_json，当天重复进入读现有行；改设置次日生效。
 */
/** 卡片步骤的读取与写入：今日一卡选择、评卡写回 SharedPreferences。 */
private class CardStepRecorder(
    private val cardStore: CardProgressStore,
) {
    private val scheduler = CardScheduler()

    fun dailyCard(
        deck: CardDeck,
        today: Long,
    ): String? = scheduler.daily(BuiltInCards.byDeck(deck), cardStore.load(), today)?.id

    fun record(
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
}

class PlanRepository(
    context: Context,
    private val db: TrainingDatabase = TrainingDatabase.get(context),
    private val profileStore: TrainingProfileStore = TrainingProfileStore(context),
    private val cardStore: CardProgressStore = CardProgressStore(context),
) {
    private val engine = ProgressEngine()
    private val generator = PlanGenerator()
    private val cardRecorder = CardStepRecorder(cardStore)
    private val statsCalculator = TrainingStatsCalculator(db)
    private val exporter = TrainingExporter(db, cardStore)

    /** 读当日计划；没有则按当前配置生成并落库（幂等）。 */
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
                        todayTheoryCardId = cardRecorder.dailyCard(CardDeck.THEORY, today),
                        todayPracticeCardId = cardRecorder.dailyCard(CardDeck.PRACTICE, today),
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
                    note = "",
                    startedAtMillis = System.currentTimeMillis(),
                    finishedAtMillis = null,
                )
            db.trainingDayDao().upsert(entity)
            TrainingDaySnapshot(entity, plan, emptyList())
        }

    fun profile(): TrainingProfile = profileStore.load()

    fun saveProfile(profile: TrainingProfile) = profileStore.save(profile)

    suspend fun stats(
        today: Long,
        profile: TrainingProfile,
    ): TrainingStats = statsCalculator.stats(today, profile)

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

    suspend fun exportJson(): String = exporter.exportJson()

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
            var note = entity.note
            when (result) {
                is StepResult.PracticeDone -> practiceSeconds += result.seconds.coerceAtLeast(0)
                is StepResult.TuneDone -> tuningPassed = result.passed
                is StepResult.CardRated -> cardRecorder.record(today, result)
                is StepResult.Assessed -> recordAssessment(today, entity.tuningPassed, result)
                is StepResult.NoteSaved -> note = result.text.trim()
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
                    note = note,
                    finishedAtMillis = finished,
                ),
            )
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

    private companion object {
        const val ATTEMPT_LOOKBACK_DAYS = 14L
    }
}
