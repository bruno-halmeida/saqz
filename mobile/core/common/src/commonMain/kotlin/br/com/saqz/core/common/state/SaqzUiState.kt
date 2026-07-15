package br.com.saqz.core.common.state

sealed interface SaqzUiState<out T> {
    data object Loading : SaqzUiState<Nothing>

    data class Content<out T>(val value: T) : SaqzUiState<T>

    data object Empty : SaqzUiState<Nothing>

    data object Error : SaqzUiState<Nothing>
}
