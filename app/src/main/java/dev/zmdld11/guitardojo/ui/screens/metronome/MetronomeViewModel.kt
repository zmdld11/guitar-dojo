package dev.zmdld11.guitardojo.ui.screens.metronome

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.zmdld11.guitardojo.core.audio.metronome.MetronomeConfig
import dev.zmdld11.guitardojo.metronome.MetronomeHolder
import dev.zmdld11.guitardojo.metronome.MetronomeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class MetronomeUiState(
    val bpm: Int = 80,
    val beatsPerBar: Int = 4,
    val subdivisions: Int = 1,
    val accentFirstBeat: Boolean = true,
    val isPlaying: Boolean = false,
    val activeBeat: Int = -1,
    val pulseCount: Long = 0L,
)

class MetronomeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MetronomeUiState())
    val uiState: StateFlow<MetronomeUiState> = _uiState.asStateFlow()

    private val tapTimestamps = mutableListOf<Long>()

    init {
        val stored = MetronomeHolder.config.value
        _uiState.update {
            it.copy(
                bpm = stored.bpm,
                beatsPerBar = stored.beatsPerBar,
                subdivisions = stored.subdivisions,
                accentFirstBeat = stored.accentFirstBeat,
            )
        }
        viewModelScope.launch {
            MetronomeHolder.isPlaying.collect { playing ->
                _uiState.update { it.copy(isPlaying = playing, activeBeat = if (playing) it.activeBeat else -1) }
            }
        }
        viewModelScope.launch {
            MetronomeHolder.engine.pulses.collect { pulse ->
                _uiState.update {
                    it.copy(activeBeat = pulse.beatInBar, pulseCount = it.pulseCount + 1)
                }
            }
        }
    }

    fun setBpm(value: Int) {
        val clamped = value.coerceIn(MetronomeConfig.MIN_BPM, MetronomeConfig.MAX_BPM)
        if (clamped == _uiState.value.bpm) return
        _uiState.update { it.copy(bpm = clamped) }
        pushConfig()
    }

    fun adjustBpm(delta: Int) = setBpm(_uiState.value.bpm + delta)

    fun setBeatsPerBar(value: Int) {
        val clamped = value.coerceIn(MetronomeConfig.MIN_BEATS_PER_BAR, MetronomeConfig.MAX_BEATS_PER_BAR)
        if (clamped == _uiState.value.beatsPerBar) return
        _uiState.update { it.copy(beatsPerBar = clamped) }
        pushConfig()
    }

    fun setSubdivisions(value: Int) {
        val clamped = value.coerceIn(MetronomeConfig.MIN_SUBDIVISIONS, MetronomeConfig.MAX_SUBDIVISIONS)
        if (clamped == _uiState.value.subdivisions) return
        _uiState.update { it.copy(subdivisions = clamped) }
        pushConfig()
    }

    fun toggleAccent() {
        _uiState.update { it.copy(accentFirstBeat = !it.accentFirstBeat) }
        pushConfig()
    }

    fun togglePlayback() {
        val context = getApplication<Application>()
        if (_uiState.value.isPlaying) {
            MetronomeService.stop(context)
        } else {
            MetronomeService.start(context)
        }
    }

    /**
     * Tap tempo：取最近几次点击间隔的中位数换算 BPM。
     * 超过 [TAP_RESET_GAP_MILLIS] 没有新点击则重新开始计数。
     */
    fun tap() {
        val now = SystemClock.elapsedRealtime()
        if (tapTimestamps.isNotEmpty() && now - tapTimestamps.last() > TAP_RESET_GAP_MILLIS) {
            tapTimestamps.clear()
        }
        tapTimestamps += now
        if (tapTimestamps.size > MAX_TAPS) tapTimestamps.removeAt(0)
        if (tapTimestamps.size < 2) return

        val intervals = tapTimestamps.zipWithNext { previous, next -> next - previous }.sorted()
        val median = intervals[intervals.size / 2]
        if (median > 0) setBpm((MILLIS_PER_MINUTE / median.toDouble()).roundToInt())
    }

    private fun pushConfig() {
        val state = _uiState.value
        MetronomeHolder.applyConfig(
            MetronomeConfig(
                bpm = state.bpm,
                beatsPerBar = state.beatsPerBar,
                subdivisions = state.subdivisions,
                accentFirstBeat = state.accentFirstBeat,
            ),
        )
    }

    private companion object {
        const val TAP_RESET_GAP_MILLIS = 2_000L
        const val MAX_TAPS = 5
        const val MILLIS_PER_MINUTE = 60_000.0
    }
}
