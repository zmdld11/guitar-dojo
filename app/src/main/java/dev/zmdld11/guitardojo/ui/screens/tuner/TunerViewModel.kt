package dev.zmdld11.guitardojo.ui.screens.tuner

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import dev.zmdld11.guitardojo.core.audio.input.MicrophoneCapture
import dev.zmdld11.guitardojo.core.audio.pitch.GuitarString
import dev.zmdld11.guitardojo.core.audio.pitch.GuitarTuning
import dev.zmdld11.guitardojo.core.audio.pitch.MpmPitchDetector
import dev.zmdld11.guitardojo.core.audio.pitch.MusicalNote
import dev.zmdld11.guitardojo.core.audio.pitch.NoteNaming
import dev.zmdld11.guitardojo.core.audio.pitch.PitchTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TunerMode(
    val label: String,
) {
    GUITAR("吉他调音"),
    CHROMATIC("半音阶"),
    PICKUP("拾音器"),
}

data class TunerUiState(
    val mode: TunerMode = TunerMode.GUITAR,
    val isListening: Boolean = false,
    val note: MusicalNote? = null,
    val matchedString: GuitarString? = null,
    val history: List<String> = emptyList(),
    val message: String? = null,
)

class TunerViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val detector = MpmPitchDetector(SAMPLE_RATE, WINDOW_SIZE)
    private val tracker = PitchTracker()
    private val capture = MicrophoneCapture(SAMPLE_RATE, WINDOW_SIZE)

    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    @Volatile
    private var lastSignalAtMillis: Long = 0L

    private var lastHistoryLabel: String? = null

    fun setMode(mode: TunerMode) {
        if (_uiState.value.mode == mode) return
        tracker.reset()
        lastHistoryLabel = null
        _uiState.update { it.copy(mode = mode, note = null, matchedString = null, history = emptyList()) }
    }

    fun start() {
        if (_uiState.value.isListening) return
        tracker.reset()
        val started = capture.start(::onFrame)
        _uiState.update {
            it.copy(
                isListening = started,
                message = if (started) null else "打不开麦克风，检查权限或是否被其他应用占用",
            )
        }
    }

    fun stop() {
        capture.stop()
        tracker.reset()
        _uiState.update { it.copy(isListening = false, note = null, matchedString = null) }
    }

    fun toggle() {
        if (_uiState.value.isListening) stop() else start()
    }

    override fun onCleared() {
        capture.stop()
        super.onCleared()
    }

    private fun onFrame(frame: FloatArray) {
        val smoothed = tracker.accept(detector.detect(frame))
        val now = SystemClock.elapsedRealtime()

        if (smoothed == null) {
            // 超过 600ms 没有稳定信号就把读数清掉，避免停留在一个过时的音上
            if (now - lastSignalAtMillis > STALE_SIGNAL_MILLIS && _uiState.value.note != null) {
                _uiState.update { it.copy(note = null, matchedString = null) }
            }
            return
        }

        val note = NoteNaming.fromFrequency(smoothed.frequencyHz) ?: return
        lastSignalAtMillis = now
        val state = _uiState.value
        val matched = if (state.mode == TunerMode.GUITAR) GuitarTuning.nearestString(note.frequencyHz) else null

        val history =
            if (state.mode == TunerMode.PICKUP && note.label != lastHistoryLabel) {
                lastHistoryLabel = note.label
                (listOf(note.label) + state.history).take(MAX_HISTORY)
            } else {
                state.history
            }

        _uiState.update {
            it.copy(
                note = note,
                matchedString = matched,
                history = history,
                message = null,
            )
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val WINDOW_SIZE = 2048
        const val STALE_SIGNAL_MILLIS = 600L
        const val MAX_HISTORY = 12
    }
}
