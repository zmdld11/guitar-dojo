package dev.zmdld11.guitardojo.data.training

import dev.zmdld11.guitardojo.data.cards.CardProgress
import dev.zmdld11.guitardojo.data.cards.CardProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 重装前的简易导出：训练日、乐句进度、考核记录与卡片进度合并成一份 JSON。 */
class TrainingExporter(
    private val db: TrainingDatabase,
    private val cardStore: CardProgressStore,
) {
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
            entity.scoreAccuracy?.let { put("scoreAccuracy", it) }
            entity.scoreRhythm?.let { put("scoreRhythm", it) }
        }

    private fun cardJson(progress: CardProgress): JsonObject =
        buildJsonObject {
            put("cardId", progress.cardId)
            put("box", progress.box)
            put("nextReviewEpochDay", progress.nextReviewEpochDay)
            put("lastReviewedEpochDay", progress.lastReviewedEpochDay)
        }
}
