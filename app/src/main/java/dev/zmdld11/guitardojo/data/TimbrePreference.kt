package dev.zmdld11.guitardojo.data

import android.content.Context
import dev.zmdld11.guitardojo.core.audio.phrase.PhraseTimbre

/** 乐句音色是全局偏好，换乐句、杀进程后都保留。 */
class TimbrePreference(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): PhraseTimbre {
        if (preferences.getInt(KEY_VERSION, 0) < SETTINGS_VERSION) {
            // 升级换默认音色：旧版存下的值不再代表用户选择，一次性重置为新默认。
            preferences
                .edit()
                .putInt(KEY_VERSION, SETTINGS_VERSION)
                .putString(KEY_TIMBRE, PhraseTimbre.DEFAULT.id)
                .apply()
            return PhraseTimbre.DEFAULT
        }
        return PhraseTimbre.fromId(preferences.getString(KEY_TIMBRE, null))
    }

    fun save(timbre: PhraseTimbre) {
        preferences
            .edit()
            .putInt(KEY_VERSION, SETTINGS_VERSION)
            .putString(KEY_TIMBRE, timbre.id)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "phrase_settings"
        const val KEY_TIMBRE = "timbre"
        const val KEY_VERSION = "timbre_settings_version"
        const val SETTINGS_VERSION = 2
    }
}
