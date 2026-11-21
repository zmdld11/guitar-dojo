package dev.zmdld11.guitardojo.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.zmdld11.guitardojo.core.plan.Curriculum
import dev.zmdld11.guitardojo.core.plan.PhraseInfo
import dev.zmdld11.guitardojo.data.PhraseRepository
import dev.zmdld11.guitardojo.data.training.CurriculumSource
import dev.zmdld11.guitardojo.data.training.PlanRepository
import dev.zmdld11.guitardojo.data.training.TrainingDaySnapshot
import dev.zmdld11.guitardojo.data.training.TrainingStats
import dev.zmdld11.guitardojo.data.training.buildPhraseInfos
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 今日页：读/生成当日计划、打卡统计；ON_RESUME 时刷新。 */
class TodayViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val planRepository = PlanRepository(application)
    private val phraseRepository = PhraseRepository(application)
    private var curriculum: Curriculum? = null

    data class UiState(
        val loading: Boolean = true,
        val snapshot: TrainingDaySnapshot? = null,
        val stats: TrainingStats? = null,
        val error: String? = null,
    )

    private val initialState = UiState()
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                val today = LocalDate.now().toEpochDay()
                val snapshot = planRepository.loadOrCreate(today, loadCurriculum(), loadPhraseInfos())
                val stats = planRepository.stats(today, planRepository.profile())
                UiState(loading = false, snapshot = snapshot, stats = stats)
            }.onSuccess { state ->
                _state.value = state
            }.onFailure { error ->
                _state.value = initialState.copy(loading = false, error = error.message ?: "计划加载失败")
            }
        }
    }

    private suspend fun loadCurriculum(): Curriculum {
        curriculum?.let { return it }
        return CurriculumSource.load(getApplication()).also { curriculum = it }
    }

    private suspend fun loadPhraseInfos(): Map<String, PhraseInfo> =
        buildPhraseInfos(phraseRepository.load())
}
