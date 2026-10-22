package br.com.saqz.groups.presentation.pastgames

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError

/**
 * "Jogos anteriores" do gestor (JOGO-011): encerrados e cancelados, do mais recente para trás,
 * separados por mês, 20 por vez ao rolar.
 */
@Immutable
internal data class PastGamesState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val rows: List<PastGameRowUi> = emptyList(),
    /** Há próxima página no servidor. */
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    /** Recarga por cima (voltar à tela ou puxar para atualizar): a lista na tela fica. */
    val isRefreshing: Boolean = false,
)

@Immutable
internal data class PastGameRowUi(
    val gameId: String,
    /** "OUTUBRO DE 2026": as linhas vêm em ordem e o mês muda onde o cabeçalho muda. */
    val monthHeader: String,
    val day: String,
    val month: String,
    val title: String,
    val meta: String,
    val cancelled: Boolean,
    val statusLabel: String,
    /** "Acerto pendente · 3 pessoas, R$ 75,00"; nulo sem diária aberta. */
    val pending: String?,
    val contentDescription: String,
)

internal sealed interface PastGamesIntent {
    data object Retry : PastGamesIntent

    /** Volta à tela ou puxar para atualizar: recarrega a primeira página sem tirar a lista. */
    data object Refresh : PastGamesIntent

    /** O fim da lista apareceu: busca a próxima página (uma por vez). */
    data object LoadMore : PastGamesIntent
    data class OpenGame(val gameId: String) : PastGamesIntent
}

internal sealed interface PastGamesEffect {
    data class OpenGame(val gameId: String) : PastGamesEffect
}
