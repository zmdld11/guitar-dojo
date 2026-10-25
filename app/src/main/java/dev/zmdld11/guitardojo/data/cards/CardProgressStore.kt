package dev.zmdld11.guitardojo.data.cards

import android.content.Context

/** 卡片进度的纯文本编解码，便于单测；坏行直接跳过，不影响其他卡片。 */
object CardProgressCodec {
    fun encode(progress: Collection<CardProgress>): String =
        progress
            .sortedBy(CardProgress::cardId)
            .joinToString("\n") { item ->
                listOf(
                    item.cardId,
                    item.box.toString(),
                    item.nextReviewEpochDay.toString(),
                    item.lastReviewedEpochDay.toString(),
                ).joinToString(FIELD_SEPARATOR)
            }

    fun decode(text: String): Map<String, CardProgress> {
        val result = LinkedHashMap<String, CardProgress>()
        text.lineSequence().forEach { line ->
            val parts = line.split(FIELD_SEPARATOR)
            if (parts.size != FIELD_COUNT) return@forEach
            val cardId = parts[0].trim()
            val box = parts[1].toIntOrNull() ?: return@forEach
            val nextReview = parts[2].toLongOrNull() ?: return@forEach
            val lastReviewed = parts[3].toLongOrNull() ?: return@forEach
            if (cardId.isEmpty() || box < 0) return@forEach
            result[cardId] = CardProgress(cardId, box, nextReview, lastReviewed)
        }
        return result
    }

    private const val FIELD_SEPARATOR = "|"
    private const val FIELD_COUNT = 4
}

/** 卡片进度只存本地；数据量很小，用 SharedPreferences 足够，不引入数据库。 */
class CardProgressStore(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): Map<String, CardProgress> =
        CardProgressCodec.decode(
            preferences.getString(KEY_PROGRESS, "").orEmpty(),
        )

    fun save(progress: CardProgress) {
        val all = load().toMutableMap()
        all[progress.cardId] = progress
        preferences.edit().putString(KEY_PROGRESS, CardProgressCodec.encode(all.values)).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "card_progress"
        const val KEY_PROGRESS = "progress"
    }
}
