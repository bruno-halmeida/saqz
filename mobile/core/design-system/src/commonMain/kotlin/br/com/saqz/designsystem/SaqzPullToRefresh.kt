package br.com.saqz.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.action_refresh
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.designsystem.theme.saqzShadow
import org.jetbrains.compose.resources.stringResource

const val SAQZ_PULL_REFRESH_INDICATOR_TAG = "saqzPullRefreshIndicator"

/**
 * Puxar para atualizar (GER-002): a bola do Saqz num círculo branco, logo abaixo da barra de cima
 * — o [content] começa onde a barra termina, então o indicador nasce no topo dele.
 *
 * O gesto é o `pullRefresh` do Material 2, que já roda em Android e iOS. Puxar com [refreshing]
 * ligado não chama [onRefresh] de novo (GER-001); a ViewModel ainda guarda a busca em voo.
 * O leitor de tela não puxa: o container ganha a ação "Atualizar".
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SaqzPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentRefreshing by rememberUpdatedState(refreshing)
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val guardedRefresh = { if (!currentRefreshing) currentOnRefresh() }
    val state = rememberPullRefreshState(refreshing = refreshing, onRefresh = guardedRefresh)
    val label = stringResource(Res.string.action_refresh)
    Box(
        modifier = modifier
            .pullRefresh(state)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(label) {
                        guardedRefresh()
                        true
                    },
                )
            },
    ) {
        content()
        val progress = state.progress
        if (refreshing || progress > 0f) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag(SAQZ_PULL_REFRESH_INDICATOR_TAG)
                    .graphicsLayer {
                        // Desce do topo junto com o dedo; parado em 16dp enquanto atualiza.
                        val travel = if (refreshing) 1f else progress.coerceAtMost(1f)
                        translationY = (travel * INDICATOR_TRAVEL - INDICATOR_SIZE).dp.toPx()
                        alpha = if (refreshing) 1f else progress.coerceIn(0f, 1f)
                    }
                    .size(INDICATOR_SIZE.dp)
                    .saqzShadow(SaqzTheme.shadows.indicator, CircleShape)
                    .background(SaqzTheme.colors.surface, CircleShape)
                    .border(1.dp, SaqzTheme.colors.border, CircleShape),
            ) {
                SaqzSpinner(
                    size = SPINNER_SIZE.dp,
                    animating = refreshing,
                    modifier = Modifier.graphicsLayer { if (!refreshing) rotationZ = progress * 360f },
                )
            }
        }
    }
}

private const val INDICATOR_SIZE = 44
private const val SPINNER_SIZE = 26
private const val INDICATOR_TRAVEL = INDICATOR_SIZE + 16
