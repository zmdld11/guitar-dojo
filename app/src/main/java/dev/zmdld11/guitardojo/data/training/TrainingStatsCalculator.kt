package dev.zmdld11.guitardojo.data.training

import dev.zmdld11.guitardojo.core.plan.DayKind
import dev.zmdld11.guitardojo.core.plan.TrainingProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 今日页展示用的统计。 */
data class TrainingStats(
    val streakDays: Int,
    val weekSessions: Int,
    val weekGoal: Int,
    val totalMinutes: Int,
    val todayFinished: Boolean,
)

/** 打卡统计：连续天数、本周有效次数、累计分钟。有效练习 = 琴上日 ≥5 分钟；无琴日完成即算。 */
class TrainingStatsCalculator(
    private val db: TrainingDatabase,
) {
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
}

/** 打卡口径统一在这里：琴上日 ≥5 分钟有效练习，无琴日完成即算。 */
internal fun isValidSession(entity: TrainingDayEntity): Boolean =
    entity.kind == DayKind.NO_GUITAR.name || entity.practiceSeconds >= VALID_SESSION_SECONDS

private const val VALID_SESSION_SECONDS = 5L * 60
