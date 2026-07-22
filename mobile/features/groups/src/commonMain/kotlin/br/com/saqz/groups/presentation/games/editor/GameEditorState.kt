package br.com.saqz.groups.presentation.games.editor

import androidx.compose.runtime.Immutable

@Immutable
data class GameEditorState(
    val draft: GameEditorDraft,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val globalValidationMessages: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: GameEditorError? = null,
    val reloadAvailable: Boolean = false,
    val successId: String? = null,
)
