package dev.zmdld11.guitardojo.data.training

import android.content.Context
import dev.zmdld11.guitardojo.core.plan.TrainingProfile

/** 训练配置是全局偏好；当日计划快照生成后，当天改配置次日才生效。 */
class TrainingProfileStore(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): TrainingProfile {
        val weekdays =
            preferences
                .getString(KEY_NO_GUITAR, null)
                ?.split(",")
                ?.mapNotNull { it.toIntOrNull() }
                ?.toSet()
                ?: TrainingProfile().noGuitarWeekdays
        return TrainingProfile(
            minutesPerDay = preferences.getInt(KEY_MINUTES, TrainingProfile().minutesPerDay),
            noGuitarWeekdays = weekdays,
            allowSkipTuning = preferences.getBoolean(KEY_ALLOW_SKIP_TUNING, true),
            weeklyGoal = preferences.getInt(KEY_WEEKLY_GOAL, TrainingProfile().weeklyGoal),
        )
    }

    fun save(profile: TrainingProfile) {
        preferences
            .edit()
            .putInt(KEY_MINUTES, profile.minutesPerDay)
            .putString(KEY_NO_GUITAR, profile.noGuitarWeekdays.sorted().joinToString(","))
            .putBoolean(KEY_ALLOW_SKIP_TUNING, profile.allowSkipTuning)
            .putInt(KEY_WEEKLY_GOAL, profile.weeklyGoal)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "training_settings"
        const val KEY_MINUTES = "minutes_per_day"
        const val KEY_NO_GUITAR = "no_guitar_weekdays"
        const val KEY_ALLOW_SKIP_TUNING = "allow_skip_tuning"
        const val KEY_WEEKLY_GOAL = "weekly_goal"
    }
}
