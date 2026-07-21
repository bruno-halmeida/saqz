package br.com.saqz.groups.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.groups.data.GroupDto
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.VersionedGroupDto
import br.com.saqz.groups.presentation.GroupActions
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.action_back
import br.com.saqz.groups.resources.action_cancel
import br.com.saqz.groups.resources.group_name
import br.com.saqz.groups.resources.group_timezone
import br.com.saqz.groups.resources.logout_confirm
import br.com.saqz.groups.resources.logout_title
import br.com.saqz.groups.resources.settings_conflict
import br.com.saqz.groups.resources.settings_reload
import br.com.saqz.groups.resources.settings_save
import br.com.saqz.groups.resources.settings_title
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzDialog
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal object GroupSettingsTags {
    const val Save = "settings-save"
    const val Reload = "settings-reload"
    const val LogoutConfirm = "logout-confirm"
}

@Immutable
data class GroupSettingsUiState(
    val administration: GroupAdministrationState,
    val name: String,
    val timeZone: String,
)

sealed interface GroupSettingsIntent {
    data class UpdateName(val value: String) : GroupSettingsIntent
    data class UpdateTimeZone(val value: String) : GroupSettingsIntent
    data object Save : GroupSettingsIntent
    data object Reload : GroupSettingsIntent
    data object Back : GroupSettingsIntent
}

sealed interface LogoutConfirmationIntent {
    data object Confirm : LogoutConfirmationIntent
    data object Cancel : LogoutConfirmationIntent
}

@Composable
fun GroupSettingsScreen(
    state: GroupSettingsUiState,
    onIntent: (GroupSettingsIntent) -> Unit,
) = ScrollColumn {
    Text(stringResource(Res.string.settings_title), style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
    SaqzInput(
        TextFieldValue(state.name),
        { onIntent(GroupSettingsIntent.UpdateName(it.text)) },
        stringResource(Res.string.group_name),
        enabled = !state.administration.isLoading,
    )
    SaqzInput(
        TextFieldValue(state.timeZone),
        { onIntent(GroupSettingsIntent.UpdateTimeZone(it.text)) },
        stringResource(Res.string.group_timezone),
        enabled = !state.administration.isLoading,
    )
    if (state.administration.versionConflict) {
        Text(stringResource(Res.string.settings_conflict), color = SaqzTheme.colors.errorForeground)
        SaqzButton(
            stringResource(Res.string.settings_reload),
            { onIntent(GroupSettingsIntent.Reload) },
            Modifier.fillMaxWidth().testTag(GroupSettingsTags.Reload),
        )
    }
    SaqzButton(
        stringResource(Res.string.settings_save),
        { onIntent(GroupSettingsIntent.Save) },
        loading = state.administration.isLoading,
        modifier = Modifier.fillMaxWidth().testTag(GroupSettingsTags.Save),
    )
    SaqzButton(
        stringResource(Res.string.action_back),
        { onIntent(GroupSettingsIntent.Back) },
        variant = SaqzButtonVariant.Ghost,
        enabled = !state.administration.isLoading,
    )
}

@Composable
fun LogoutConfirmationDialog(onIntent: (LogoutConfirmationIntent) -> Unit) {
    SaqzDialog(
        title = stringResource(Res.string.logout_title),
        onCloseRequest = { onIntent(LogoutConfirmationIntent.Cancel) },
        primaryAction = {
            SaqzButton(
                stringResource(Res.string.logout_confirm),
                { onIntent(LogoutConfirmationIntent.Confirm) },
                Modifier.testTag(GroupSettingsTags.LogoutConfirm),
            )
        },
    ) {
        SaqzButton(
            stringResource(Res.string.action_cancel),
            { onIntent(LogoutConfirmationIntent.Cancel) },
            variant = SaqzButtonVariant.Ghost,
        )
    }
}

private val previewGroupState = GroupAdministrationState(
    group = VersionedGroupDto(GroupDto("preview-group", "Futebol de terça", "America/Sao_Paulo", 1, GroupRoleDto.OWNER), "preview-etag"),
    actions = GroupActions(canEditSettings = true, canManageRoles = true, canManageInvite = true),
)

@Preview
@Composable
private fun GroupSettingsScreenPreview() = SaqzTheme {
    GroupSettingsScreen(GroupSettingsUiState(previewGroupState, "Futebol de terça", "America/Sao_Paulo")) {}
}

@Preview
@Composable
private fun LogoutConfirmationDialogPreview() = SaqzTheme { LogoutConfirmationDialog {} }
