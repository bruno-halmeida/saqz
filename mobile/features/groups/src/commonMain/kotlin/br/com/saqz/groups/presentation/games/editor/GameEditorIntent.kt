package br.com.saqz.groups.presentation.games.editor

import br.com.saqz.groups.domain.game.SeriesBoundaryScope

sealed interface GameEditorIntent {
    data class SetMode(val mode: GameEditorMode) : GameEditorIntent

    data class UpdateForm(val form: GameEditorForm) : GameEditorIntent

    data object AddSlot : GameEditorIntent

    data class SetScope(val scope: SeriesBoundaryScope) : GameEditorIntent

    data object Submit : GameEditorIntent

    data object Reload : GameEditorIntent
}
