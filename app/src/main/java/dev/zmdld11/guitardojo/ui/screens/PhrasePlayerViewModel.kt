package dev.zmdld11.guitardojo.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.zmdld11.guitardojo.core.audio.phrase.PhraseTimbre
import dev.zmdld11.guitardojo.core.audio.phrase.PracticeEngine
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.data.PhraseRepository
import dev.zmdld11.guitardojo.data.TimbrePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhrasePlayerUiState(
    val isLoading: Boolean = true,
    val phrase: Phrase? = null,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val speed: Double = 1.0,
    val clickEnabled: Boolean = true,
    val loopStartBar: Int? = null,
    val loopEndBar: Int? = null,
    val currentBar: Int? = null,
    val currentBeat: Int = 0,
    /** 正在响的音在 [Phrase.notes] 中的下标。 */
    val currentNoteIndex: Int? = null,
    val timbre: PhraseTimbre = PhraseTimbre.DEFAULT,
    val showNoteNames: Boolean = false,
    val showPicking: Boolean = true,
)

class PhrasePlayerViewModel(
    application: Application,
    private val phraseId: String,
    initialSpeed: Double? = null,
    initialLoopBars: IntRange? = null,
) : AndroidViewModel(application) {
    private val repository = PhraseRepository(application)
    private val engine = PracticeEngine()
    private val timbrePreference = TimbrePreference(application)
    /** 训练向导预置的速度与循环区间；用户在页面上仍可调整。 */
    private val presetSpeed = initialSpeed?.coerceIn(MIN_SPEED, MAX_SPEED)
    private val presetLoop = initialLoopBars

    private val _uiState =
        MutableStateFlow(PhrasePlayerUiState(speed = presetSpeed ?: 1.0))
    val uiState: StateFlow<PhrasePlayerUiState> = _uiState.asStateFlow()

    init {
        val savedTimbre = timbrePreference.load()
        engine.setTimbre(savedTimbre)
        _uiState.update { it.copy(timbre = savedTimbre) }
        viewModelScope.launch { loadPhrase() }
        viewModelScope.launch {
            engine.pulses.collect { pulse ->
                _uiState.update { it.copy(currentBar = pulse.barIndex, currentBeat = pulse.beatInBar) }
            }
        }
        viewModelScope.launch {
            engine.notePulses.collect { pulse ->
                _uiState.update { it.copy(currentNoteIndex = pulse.phraseIndex) }
            }
        }
    }

    fun togglePlayback() {
        val state = _uiState.value
        val phrase = state.phrase ?: return
        if (state.isPlaying) {
            engine.stop()
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    currentBar = it.loopStartBar ?: 0,
                    currentBeat = 0,
                    currentNoteIndex = firstNoteIndex(it),
                )
            }
        } else {
            engine.setPhrase(
                phrase = phrase,
                speed = state.speed,
                loopStartBar = state.loopStartBar,
                loopEndBar = state.loopEndBar,
                clickEnabled = state.clickEnabled,
            )
            engine.start()
            _uiState.update {
                it.copy(
                    isPlaying = engine.isRunning,
                    currentBar = it.loopStartBar ?: 0,
                    currentBeat = 0,
                    currentNoteIndex = firstNoteIndex(it),
                )
            }
        }
    }

    fun setSpeed(speed: Double) {
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        if (clamped == _uiState.value.speed) return
        engine.setSpeed(clamped)
        _uiState.update { it.copy(speed = clamped) }
    }

    fun toggleClick() {
        val enabled = !_uiState.value.clickEnabled
        engine.setClickEnabled(enabled)
        _uiState.update { it.copy(clickEnabled = enabled) }
    }

    fun setTimbre(timbre: PhraseTimbre) {
        if (timbre == _uiState.value.timbre) return
        engine.setTimbre(timbre)
        timbrePreference.save(timbre)
        _uiState.update { it.copy(timbre = timbre) }
    }

    fun toggleNoteNames() {
        _uiState.update { it.copy(showNoteNames = !it.showNoteNames) }
    }

    fun togglePicking() {
        _uiState.update { it.copy(showPicking = !it.showPicking) }
    }

    /** 第一次点选单小节循环，第二次点另一小节扩成区间，点区间内部清除。 */
    fun selectBar(bar: Int) {
        val state = _uiState.value
        val start = state.loopStartBar
        val end = state.loopEndBar
        val newStart: Int?
        val newEnd: Int?
        when {
            start == null -> {
                newStart = bar
                newEnd = bar
            }
            end == null || start == end -> {
                if (bar == start) {
                    newStart = null
                    newEnd = null
                } else {
                    newStart = minOf(start, bar)
                    newEnd = maxOf(start, bar)
                }
            }
            bar in start..end -> {
                newStart = null
                newEnd = null
            }
            else -> {
                newStart = bar
                newEnd = bar
            }
        }
        engine.setLoop(newStart, newEnd)
        _uiState.update { current ->
            val next = current.copy(loopStartBar = newStart, loopEndBar = newEnd)
            next.copy(
                currentBar = if (newStart != null && !current.isPlaying) newStart else current.currentBar,
                currentNoteIndex = if (current.isPlaying) current.currentNoteIndex else firstNoteIndex(next),
            )
        }
    }

    fun clearLoop() {
        engine.setLoop(null, null)
        _uiState.update { it.copy(loopStartBar = null, loopEndBar = null) }
    }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }

    private suspend fun loadPhrase() {
        val phrase = repository.find(phraseId)
        if (phrase == null) {
            _uiState.update { it.copy(isLoading = false, error = "找不到乐句：$phraseId") }
            return
        }
        _uiState.update { current ->
            val withPreset =
                current.copy(
                    isLoading = false,
                    phrase = phrase,
                    loopStartBar = presetLoop?.first,
                    loopEndBar = presetLoop?.last,
                    currentBar = presetLoop?.first ?: 0,
                )
            withPreset.copy(currentNoteIndex = firstNoteIndex(withPreset))
        }
        engine.setPhrase(
            phrase = phrase,
            speed = _uiState.value.speed,
            loopStartBar = presetLoop?.first,
            loopEndBar = presetLoop?.last,
            clickEnabled = _uiState.value.clickEnabled,
        )
    }

    companion object {
        const val MIN_SPEED = 0.25
        const val MAX_SPEED = 1.5
    }
}

/** 当前循环区间里的第一个音；用于停止状态和刚开始播放时给 UI 一个落点。 */
private fun firstNoteIndex(state: PhrasePlayerUiState): Int? {
    val phrase = state.phrase ?: return null
    if (phrase.notes.isEmpty()) return null
    val startBar = (state.loopStartBar ?: 0).coerceIn(0, phrase.bars.lastIndex.coerceAtLeast(0))
    val endBar = (state.loopEndBar ?: phrase.bars.lastIndex).coerceIn(startBar, phrase.bars.lastIndex.coerceAtLeast(0))
    val from = phrase.bars.getOrNull(startBar)?.startTick ?: 0L
    val to = phrase.bars.getOrNull(endBar)?.let { it.startTick + it.lengthTicks } ?: Long.MAX_VALUE
    val index = phrase.notes.indexOfFirst { it.tick >= from && it.tick < to }
    return index.takeIf { it >= 0 } ?: 0
}
