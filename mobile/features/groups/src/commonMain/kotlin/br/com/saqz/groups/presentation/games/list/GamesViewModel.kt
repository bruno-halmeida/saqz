package br.com.saqz.groups.presentation.games.list

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.SaqzDateTimeFormatter
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.GameGateway
import kotlinx.coroutines.launch

class GamesViewModel(
    private val gateway: GameGateway,
    private val formatter: SaqzDateTimeFormatter = SaqzDateTimeFormatter(),
) : MviViewModel<GamesState, GamesIntent, GamesEffect>(GamesState()) {
    private var generation = 0L
    private var today = "9999-12-31"
    private val emittedNavigations = mutableSetOf<String>()

    override fun onIntent(intent: GamesIntent) {
        when (intent) {
            is GamesIntent.SelectGroup -> select(intent)
            GamesIntent.Refresh -> refresh()
            is GamesIntent.OpenGame -> open(intent.gameId)
            GamesIntent.OpenCreate -> create()
        }
    }

    private fun select(intent: GamesIntent.SelectGroup) {
        generation++
        today = intent.today
        emittedNavigations.clear()
        update {
            GamesState(
                groupId = intent.groupId,
                role = intent.role,
                isLoading = true,
            )
        }
        load(intent.groupId, generation, refresh = false)
    }

    private fun refresh() {
        val current = state.value
        val groupId = current.groupId ?: return
        if (current.isLoading || current.isRefreshing) return

        update { it.copy(isRefreshing = true, error = null) }
        load(groupId, generation, refresh = true)
    }

    private fun load(groupId: String, operation: Long, refresh: Boolean) {
        viewModelScope.launch {
            when (val result = gateway.list(GroupId(groupId))) {
                is SaqzResult.Success -> {
                    if (operation != generation) return@launch

                    val items = result.value
                        .sortedBy { it.startsAt }
                        .map { it.toGameListItem(formatter) }
                    update {
                        it.copy(
                            upcoming = items.filter { item -> item.localDateIso >= today },
                            past = items.filter { item -> item.localDateIso < today }.reversed(),
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                        )
                    }
                }
                is SaqzResult.Failure -> {
                    if (operation != generation) return@launch

                    update {
                        it.copy(
                            upcoming = if (refresh) it.upcoming else emptyList(),
                            past = if (refresh) it.past else emptyList(),
                            isLoading = false,
                            isRefreshing = false,
                            error = GamesLoadError.UNAVAILABLE,
                        )
                    }
                }
            }
        }
    }

    private fun open(gameId: String) {
        val current = state.value
        val groupId = current.groupId ?: return
        if (current.isLoading) return
        if ((current.upcoming + current.past).none { it.id == gameId }) return

        if (emittedNavigations.add("game:$gameId")) {
            emit(GamesEffect.OpenGame(groupId, gameId))
        }
    }

    private fun create() {
        val current = state.value
        val groupId = current.groupId ?: return
        if (!current.canCreate || current.isLoading) return

        if (emittedNavigations.add("create")) {
            emit(GamesEffect.OpenCreate(groupId))
        }
    }
}
