package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import br.com.saqz.access.presentation.GroupAdministrationState
import br.com.saqz.access.presentation.GroupSelectionState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.action_back
import br.com.saqz.access.resources.action_retry
import br.com.saqz.access.resources.bootstrap_error
import br.com.saqz.access.resources.group_create_submit
import br.com.saqz.access.resources.group_create_title
import br.com.saqz.access.resources.group_name
import br.com.saqz.access.resources.group_name_invalid
import br.com.saqz.access.resources.group_timezone
import br.com.saqz.access.resources.group_timezone_invalid
import br.com.saqz.access.resources.groups_create
import br.com.saqz.access.resources.groups_empty
import br.com.saqz.access.resources.groups_load_error
import br.com.saqz.access.resources.groups_select
import br.com.saqz.designsystem.component.SaqzBadge
import br.com.saqz.designsystem.component.SaqzBadgeVariant
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.component.SaqzListItem
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal object GroupOnboardingTags {
    const val BootstrapLoading = "bootstrap-loading"
    const val GroupLoading = "group-loading"
    const val Create = "group-create"
    const val CreateSubmit = "group-create-submit"
}

@Composable
fun BootstrapAccessScreen(state: SessionAccessState, onRetry: () -> Unit) {
    when (state) {
        SessionAccessState.Bootstrapping -> SaqzLoadingState(Modifier.testTag(GroupOnboardingTags.BootstrapLoading))
        SessionAccessState.BootstrapError -> CenteredActions {
            Text(stringResource(Res.string.bootstrap_error), color = SaqzTheme.colors.textPrimary)
            SaqzButton(stringResource(Res.string.action_retry), onRetry)
        }
        else -> Unit
    }
}

@Composable
fun GroupOnboardingScreen(
    state: GroupSelectionState,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        GroupSelectionState.NoGroup -> CenteredActions {
            Text(stringResource(Res.string.groups_empty), color = SaqzTheme.colors.textPrimary)
            CreateGroupButton(onCreate)
        }
        is GroupSelectionState.Selector -> ScrollColumn {
            Text(stringResource(Res.string.groups_select), style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
            state.memberships.forEach { membership ->
                SaqzListItem(
                    headline = membership.groupName,
                    trailingContent = { SaqzBadge(membership.role, SaqzBadgeVariant.Neutral) },
                    onClick = { onSelect(membership.groupId) },
                )
            }
            CreateGroupButton(onCreate)
        }
        is GroupSelectionState.Loading -> SaqzLoadingState(Modifier.testTag(GroupOnboardingTags.GroupLoading))
        is GroupSelectionState.LoadError -> CenteredActions {
            Text(stringResource(Res.string.groups_load_error), color = SaqzTheme.colors.textPrimary)
            SaqzButton(stringResource(Res.string.action_retry), onRetry)
        }
        is GroupSelectionState.Selected -> CenteredActions {
            Text(state.group.group.name, style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
        }
    }
}

@Composable
fun CreateGroupScreen(
    state: GroupAdministrationState,
    name: String,
    timeZone: String,
    onNameChange: (String) -> Unit,
    onTimeZoneChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    var attempted by remember { mutableStateOf(false) }
    val validName = name.trim().length in 2..80 && name.none(Char::isISOControl)
    val validTimeZone = timeZone.contains('/') && timeZone.none { it.isWhitespace() || it.isISOControl() }
    ScrollColumn {
        Text(stringResource(Res.string.group_create_title), style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
        SaqzInput(
            TextFieldValue(name), { onNameChange(it.text) }, stringResource(Res.string.group_name),
            errorText = if ((attempted && !validName) || state.fieldErrors.containsKey("name")) {
                stringResource(Res.string.group_name_invalid)
            } else null,
            enabled = !state.isLoading,
        )
        SaqzInput(
            TextFieldValue(timeZone), { onTimeZoneChange(it.text) }, stringResource(Res.string.group_timezone),
            errorText = if ((attempted && !validTimeZone) || state.fieldErrors.containsKey("timeZone")) {
                stringResource(Res.string.group_timezone_invalid)
            } else null,
            enabled = !state.isLoading,
        )
        SaqzButton(
            stringResource(Res.string.group_create_submit),
            onClick = { attempted = true; if (validName && validTimeZone) onSubmit() },
            loading = state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag(GroupOnboardingTags.CreateSubmit),
        )
        SaqzButton(
            stringResource(Res.string.action_back), onBack, variant = SaqzButtonVariant.Ghost,
            enabled = !state.isLoading, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CreateGroupButton(onCreate: () -> Unit) = SaqzButton(
    stringResource(Res.string.groups_create), onCreate,
    modifier = Modifier.fillMaxWidth().testTag(GroupOnboardingTags.Create),
)

@Composable
private fun CenteredActions(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun ScrollColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.sectionVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) { content() }
}
