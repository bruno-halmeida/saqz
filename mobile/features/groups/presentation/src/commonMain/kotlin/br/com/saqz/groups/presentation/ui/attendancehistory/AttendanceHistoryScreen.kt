package br.com.saqz.groups.presentation.ui.attendancehistory

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzPullToRefresh
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.attendancehistory.AttendanceHistoryIntent
import br.com.saqz.groups.presentation.attendancehistory.AttendanceHistoryRowUi
import br.com.saqz.groups.presentation.attendancehistory.AttendanceHistoryState
import br.com.saqz.groups.presentation.attendancehistory.AttendanceHistoryTone
import br.com.saqz.groups.presentation.photo.avatarSlot
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.RefreshFailedToast
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.history_empty
import br.com.saqz.groups.resources.history_load_more_failed
import br.com.saqz.groups.resources.history_load_more_retry
import br.com.saqz.groups.resources.history_loading_more
import br.com.saqz.groups.resources.history_title
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource

internal object AttendanceHistoryTags {
    const val List = "attendance-history-list"
    const val Loading = "attendance-history-loading"
    const val LoadFailure = "attendance-history-load-failure"
    const val Empty = "attendance-history-empty"
    const val Subtitle = "attendance-history-subtitle"
    const val LoadingMore = "attendance-history-loading-more"
    const val LoadMoreFailed = "attendance-history-load-more-failed"
    const val LoadMoreRetry = "attendance-history-load-more-retry"
    fun row(id: String) = "attendance-history-row-$id"
    fun day(header: String) = "attendance-history-day-$header"
}

/** Quanto do fim do último cartão ainda falta aparecer quando a próxima página já é pedida. */
private const val LoadMoreThresholdPx = 400

/** JOGO-012. Puxar para atualizar (GER-002) envolve a lista; a falha mantém a lista e mostra o aviso. */
@Composable
internal fun AttendanceHistoryScreen(
    state: AttendanceHistoryState,
    subtitle: String,
    onIntent: (AttendanceHistoryIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val metrics = SaqzTheme.metrics
    Column(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        SaqzTopAppBar(
            onBack = onBack,
            // Título e "Jogo de quinta, 22/10 · 20h00" embaixo, no lugar do título simples.
            logo = {
                Column {
                    Text(
                        text = stringResource(Res.string.history_title),
                        style = SaqzTheme.typography.subtitle,
                        color = SaqzTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = SaqzTheme.typography.support,
                            color = SaqzTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag(AttendanceHistoryTags.Subtitle),
                        )
                    }
                }
            },
        )
        when {
            state.isLoading -> AttendanceHistorySkeleton()
            state.loadFailed -> GroupLoadFailure(
                error = state.error,
                onRetry = { onIntent(AttendanceHistoryIntent.Retry) },
                modifier = Modifier.padding(metrics.horizontalPadding),
                testTag = AttendanceHistoryTags.LoadFailure,
            )
            else -> Box(modifier = Modifier.fillMaxSize()) {
                SaqzPullToRefresh(
                    refreshing = state.isRefreshing,
                    onRefresh = { onIntent(AttendanceHistoryIntent.PullRefresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.rows.isEmpty()) {
                        // Vazio também puxa: a lista vazia rola para a bola aparecer.
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = stringResource(Res.string.history_empty),
                                    style = SaqzTheme.typography.support,
                                    color = SaqzTheme.colors.textSecondary,
                                    modifier = Modifier.padding(metrics.horizontalPadding).testTag(AttendanceHistoryTags.Empty),
                                )
                            }
                        }
                    } else {
                        AttendanceHistoryList(state, onIntent, listState)
                    }
                }
                RefreshFailedToast(
                    visible = state.refreshFailed,
                    onDismiss = { onIntent(AttendanceHistoryIntent.DismissRefreshFailed) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun AttendanceHistoryList(
    state: AttendanceHistoryState,
    onIntent: (AttendanceHistoryIntent) -> Unit,
    listState: LazyListState,
) {
    val metrics = SaqzTheme.metrics
    val days = remember(state.rows) { state.rows.groupBy { it.dayHeader }.toList() }
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index == info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + LoadMoreThresholdPx
        }
    }
    val currentOnIntent by rememberUpdatedState(onIntent)
    LaunchedEffect(listState, state.canLoadMore) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect {
            if (state.canLoadMore) currentOnIntent(AttendanceHistoryIntent.LoadMore)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(AttendanceHistoryTags.List),
        contentPadding = PaddingValues(
            start = metrics.horizontalPadding,
            end = metrics.horizontalPadding,
            top = metrics.blockGap,
            bottom = metrics.subGrid * 5,
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        days.forEachIndexed { dayIndex, (header, rows) ->
            item(key = "day-$header", contentType = "day") {
                Column(verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
                    Text(
                        text = header,
                        style = SaqzTheme.typography.eyebrow,
                        color = SaqzTheme.colors.textSecondary,
                        modifier = Modifier
                            .padding(top = if (dayIndex == 0) 0.dp else metrics.blockGap)
                            .semantics { heading() }
                            .testTag(AttendanceHistoryTags.day(header)),
                    )
                    SaqzCard(padded = false) {
                        rows.forEachIndexed { index, row ->
                            if (index > 0) SaqzDivider()
                            AttendanceHistoryRow(row)
                        }
                    }
                }
            }
        }
        if (state.isLoadingMore) {
            item(key = "loading-more") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = metrics.horizontalPadding)
                        .testTag(AttendanceHistoryTags.LoadingMore),
                    horizontalArrangement = Arrangement.spacedBy(metrics.grid, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SaqzSpinner(size = 20.dp)
                    Text(
                        text = stringResource(Res.string.history_loading_more),
                        style = SaqzTheme.typography.support,
                        color = SaqzTheme.colors.textSecondary,
                    )
                }
            }
        } else if (state.loadMoreFailed) {
            item(key = "load-more-failed") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = metrics.horizontalPadding)
                        .testTag(AttendanceHistoryTags.LoadMoreFailed),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(metrics.grid),
                ) {
                    Text(
                        text = stringResource(Res.string.history_load_more_failed),
                        style = SaqzTheme.typography.support,
                        color = SaqzTheme.colors.textSecondary,
                    )
                    SaqzButton(
                        label = stringResource(Res.string.history_load_more_retry),
                        onClick = { onIntent(AttendanceHistoryIntent.LoadMore) },
                        variant = SaqzButtonVariant.Secondary,
                        size = SaqzButtonSize.Sm,
                        modifier = Modifier.testTag(AttendanceHistoryTags.LoadMoreRetry),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendanceHistoryRow(row: AttendanceHistoryRowUi) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val accent = if (row.currentlyGoing || row.guest) colors.primary else colors.textSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.grid + 4.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(AttendanceHistoryTags.row(row.id)),
        horizontalArrangement = Arrangement.spacedBy(metrics.grid + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzAvatar(
            name = row.avatarName,
            initialsColor = accent,
            photo = if (row.guest) {
                { SaqzIcon(SaqzIcons.UserPlus, tint = colors.primary, size = 18.dp) }
            } else {
                row.photo.avatarSlot(row.avatarName, initialsColor = accent)
            },
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = row.title, style = SaqzTheme.typography.label, color = colors.textPrimary)
            Text(text = row.meta, style = SaqzTheme.typography.support, color = colors.textSecondary)
            row.reason?.let { Text(text = it, style = SaqzTheme.typography.support, color = colors.textPrimary) }
        }
        SaqzStatusChip(
            text = row.statusLabel,
            tone = when (row.tone) {
                AttendanceHistoryTone.Going -> SaqzChipTone.Success
                AttendanceHistoryTone.Waiting -> SaqzChipTone.Warning
                AttendanceHistoryTone.NotGoing -> SaqzChipTone.Neutral
            },
            dot = row.tone == AttendanceHistoryTone.Going,
        )
    }
}

@Composable
private fun AttendanceHistorySkeleton() {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier.padding(metrics.horizontalPadding).testTag(AttendanceHistoryTags.Loading),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzSkeleton(width = 80.dp, height = 14.dp)
        repeat(4) { Box { SaqzSkeleton(height = 64.dp, radius = metrics.inputRadius) } }
    }
}
