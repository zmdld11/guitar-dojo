package dev.zmdld11.guitardojo.data

import android.content.Context
import dev.zmdld11.guitardojo.core.audio.phrase.PhraseTimbre

/** 乐句音色是全局偏好，换乐句、杀进程后都保留。 */
class TimbrePreference(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): PhraseTimbre = PhraseTimbre.fromId(preferences.getString(KEY_TIMBRE, null))

    fun save(timbre: PhraseTimbre) {
        preferences.edit().putString(KEY_TIMBRE, timbre.id).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "phrase_settings"
        const val KEY_TIMBRE = "timbre"
    }
}
