package br.com.saqz.groups.presentation.gameeditor

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch

/**
 * 4 · editor de jogo. O VUL-151 só monta o scaffold: carrega o jogo em edição e
 * mantém `isLoading` em criação. O formulário é VUL-153.
 */
class GameEditorViewModel(
    val groupId: String,
    private val gameId: String?,
    private val gameGateway: GameGateway,
) : MviViewModel<GameEditorState, GameEditorIntent, GameEditorEffect>(
    initialState = GameEditorState(isLoading = gameId != null),
) {
    private var loadGeneration = 0

    init {
        if (gameId != null) load()
    }

    override fun onIntent(intent: GameEditorIntent) {
        when (intent) {
            GameEditorIntent.Retry -> if (gameId != null) load()
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        update { it.copy(isLoading = true, loadFailed = false, error = null) }
        viewModelScope.launch {
            when (val result = gameGateway.read(GroupId(groupId), gameId!!)) {
                is SaqzResult.Failure -> showFailure(generation, result.error.toUiError())
                is SaqzResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    update { it.copy(isLoading = false, loadFailed = false, error = null) }
                }
            }
        }
    }

    private fun showFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }
}
