package br.com.saqz.composeapp.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.home_explore_components
import br.com.saqz.composeapp.resources.home_title
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.resources.Res as DesignRes
import br.com.saqz.designsystem.resources.saqz_wordmark
import br.com.saqz.designsystem.theme.SaqzTheme

internal const val SaqzHomeWordmarkTag = "saqz-home-wordmark"
internal const val SaqzHomeHeadingTag = "saqz-home-heading"

// The single output of the Home screen: no avatar, profile, role or business data.
// The wordmark is decorative (the heading carries the "Saqz" accessible name), so a
// reader announces "Saqz" once, then the explore action.
@Composable
fun SaqzHomeScreen(
    onExploreComponents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = metrics.horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.sectionVerticalPadding, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(DesignRes.drawable.saqz_wordmark),
            contentDescription = null,
            modifier = Modifier.testTag(SaqzHomeWordmarkTag),
        )
        Text(
            text = stringResource(Res.string.home_title),
            style = SaqzTheme.typography.heroDisplay,
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.testTag(SaqzHomeHeadingTag),
        )
        SaqzButton(
            label = stringResource(Res.string.home_explore_components),
            onClick = onExploreComponents,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun SaqzHomeScreenPreview() = SaqzTheme { SaqzHomeScreen(onExploreComponents = {}) }
