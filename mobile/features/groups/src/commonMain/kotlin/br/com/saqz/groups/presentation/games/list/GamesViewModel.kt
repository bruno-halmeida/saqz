package br.com.saqz.groups.presentation.games.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.saqz.groups.data.game.GameGateway
import br.com.saqz.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class GamesViewModel(
    private val gateway: GameGateway,
    testScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = testScope ?: viewModelScope
    private val mutableState = MutableStateFlow(GamesState())
    val state: StateFlow<GamesState> = mutableState.asStateFlow()

    private val effectChannel = Channel<GamesEffect>(Channel.BUFFERED)
    val effects: Flow<GamesEffect> = effectChannel.receiveAsFlow()

    private var generation = 0L
    private var today = "9999-12-31"
    private val emittedNavigations = mutableSetOf<String>()

    fun onIntent(intent: GamesIntent) {
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
        mutableState.value = GamesState(
            groupId = intent.groupId,
            role = intent.role,
            isLoading = true,
        )
        load(intent.groupId, generation, refresh = false)
    }

    private fun refresh() {
        val current = mutableState.value
        val groupId = current.groupId ?: return
        if (current.isLoading || current.isRefreshing) return

        mutableState.value = current.copy(isRefreshing = true, error = null)
        load(groupId, generation, refresh = true)
    }

    private fun load(groupId: String, operation: Long, refresh: Boolean) {
        scope.launch {
            when (val result = gateway.list(groupId)) {
                is NetworkResult.Success -> {
                    if (operation != generation) return@launch

                    val items = result.value
                        .sortedBy { it.startsAt }
                        .map { it.toGameListItem() }
                    mutableState.value = mutableState.value.copy(
                        upcoming = items.filter { it.isoLocalDate() >= today },
                        past = items.filter { it.isoLocalDate() < today }.reversed(),
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                }
                is NetworkResult.Failure -> {
                    if (operation != generation) return@launch

                    mutableState.value = mutableState.value.copy(
                        upcoming = if (refresh) mutableState.value.upcoming else emptyList(),
                        past = if (refresh) mutableState.value.past else emptyList(),
                        isLoading = false,
                        isRefreshing = false,
                        error = GamesLoadError.UNAVAILABLE,
                    )
                }
            }
        }
    }

    private fun open(gameId: String) {
        val current = mutableState.value
        val groupId = current.groupId ?: return
        if (current.isLoading) return
        if ((current.upcoming + current.past).none { it.id == gameId }) return

        if (emittedNavigations.add("game:$gameId")) {
            effectChannel.trySend(GamesEffect.OpenGame(groupId, gameId))
        }
    }

    private fun create() {
        val current = mutableState.value
        val groupId = current.groupId ?: return
        if (!current.canCreate || current.isLoading) return

        if (emittedNavigations.add("create")) {
            effectChannel.trySend(GamesEffect.OpenCreate(groupId))
        }
    }
}
