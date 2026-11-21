package dev.zmdld11.guitardojo.ui.screens.assess

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.zmdld11.guitardojo.core.audio.assess.ClickAnchor
import dev.zmdld11.guitardojo.core.audio.assess.NoteEventExtractor
import dev.zmdld11.guitardojo.core.audio.assess.PerformanceAligner
import dev.zmdld11.guitardojo.core.audio.assess.PerformanceScores
import dev.zmdld11.guitardojo.core.audio.assess.TakeBuffer
import dev.zmdld11.guitardojo.core.audio.assess.TakePlayer
import dev.zmdld11.guitardojo.core.audio.assess.assessmentTargets
import dev.zmdld11.guitardojo.core.audio.input.MicrophoneCapture
import dev.zmdld11.guitardojo.core.audio.phrase.PhraseScheduler
import dev.zmdld11.guitardojo.core.audio.phrase.PracticeEngine
import dev.zmdld11.guitardojo.core.audio.phrase.PracticePulse
import dev.zmdld11.guitardojo.core.audio.pitch.MpmPitchDetector
import dev.zmdld11.guitardojo.core.phrase.Phrase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 考核（跟弹判定）的状态机：只放拍点 + 录音 → 离线分析 → 结果。
 *
 * 录音与播放流的同步不靠起点对齐：分析时用拍点瞬态解出全局偏移（ClickAnchor），
 * 引擎/扬声器/麦克风三条链路的固定延迟都在这个偏移里。
 */
class AssessmentViewModel(
    application: Application,
) : AndroidViewModel(application) {
    enum class Phase { READY, RECORDING, ANALYZING, RESULT, PREVIEW }

    data class UiState(
        val phase: Phase = Phase.READY,
        val message: String? = null,
        val beat: Int = 0,
        val totalBeats: Int = 0,
        /** 用户中途结束（视为停过，直接判不干净）。 */
        val stoppedEarly: Boolean = false,
        val hasTake: Boolean = false,
        val playingTake: Boolean = false,
        val playingReference: Boolean = false,
        val scores: PerformanceScores? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    private val extractor = NoteEventExtractor(MpmPitchDetector(SAMPLE_RATE), SAMPLE_RATE)
    private val engine = PracticeEngine(SAMPLE_RATE)
    private val takeBuffer = TakeBuffer()
    private val takePlayer = TakePlayer()
    private val capture = MicrophoneCapture(SAMPLE_RATE, WINDOW_SIZE)

    private var phrase: Phrase? = null
    private var speed = 1.0
    private var lastBarIndex = -1
    private var lastBeatInBar = -1
    private var recordedBeats = 0
    private var tailJob: Job? = null
    private var takeSamples: FloatArray? = null

    init {
        viewModelScope.launch {
            engine.pulses.collect { onPulse(it) }
        }
    }

    /** 开始考核：先开麦克风再起引擎（只放拍点）。返回 false 表示麦克风不可用。 */
    fun startAssessment(
        target: Phrase,
        bpm: Double,
    ): Boolean {
        val tempo = target.bars.firstOrNull()?.tempoBpm ?: return false
        phrase = target
        speed = (bpm / tempo).coerceIn(PhraseScheduler.MIN_SPEED, PhraseScheduler.MAX_SPEED)
        val plan = PhraseScheduler.plan(target, SAMPLE_RATE, speed)
        lastBarIndex = plan.beatBarIndices.lastOrNull() ?: -1
        lastBeatInBar = plan.beatInBar.lastOrNull() ?: -1
        recordedBeats = 0
        takeBuffer.clear()
        takeSamples = null
        _uiState.value = UiState(phase = Phase.RECORDING, totalBeats = plan.beatFrames.size)
        if (!capture.start(takeBuffer::append)) {
            _uiState.value = UiState(message = "打不开麦克风，检查权限后重试")
            return false
        }
        engine.setPhrase(target, speed, clickEnabled = true, phraseMuted = true)
        engine.start()
        return true
    }

    /** 用户结束演奏；[stopped] 为 true 表示中途停过，直接判不干净。 */
    fun finishNow(stopped: Boolean) {
        tailJob?.cancel()
        stopAndAnalyze(stoppedEarly = stopped)
    }

    /** 重考一次：清掉录音与结果，回到就绪。 */
    fun resetForRetry() {
        tailJob?.cancel()
        stopEngine()
        takePlayer.stop()
        takeBuffer.clear()
        takeSamples = null
        _uiState.value = UiState()
    }

    /** 回放考核录音；再点一次停止。 */
    fun playTake() {
        if (_uiState.value.playingTake) {
            takePlayer.stop()
            _uiState.update { it.copy(playingTake = false) }
            return
        }
        val samples = takeSamples ?: return
        _uiState.update { it.copy(playingTake = true) }
        takePlayer.start(samples) { _uiState.update { it.copy(playingTake = false) } }
    }

    /** 回放示范（原速、带拍点）；再点一次停止。 */
    fun playReference() {
        if (_uiState.value.playingReference) {
            tailJob?.cancel()
            stopEngine()
            _uiState.update { it.copy(playingReference = false, phase = Phase.RESULT) }
            return
        }
        val target = phrase ?: return
        _uiState.update { it.copy(playingReference = true, phase = Phase.PREVIEW) }
        engine.setPhrase(target, speed, clickEnabled = true, phraseMuted = false)
        engine.start()
    }

    override fun onCleared() {
        tailJob?.cancel()
        runCatching { capture.stop() }
        stopEngine()
        takePlayer.stop()
    }

    private fun onPulse(pulse: PracticePulse) {
        val state = _uiState.value
        when (state.phase) {
            Phase.RECORDING -> {
                recordedBeats++
                _uiState.update { it.copy(beat = recordedBeats.coerceAtMost(it.totalBeats)) }
                if (pulse.barIndex == lastBarIndex && pulse.beatInBar == lastBeatInBar) {
                    tailJob =
                        viewModelScope.launch {
                            delay(TAIL_MS)
                            stopAndAnalyze(stoppedEarly = false)
                        }
                }
            }
            Phase.PREVIEW -> {
                if (pulse.barIndex == lastBarIndex && pulse.beatInBar == lastBeatInBar) {
                    tailJob =
                        viewModelScope.launch {
                            delay(PREVIEW_TAIL_MS)
                            stopEngine()
                            _uiState.update { it.copy(phase = Phase.RESULT, playingReference = false) }
                        }
                }
            }
            else -> Unit
        }
    }

    private fun stopAndAnalyze(stoppedEarly: Boolean) {
        if (_uiState.value.phase != Phase.RECORDING) return
        stopEngine()
        capture.stop()
        _uiState.update { it.copy(phase = Phase.ANALYZING, stoppedEarly = stoppedEarly) }
        val target = phrase ?: return
        viewModelScope.launch {
            val rate = engine.activeSampleRate
            val scores = withContext(Dispatchers.Default) { analyze(target, rate) }
            _uiState.update { it.copy(phase = Phase.RESULT, scores = scores, hasTake = true) }
        }
    }

    private fun analyze(
        target: Phrase,
        sampleRate: Int,
    ): PerformanceScores {
        val samples = takeBuffer.snapshot()
        takeSamples = samples
        val localExtractor =
            if (sampleRate == SAMPLE_RATE) {
                extractor
            } else {
                NoteEventExtractor(MpmPitchDetector(sampleRate), sampleRate)
            }
        val events = localExtractor.extract(samples)
        val plan = PhraseScheduler.plan(target, sampleRate, speed)
        val targets = assessmentTargets(target, plan)
        val anchor = ClickAnchor(sampleRate).findOffset(samples, targets.clickSamples)
        return PerformanceAligner(sampleRate).align(targets.notes, events, anchor)
    }

    private fun stopEngine() {
        runCatching { engine.stop() }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val WINDOW_SIZE = 2048

        /** 最后一个拍点之后留的尾音时长，覆盖最长音的衰减。 */
        const val TAIL_MS = 1_500L
        const val PREVIEW_TAIL_MS = 600L
    }
}
