package br.com.saqz.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import br.com.saqz.core.common.state.SaqzUiState
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme

// Pure transition contract fed to AnimatedContent: route/state duration and the spatial
// translation. Reduced motion carries 0dp translation, so the state change fades only.
@Immutable
internal data class SaqzStateTransition(val durationMillis: Int, val translation: Dp)

internal fun saqzStateTransition(motion: SaqzMotionPolicy): SaqzStateTransition =
    SaqzStateTransition(motion.routeDurationMillis, motion.maxTranslation)

// Renders exactly one of the four SaqzUiState branches through caller-owned slots. No
// network, coroutine, retry policy or extra state lives here; retry is delegated to onRetry.
@Composable
fun <T> SaqzStateHost(
    state: SaqzUiState<T>,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { SaqzLoadingState() },
    content: @Composable (T) -> Unit,
    empty: @Composable () -> Unit = { SaqzEmptyState() },
    error: @Composable (onRetry: () -> Unit) -> Unit = { retry -> SaqzErrorState(onRetry = retry) },
    onRetry: () -> Unit = {},
) {
    val transition = saqzStateTransition(SaqzTheme.motion)
    val translatePx = with(LocalDensity.current) { transition.translation.roundToPx() }

    AnimatedContent(
        targetState = state,
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        transitionSpec = {
            val dur = transition.durationMillis
            if (translatePx > 0) {
                (fadeIn(tween(dur)) + slideInVertically(tween(dur)) { translatePx }) togetherWith
                    fadeOut(tween(dur))
            } else {
                fadeIn(tween(dur)) togetherWith fadeOut(tween(dur))
            }
        },
        label = "saqzState",
    ) { target ->
        // Exhaustive over the sealed contract; each slot runs only in its branch.
        when (target) {
            is SaqzUiState.Loading -> loading()
            is SaqzUiState.Content -> content(target.value)
            is SaqzUiState.Empty -> empty()
            is SaqzUiState.Error -> error(onRetry)
        }
    }
}
