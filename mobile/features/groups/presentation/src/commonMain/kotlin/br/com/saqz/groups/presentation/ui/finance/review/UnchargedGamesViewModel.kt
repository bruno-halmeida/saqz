package br.com.saqz.groups.presentation.ui.finance.review

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.CashboxGateway
import br.com.saqz.groups.presentation.ui.finance.weekdayDayMonth
import kotlinx.coroutines.launch

/** Os jogos com gente sem cobrança, do mais recente ao mais antigo (a ordem do servidor). */
class UnchargedGamesViewModel(
    private val groupId: String,
    private val cashbox: CashboxGateway,
) : MviViewModel<UnchargedGamesState, UnchargedGamesIntent, UnchargedGamesEffect>(UnchargedGamesState()) {
    private var generation = 0L

    override fun handleIntent(intent: UnchargedGamesIntent) {
        when (intent) {
            UnchargedGamesIntent.Refresh -> load()
            is UnchargedGamesIntent.Open -> if (state.value.games.any { it.gameId == intent.gameId }) {
                emit(UnchargedGamesEffect.OpenReview(groupId, intent.gameId))
            }
        }
    }

    /** Toda volta à tela recarrega: o jogo revisado sai da lista. */
    private fun load() {
        val current = ++generation
        val firstLoad = state.value.games.isEmpty()
        if (firstLoad) update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            val result = cashbox.unchargedSummary(GroupId(groupId))
            if (current != generation) return@launch
            when (result) {
                is SaqzResult.Failure -> if (firstLoad) update { it.copy(isLoading = false, loadFailed = true) }
                is SaqzResult.Success -> update {
                    UnchargedGamesState(
                        isLoading = false,
                        games = result.value.games.map { game ->
                            UnchargedGameUi(
                                gameId = game.gameId,
                                title = listOfNotNull(weekdayDayMonth(game.localDate), game.venueName).joinToString(" · "),
                                subtitle = game.title,
                                peopleCount = game.peopleCount,
                            )
                        },
                    )
                }
            }
        }
    }
}
