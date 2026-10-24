package dev.zmdld11.guitardojo.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.zmdld11.guitardojo.core.audio.phrase.PracticeEngine
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.data.PhraseRepository
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
)

class PhrasePlayerViewModel(
    application: Application,
    private val phraseId: String,
) : AndroidViewModel(application) {
    private val repository = PhraseRepository(application)
    private val engine = PracticeEngine()

    private val _uiState = MutableStateFlow(PhrasePlayerUiState())
    val uiState: StateFlow<PhrasePlayerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadPhrase() }
        viewModelScope.launch {
            engine.pulses.collect { pulse ->
                _uiState.update { it.copy(currentBar = pulse.barIndex, currentBeat = pulse.beatInBar) }
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
        _uiState.update {
            it.copy(
                loopStartBar = newStart,
                loopEndBar = newEnd,
                currentBar = if (newStart != null && !it.isPlaying) newStart else it.currentBar,
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
        _uiState.update {
            it.copy(
                isLoading = false,
                phrase = phrase,
                currentBar = 0,
            )
        }
        engine.setPhrase(
            phrase = phrase,
            speed = _uiState.value.speed,
            loopStartBar = null,
            loopEndBar = null,
            clickEnabled = _uiState.value.clickEnabled,
        )
    }

    companion object {
        const val MIN_SPEED = 0.25
        const val MAX_SPEED = 1.5
    }
}
