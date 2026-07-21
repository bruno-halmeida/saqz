package br.com.saqz.groups.presentation.games.editor

sealed interface GameEditorEffect {
    data class Saved(val id: String) : GameEditorEffect

    data class Reload(val groupId: String, val gameId: String) : GameEditorEffect
}
