package br.com.saqz.groups.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.navigation.GroupsNavigationIntent
import br.com.saqz.groups.presentation.navigation.GroupsNavigationTags
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.groups_back_home
import org.jetbrains.compose.resources.stringResource

@Composable
fun RoutePage(
    title: String,
    body: String,
    tag: String,
    onNavigationIntent: (GroupsNavigationIntent) -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SaqzTheme.metrics.horizontalPadding)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        Text(title, style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
        Text(body, color = SaqzTheme.colors.textSecondary)
        SaqzButton(
            label = stringResource(Res.string.groups_back_home),
            onClick = { onNavigationIntent(GroupsNavigationIntent.OpenHome) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("groups-back"),
            variant = SaqzButtonVariant.Secondary,
        )
    }
}

@Preview
@Composable
private fun RoutePagePreview() = SaqzTheme {
    RoutePage(
        title = "Jogos",
        body = "Consulte jogos e crie novas partidas.",
        tag = GroupsNavigationTags.Games,
        onNavigationIntent = {},
    )
}