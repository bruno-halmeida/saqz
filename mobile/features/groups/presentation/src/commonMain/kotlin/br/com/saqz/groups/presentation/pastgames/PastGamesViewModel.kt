package br.com.saqz.groups.presentation.pastgames

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.PastGame
import br.com.saqz.groups.domain.game.PastGamesGateway
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.presentation.game.gameDateLabel
import br.com.saqz.groups.presentation.game.gameLongDateLabel
import br.com.saqz.groups.presentation.game.gameShortMonthLabel
import br.com.saqz.groups.presentation.game.gameTimeLabel
import br.com.saqz.groups.presentation.game.gameTimeZone
import br.com.saqz.groups.presentation.game.gameWeekdayLabel
import br.com.saqz.groups.presentation.toUiError
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_upcoming_cd_row
import br.com.saqz.groups.resources.home_upcoming_row_title
import br.com.saqz.groups.resources.past_games_meta
import br.com.saqz.groups.resources.past_games_meta_cancelled
import br.com.saqz.groups.resources.past_games_meta_venue
import br.com.saqz.groups.resources.past_games_month_header
import br.com.saqz.groups.resources.past_games_pending
import br.com.saqz.groups.resources.past_games_status_cancelled
import br.com.saqz.groups.resources.past_games_status_completed
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.time.Instant

internal class PastGamesViewModel(
    val groupId: String,
    private val gateway: PastGamesGateway,
    private val groupGateway: GroupGateway,
) : MviViewModel<PastGamesState, PastGamesIntent, PastGamesEffect>(PastGamesState()) {

    /** Cada carga da primeira página invalida as respostas que ainda estão a caminho. */
    private var generation = 0
    private var cursor: String? = null

    /** Quadra padrão do grupo: a linha só mostra o local quando o jogo foi em outro (regra da agenda). */
    private var defaultVenueName: String? = null

    init {
        load(soft = false)
    }

    override fun handleIntent(intent: PastGamesIntent) {
        when (intent) {
            PastGamesIntent.Retry -> load(soft = false)
            PastGamesIntent.Refresh -> if (!state.value.isLoading) load(soft = true)
            PastGamesIntent.LoadMore -> loadMore()
            is PastGamesIntent.OpenGame -> emit(PastGamesEffect.OpenGame(intent.gameId))
        }
    }

    private fun load(soft: Boolean) {
        val current = ++generation
        update {
            if (soft) {
                it.copy(isRefreshing = true)
            } else {
                PastGamesState(isLoading = true)
            }
        }
        viewModelScope.launch {
            val venue = async { (groupGateway.read(GroupId(groupId)) as? SaqzResult.Success)?.value?.group?.profile?.defaultVenue?.name }
            val page = gateway.page(GroupId(groupId), cursor = null)
            defaultVenueName = venue.await()
            if (current != generation) return@launch
            when (page) {
                is SaqzResult.Success -> {
                    cursor = page.value.nextCursor
                    val rows = page.value.games.mapNotNull { it.toRow() }
                    update {
                        PastGamesState(isLoading = false, rows = rows, canLoadMore = cursor != null)
                    }
                }
                // Por cima, a falha não troca a lista por erro.
                is SaqzResult.Failure -> update {
                    if (soft) it.copy(isRefreshing = false)
                    else PastGamesState(isLoading = false, loadFailed = true, error = page.error.toUiError())
                }
            }
        }
    }

    private fun loadMore() {
        val current = state.value
        val after = cursor
        if (current.isLoading || current.isLoadingMore || current.isRefreshing || after == null) return
        val loadAtStart = generation
        update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
        viewModelScope.launch {
            val page = gateway.page(GroupId(groupId), cursor = after)
            if (loadAtStart != generation) return@launch
            when (page) {
                is SaqzResult.Success -> {
                    cursor = page.value.nextCursor
                    val known = state.value.rows.mapTo(HashSet()) { it.gameId }
                    val rows = page.value.games.filterNot { it.id in known }.mapNotNull { it.toRow() }
                    update { it.copy(rows = it.rows + rows, isLoadingMore = false, canLoadMore = cursor != null) }
                }
                is SaqzResult.Failure -> update { it.copy(isLoadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    private suspend fun PastGame.toRow(): PastGameRowUi? {
        val startsAt = runCatching { Instant.parse(startsAt) }.getOrNull() ?: return null
        val local = startsAt.toLocalDateTime(gameTimeZone(zoneId))
        val weekday = local.date.dayOfWeek.gameWeekdayLabel()
        val time = local.gameTimeLabel()
        val cancelled = status == GameStatus.Cancelled
        val statusLabel = getString(if (cancelled) Res.string.past_games_status_cancelled else Res.string.past_games_status_completed)
        val meta = when {
            cancelled -> getPluralString(Res.plurals.past_games_meta_cancelled, confirmedCount, confirmedCount)
            defaultVenueName != null && venueName.isNotBlank() && venueName != defaultVenueName ->
                getString(Res.string.past_games_meta_venue, confirmedCount, capacity, venueName)
            else -> getString(Res.string.past_games_meta, confirmedCount, capacity)
        }
        return PastGameRowUi(
            gameId = id,
            monthHeader = gameMonthYearHeader(local.date),
            day = local.day.toString(),
            month = gameShortMonthLabel(local.date.month.ordinal + 1),
            title = getString(Res.string.home_upcoming_row_title, weekday, time),
            meta = meta,
            cancelled = cancelled,
            statusLabel = statusLabel,
            pending = pendingChargeCount.takeIf { it > 0 }?.let { count ->
                getPluralString(Res.plurals.past_games_pending, count, count, formatBrl(pendingChargeCents))
            },
            contentDescription = getString(Res.string.home_upcoming_cd_row, weekday, local.date.gameDateLabel(), time, statusLabel),
        )
    }
}

/** "OUTUBRO DE 2026": cabeçalho de mês da lista; o mês sai do "20 de outubro" que o app já escreve. */
private suspend fun gameMonthYearHeader(date: kotlinx.datetime.LocalDate): String =
    getString(Res.string.past_games_month_header, date.gameLongDateLabel().substringAfter(" de "), date.year).uppercase()
