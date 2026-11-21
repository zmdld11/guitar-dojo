package dev.zmdld11.guitardojo.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.plan.Curriculum
import dev.zmdld11.guitardojo.core.plan.PhraseInfo
import dev.zmdld11.guitardojo.core.plan.PlanTask
import dev.zmdld11.guitardojo.data.PhraseRepository
import dev.zmdld11.guitardojo.data.training.CurriculumSource
import dev.zmdld11.guitardojo.data.training.PlanRepository
import dev.zmdld11.guitardojo.data.training.StepResult
import dev.zmdld11.guitardojo.data.training.TrainingDaySnapshot
import dev.zmdld11.guitardojo.data.training.buildPhraseInfos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 训练向导：读当日计划、写步骤完成；每步完成即落库，支持断点续练。 */
class SessionViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val planRepository = PlanRepository(application)
    private val phraseRepository = PhraseRepository(application)
    private var curriculum: Curriculum? = null

    data class UiState(
        val loading: Boolean = true,
        val snapshot: TrainingDaySnapshot? = null,
        /** 当前步骤要练的乐句；考核/复习/练习/心理练习步骤有值。 */
        val currentPhrase: Phrase? = null,
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
                UiState(loading = false, snapshot = snapshot, currentPhrase = currentPhrase(snapshot), error = null)
            }.onSuccess { state ->
                _state.value = state
            }.onFailure { error ->
                _state.value = initialState.copy(loading = false, error = error.message ?: "计划加载失败")
            }
        }
    }

    fun completeStep(
        stepIndex: Int,
        result: StepResult,
    ) {
        viewModelScope.launch {
            planRepository.completeStep(LocalDate.now().toEpochDay(), stepIndex, result)
            refresh()
        }
    }

    private suspend fun currentPhrase(snapshot: TrainingDaySnapshot): Phrase? {
        val task = snapshot.plan.tasks.getOrNull(snapshot.entity.currentStep) ?: return null
        val phraseId =
            when (task) {
                is PlanTask.Warmup -> task.phraseId
                is PlanTask.Practice -> task.phraseId
                is PlanTask.Review -> task.phraseId
                is PlanTask.Assess -> task.phraseId
                is PlanTask.MentalPractice -> task.phraseId
                else -> null
            } ?: return null
        return phraseRepository.find(phraseId)
    }

    private suspend fun loadCurriculum(): Curriculum {
        curriculum?.let { return it }
        return CurriculumSource.load(getApplication()).also { curriculum = it }
    }

    private suspend fun loadPhraseInfos(): Map<String, PhraseInfo> = buildPhraseInfos(phraseRepository.load())
}
