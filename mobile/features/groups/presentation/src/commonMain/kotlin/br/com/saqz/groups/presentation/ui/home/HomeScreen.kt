package br.com.saqz.groups.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeAction
import br.com.saqz.groups.presentation.home.HomeState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_error_message
import br.com.saqz.groups.resources.home_error_title
import br.com.saqz.groups.resources.home_greeting
import br.com.saqz.groups.resources.home_placeholder
import br.com.saqz.groups.resources.home_retry
import org.jetbrains.compose.resources.stringResource

internal object HomeTags {
    const val Content = "home-content"
    const val Error = "home-error"
    const val Loading = "home-loading"
    const val Retry = "home-retry"
}

@Composable
fun HomeScreen(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> HomeSkeleton(modifier)
        state.loadFailed -> HomeFailure(
            onRetry = { onAction(HomeAction.Retry) },
            modifier = modifier,
        )
        else -> HomeContent(state, modifier)
    }
}

@Composable
private fun HomeSkeleton(modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .padding(metrics.horizontalPadding)
            .testTag(HomeTags.Loading),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzSkeleton(width = metrics.avatarSize, height = metrics.avatarSize, circle = true)
        SaqzSkeleton(width = metrics.buttonHeight * 4, height = metrics.buttonHeight / 2)
        SaqzSkeleton(height = metrics.avatarSize * 2)
        SaqzSkeleton(height = metrics.avatarSize * 2)
    }
}

@Composable
private fun HomeFailure(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .testTag(HomeTags.Error),
        contentAlignment = Alignment.Center,
    ) {
        SaqzEmptyState(
            title = stringResource(Res.string.home_error_title),
            description = stringResource(Res.string.home_error_message),
            action = stringResource(Res.string.home_retry),
            onAction = onRetry,
            modifier = Modifier.testTag(HomeTags.Retry),
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeState,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .padding(metrics.horizontalPadding)
            .testTag(HomeTags.Content),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        state.displayName?.let { displayName ->
            Text(
                text = stringResource(Res.string.home_greeting, displayName),
                style = SaqzTheme.typography.title,
                color = SaqzTheme.colors.textPrimary,
            )
        }
        Text(
            text = stringResource(Res.string.home_placeholder),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Preview(name = "Home carregando", widthDp = 390, heightDp = 844)
@Composable
private fun HomeLoadingPreview() = SaqzTheme {
    HomeScreen(HomeState(), onAction = {})
}

@Preview(name = "Home erro", widthDp = 390, heightDp = 844)
@Composable
private fun HomeFailurePreview() = SaqzTheme {
    HomeScreen(HomeState(isLoading = false, loadFailed = true), onAction = {})
}

@Preview(name = "Home carregada", widthDp = 390, heightDp = 844)
@Composable
private fun HomeContentPreview() = SaqzTheme {
    HomeScreen(HomeState(isLoading = false, displayName = "Bruno"), onAction = {})
}
