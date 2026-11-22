package dev.zmdld11.guitardojo.ui.screens.cards

import dev.zmdld11.guitardojo.core.audio.phrase.PhraseTimbre
import dev.zmdld11.guitardojo.core.audio.phrase.PracticeEngine
import dev.zmdld11.guitardojo.data.PhraseRepository

/**
 * 卡片听感示例的播放控制：点「听示例」循环播放对应乐句（不开拍点），
 * 再点一次停止。同一时间只播一条，切卡/离开页面由调用方 [stop]。
 */
class CardSoundPlayer(
    private val repository: PhraseRepository,
    private val timbre: PhraseTimbre,
) {
    private var engine: PracticeEngine? = null

    /** toggle 后当前在播的乐句 id；null 表示没有在播。 */
    suspend fun toggle(phraseId: String): String? {
        if (engine?.isRunning == true) {
            stop()
            return null
        }
        val phrase = repository.find(phraseId) ?: return null
        stop()
        val newEngine = PracticeEngine()
        newEngine.setPhrase(phrase, clickEnabled = false)
        newEngine.setTimbre(timbre)
        newEngine.start()
        engine = newEngine
        return phraseId
    }

    fun stop() {
        engine?.let { soundEngine ->
            runCatching { soundEngine.stop() }
        }
        engine = null
    }
}
