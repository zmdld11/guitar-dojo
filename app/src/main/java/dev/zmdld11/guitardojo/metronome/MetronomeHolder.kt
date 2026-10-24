package dev.zmdld11.guitardojo.metronome

import android.util.Log
import dev.zmdld11.guitardojo.BuildConfig
import dev.zmdld11.guitardojo.core.audio.metronome.MetronomeConfig
import dev.zmdld11.guitardojo.core.audio.metronome.MetronomeEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 节拍器的进程内单例。
 *
 * 界面与前台服务共用同一个引擎实例，因此锁屏、切后台后节奏不中断，
 * 返回界面时状态也一致。这是本项目里唯一允许的可变全局状态。
 */
object MetronomeHolder {
    val engine: MetronomeEngine = MetronomeEngine()

    private val _config = MutableStateFlow(MetronomeConfig())
    val config: StateFlow<MetronomeConfig> = _config.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        if (BuildConfig.DEBUG) {
            // 漂移测量钩子，用法见 docs/节拍器漂移测量.md；release 构建不安装
            engine.pulseObserver = { pulse, elapsedNanos ->
                Log.d(DRIFT_TAG, "${pulse.frame},$elapsedNanos")
            }
        }
    }

    fun applyConfig(newConfig: MetronomeConfig) {
        _config.value = newConfig
        engine.setConfig(newConfig)
    }

    fun start() {
        if (_isPlaying.value) return
        engine.setConfig(_config.value)
        engine.start()
        _isPlaying.value = true
    }

    fun stop() {
        if (!_isPlaying.value) return
        engine.stop()
        _isPlaying.value = false
    }

    private const val DRIFT_TAG = "MetronomeDrift"
}
