package dev.zmdld11.guitardojo.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.zmdld11.guitardojo.core.phrase.Phrase
import dev.zmdld11.guitardojo.core.phrase.PhraseException
import dev.zmdld11.guitardojo.data.PhraseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhraseLibraryUiState(
    val isLoading: Boolean = true,
    val phrases: List<Phrase> = emptyList(),
    val errors: List<String> = emptyList(),
    val message: String? = null,
)

class PhraseLibraryViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = PhraseRepository(application)
    private val _uiState = MutableStateFlow(PhraseLibraryUiState())
    val uiState: StateFlow<PhraseLibraryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val library = repository.load()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    phrases = library.phrases,
                    errors = library.errors,
                )
            }
        }
    }

    fun importPhrase(uri: Uri) {
        viewModelScope.launch {
            runCatching { repository.import(uri) }
                .onSuccess { phrase ->
                    val library = repository.load()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            phrases = library.phrases,
                            errors = library.errors,
                            message = "已导入《${phrase.title}》",
                        )
                    }
                }.onFailure { error ->
                    val detail =
                        if (error is PhraseException) {
                            error.issues.joinToString("\n")
                        } else {
                            error.message ?: "未知错误"
                        }
                    _uiState.update { it.copy(message = "导入失败：\n$detail") }
                }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
