package dev.zmdld11.guitardojo.ui.screens.tuner

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import dev.zmdld11.guitardojo.BuildConfig
import dev.zmdld11.guitardojo.core.audio.input.MicrophoneCapture
import dev.zmdld11.guitardojo.core.audio.pitch.GuitarString
import dev.zmdld11.guitardojo.core.audio.pitch.GuitarTuning
import dev.zmdld11.guitardojo.core.audio.pitch.MpmPitchDetector
import dev.zmdld11.guitardojo.core.audio.pitch.MusicalNote
import dev.zmdld11.guitardojo.core.audio.pitch.NoteNaming
import dev.zmdld11.guitardojo.core.audio.pitch.PitchResult
import dev.zmdld11.guitardojo.core.audio.pitch.PitchTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

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
    private var lastProbeLogAtMillis: Long = 0L

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
        val raw = detector.detect(frame)
        val smoothed = tracker.accept(raw)
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

        logProbe(raw, smoothed, note, matched, now)
    }

    /**
     * 真机验收用：把原始检测值与平滑后的读数打到 logcat，方便事后统计泛音误判。
     * 只在 debug 构建生效，release 不会输出。
     */
    private fun logProbe(
        raw: PitchResult?,
        smoothed: PitchResult,
        note: MusicalNote,
        matched: GuitarString?,
        now: Long,
    ) {
        if (!BuildConfig.DEBUG || now - lastProbeLogAtMillis < PROBE_LOG_INTERVAL_MILLIS) return
        lastProbeLogAtMillis = now
        Log.d(
            "TunerProbe",
            String.format(
                Locale.US,
                "raw=%s clear=%s smooth=%s note=%s cents=%s str=%d mode=%s",
                raw?.let { fmt(it.frequencyHz) } ?: "-",
                raw?.let { fmt(it.clarity) } ?: "-",
                fmt(smoothed.frequencyHz),
                note.label,
                fmt(note.cents),
                matched?.number ?: 0,
                _uiState.value.mode.name,
            ),
        )
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val WINDOW_SIZE = 2048
        const val STALE_SIGNAL_MILLIS = 600L
        const val MAX_HISTORY = 12
        const val PROBE_LOG_INTERVAL_MILLIS = 80L
    }
}
