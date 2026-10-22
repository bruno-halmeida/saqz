package br.com.saqz.groups.presentation.ui.pastgames

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzPullToRefresh
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.groups.presentation.ui.RefreshFailedToast
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.pastgames.PastGameRowUi
import br.com.saqz.groups.presentation.pastgames.PastGamesIntent
import br.com.saqz.groups.presentation.pastgames.PastGamesState
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.components.UpcomingGameRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.past_games_empty
import br.com.saqz.groups.resources.past_games_load_more_failed
import br.com.saqz.groups.resources.past_games_load_more_retry
import br.com.saqz.groups.resources.past_games_loading_more
import br.com.saqz.groups.resources.past_games_title
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource

internal object PastGamesTags {
    const val List = "past-games-list"
    const val Loading = "past-games-loading"
    const val LoadFailure = "past-games-load-failure"
    const val Empty = "past-games-empty"
    const val LoadingMore = "past-games-loading-more"
    const val LoadMoreFailed = "past-games-load-more-failed"
    const val LoadMoreRetry = "past-games-load-more-retry"
    fun game(gameId: String) = "past-games-game-$gameId"
    fun month(header: String) = "past-games-month-$header"
}

/** Quanto do fim do último cartão ainda falta aparecer quando a próxima página já é pedida. */
private const val LoadMoreThresholdPx = 400

/**
 * JOGO-011. Puxar para atualizar (GER-002) envolve a lista; a falha mantém a lista e mostra o aviso.
 */
@Composable
internal fun PastGamesScreen(
    state: PastGamesState,
    onIntent: (PastGamesIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val metrics = SaqzTheme.metrics
    Column(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        SaqzTopAppBar(title = stringResource(Res.string.past_games_title), onBack = onBack)
        when {
            state.isLoading -> PastGamesSkeleton()
            state.loadFailed -> GroupLoadFailure(
                error = state.error,
                onRetry = { onIntent(PastGamesIntent.Retry) },
                modifier = Modifier.padding(metrics.horizontalPadding),
                testTag = PastGamesTags.LoadFailure,
            )
            state.rows.isEmpty() -> Text(
                text = stringResource(Res.string.past_games_empty),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
                modifier = Modifier.padding(metrics.horizontalPadding).testTag(PastGamesTags.Empty),
            )
            else -> Box(modifier = Modifier.fillMaxSize()) {
                SaqzPullToRefresh(
                    refreshing = state.isRefreshing,
                    onRefresh = { onIntent(PastGamesIntent.PullRefresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PastGamesList(state, onIntent, listState)
                }
                RefreshFailedToast(
                    visible = state.refreshFailed,
                    onDismiss = { onIntent(PastGamesIntent.DismissRefreshFailed) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun PastGamesList(state: PastGamesState, onIntent: (PastGamesIntent) -> Unit, listState: LazyListState) {
    val metrics = SaqzTheme.metrics
    // As linhas vêm em ordem; o mês é um cartão com o cabeçalho em cima.
    val months = remember(state.rows) { state.rows.groupBy { it.monthHeader }.toList() }
    val nearEnd by remember(listState) {
        derivedStateOf {
            // O mês é um item só: conta o fim do último cartão chegando perto da borda, não o item aparecer.
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index == info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + LoadMoreThresholdPx
        }
    }
    val currentOnIntent by rememberUpdatedState(onIntent)
    LaunchedEffect(listState, state.canLoadMore) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect {
            if (state.canLoadMore) currentOnIntent(PastGamesIntent.LoadMore)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(PastGamesTags.List),
        contentPadding = PaddingValues(
            start = metrics.horizontalPadding,
            end = metrics.horizontalPadding,
            top = metrics.blockGap,
            bottom = metrics.subGrid * 5,
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        // Um item por mês (cabeçalho e cartão): no máximo uns 15 jogos, e o cartão fica inteiro.
        months.forEachIndexed { monthIndex, (header, rows) ->
            item(key = "month-$header", contentType = "month") {
                Column(verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
                    Text(
                        text = header,
                        style = SaqzTheme.typography.eyebrow,
                        color = SaqzTheme.colors.textSecondary,
                        modifier = Modifier
                            .padding(top = if (monthIndex == 0) 0.dp else metrics.blockGap)
                            .semantics { heading() }
                            .testTag(PastGamesTags.month(header)),
                    )
                    SaqzCard(padded = false) {
                        rows.forEachIndexed { index, row ->
                            if (index > 0) SaqzDivider()
                            PastGameRow(row, onIntent)
                        }
                    }
                }
            }
        }
        if (state.isLoadingMore) {
            item(key = "loading-more") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = metrics.horizontalPadding).testTag(PastGamesTags.LoadingMore),
                    horizontalArrangement = Arrangement.spacedBy(metrics.grid, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SaqzSpinner(size = 20.dp)
                    Text(
                        text = stringResource(Res.string.past_games_loading_more),
                        style = SaqzTheme.typography.support,
                        color = SaqzTheme.colors.textSecondary,
                    )
                }
            }
        } else if (state.loadMoreFailed) {
            item(key = "load-more-failed") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = metrics.horizontalPadding).testTag(PastGamesTags.LoadMoreFailed),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(metrics.grid),
                ) {
                    Text(
                        text = stringResource(Res.string.past_games_load_more_failed),
                        style = SaqzTheme.typography.support,
                        color = SaqzTheme.colors.textSecondary,
                    )
                    SaqzButton(
                        label = stringResource(Res.string.past_games_load_more_retry),
                        onClick = { onIntent(PastGamesIntent.LoadMore) },
                        variant = SaqzButtonVariant.Secondary,
                        size = SaqzButtonSize.Sm,
                        modifier = Modifier.testTag(PastGamesTags.LoadMoreRetry),
                    )
                }
            }
        }
    }
}

@Composable
private fun PastGameRow(row: PastGameRowUi, onIntent: (PastGamesIntent) -> Unit) {
    UpcomingGameRow(
        day = row.day,
        month = row.month,
        title = row.title,
        meta = row.meta,
        contentDescription = row.contentDescription,
        onClick = { onIntent(PastGamesIntent.OpenGame(row.gameId)) },
        tag = PastGamesTags.game(row.gameId),
        alert = row.pending,
        muted = row.cancelled,
    ) {
        SaqzStatusChip(
            text = row.statusLabel,
            tone = if (row.cancelled) SaqzChipTone.Error else SaqzChipTone.Neutral,
        )
    }
}

@Composable
private fun PastGamesSkeleton() {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier.padding(metrics.horizontalPadding).testTag(PastGamesTags.Loading),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzSkeleton(width = 140.dp, height = 14.dp)
        repeat(4) { Box { SaqzSkeleton(height = 64.dp, radius = metrics.inputRadius) } }
    }
}
