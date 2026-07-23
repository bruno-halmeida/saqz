package br.com.saqz.groups.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.groups.presentation.GroupSelectionMembership
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.resources.*
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

sealed interface GroupOnboardingIntent {
    data class Select(val groupId: String) : GroupOnboardingIntent

    data object OpenCreateGroup : GroupOnboardingIntent

    data object Retry : GroupOnboardingIntent
}

sealed interface CreateGroupIntent {
    data class UpdateName(val value: String) : CreateGroupIntent

    data class UpdateTimeZone(val value: String) : CreateGroupIntent

    data object Submit : CreateGroupIntent

    data object Back : CreateGroupIntent
}

data class CreateGroupUiState(
    val administration: GroupAdministrationState,
    val name: String,
    val timeZone: String,
    val validationAttempted: Boolean = false,
) {
    val validName: Boolean
        get() = name.trim().length in 2..80 && name.none(Char::isISOControl)

    val validTimeZone: Boolean
        get() = timeZone.contains('/') && timeZone.none { it.isWhitespace() || it.isISOControl() }

    val isValid: Boolean
        get() = validName && validTimeZone
}

@Composable
fun GroupOnboardingScreen(
    state: GroupSelectionState,
    onIntent: (GroupOnboardingIntent) -> Unit,
) {
    when (state) {
        GroupSelectionState.NoGroup -> CenteredActions {
            Text(stringResource(Res.string.groups_empty), color = SaqzTheme.colors.textPrimary)
            CreateGroupButton { onIntent(GroupOnboardingIntent.OpenCreateGroup) }
        }
        is GroupSelectionState.Selector -> ScrollColumn {
            Text(stringResource(Res.string.groups_select), style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
            state.memberships.forEach { membership ->
                SaqzListItem(
                    headline = membership.groupName,
                    trailingContent = { SaqzBadge(membership.role.name, SaqzBadgeVariant.Neutral) },
                    onClick = { onIntent(GroupOnboardingIntent.Select(membership.groupId)) },
                )
            }
            CreateGroupButton { onIntent(GroupOnboardingIntent.OpenCreateGroup) }
        }
        is GroupSelectionState.Loading -> SaqzLoadingState(Modifier.testTag(GroupOnboardingTags.GroupLoading))
        is GroupSelectionState.LoadError -> CenteredActions {
            Text(stringResource(Res.string.groups_load_error), color = SaqzTheme.colors.textPrimary)
            SaqzButton(stringResource(Res.string.action_retry), { onIntent(GroupOnboardingIntent.Retry) })
        }
        is GroupSelectionState.Selected -> CenteredActions {
            Text(state.group.group.name, style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
        }
    }
}

@Composable
fun CreateGroupScreen(
    state: CreateGroupUiState,
    onIntent: (CreateGroupIntent) -> Unit,
) {
    ScrollColumn {
        Text(stringResource(Res.string.group_create_title), style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
        SaqzInput(
            TextFieldValue(state.name), { onIntent(CreateGroupIntent.UpdateName(it.text)) }, stringResource(Res.string.group_name),
            errorText = if ((state.validationAttempted && !state.validName) || state.administration.fieldErrors.containsKey("name")) {
                stringResource(Res.string.group_name_invalid)
            } else null,
            enabled = !state.administration.isLoading,
        )
        SaqzInput(
            TextFieldValue(state.timeZone), { onIntent(CreateGroupIntent.UpdateTimeZone(it.text)) }, stringResource(Res.string.group_timezone),
            errorText = if ((state.validationAttempted && !state.validTimeZone) || state.administration.fieldErrors.containsKey("timeZone")) {
                stringResource(Res.string.group_timezone_invalid)
            } else null,
            enabled = !state.administration.isLoading,
        )
        SaqzButton(
            stringResource(Res.string.group_create_submit),
            onClick = { onIntent(CreateGroupIntent.Submit) },
            loading = state.administration.isLoading,
            modifier = Modifier.fillMaxWidth().testTag(GroupOnboardingTags.CreateSubmit),
        )
        SaqzButton(
            stringResource(Res.string.action_back), { onIntent(CreateGroupIntent.Back) }, variant = SaqzButtonVariant.Ghost,
            enabled = !state.administration.isLoading, modifier = Modifier.fillMaxWidth(),
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
internal fun ScrollColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.sectionVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) { content() }
}

@Preview
@Composable
private fun GroupOnboardingScreenPreview() = SaqzTheme {
    GroupOnboardingScreen(
        GroupSelectionState.Selector(
            listOf(GroupSelectionMembership("preview-group", "Futebol de terça", GroupRole.OWNER)),
        ),
        {},
    )
}

@Preview
@Composable
private fun CreateGroupScreenPreview() = SaqzTheme {
    CreateGroupScreen(CreateGroupUiState(GroupAdministrationState(), "Futebol de terça", "America/Sao_Paulo"), {})
}
