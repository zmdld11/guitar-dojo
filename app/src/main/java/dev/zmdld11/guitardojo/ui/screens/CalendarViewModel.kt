package dev.zmdld11.guitardojo.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.zmdld11.guitardojo.data.PhraseRepository
import dev.zmdld11.guitardojo.data.training.AttemptEntity
import dev.zmdld11.guitardojo.data.training.PlanRepository
import dev.zmdld11.guitardojo.data.training.TrainingDayEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

/** 打卡日历：月视图完成标记 + 单日记录明细。 */
class CalendarViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val planRepository = PlanRepository(application)
    private val phraseRepository = PhraseRepository(application)

    data class UiState(
        val yearMonth: YearMonth = YearMonth.now(),
        val days: Map<Long, TrainingDayEntity> = emptyMap(),
        val selectedDay: Long? = null,
        val selectedAttempts: List<AttemptEntity> = emptyList(),
        val phraseTitles: Map<String, String> = emptyMap(),
    )

    private val initialState = UiState()
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load(YearMonth.now())
    }

    fun shiftMonth(delta: Long) {
        load(_state.value.yearMonth.plusMonths(delta))
    }

    fun selectDay(epochDay: Long?) {
        viewModelScope.launch {
            val attempts = epochDay?.let { planRepository.attemptsByDay(it) }.orEmpty()
            _state.value = _state.value.copy(selectedDay = epochDay, selectedAttempts = attempts)
        }
    }

    private fun load(yearMonth: YearMonth) {
        viewModelScope.launch {
            runCatching {
                val start = yearMonth.atDay(1).toEpochDay()
                val end = yearMonth.atEndOfMonth().toEpochDay()
                val days = planRepository.daysInRange(start, end).associateBy { it.epochDay }
                val titles =
                    phraseRepository
                        .load()
                        .phrases
                        .associate { it.id to it.title }
                initialState.copy(yearMonth = yearMonth, days = days, phraseTitles = titles)
            }.onSuccess { state ->
                _state.value = state
            }.onFailure {
                _state.value = initialState.copy(yearMonth = yearMonth)
            }
        }
    }
}
