package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.access.data.GroupDto
import br.com.saqz.access.data.GroupRoleDto
import br.com.saqz.access.data.VersionedGroupDto
import br.com.saqz.access.presentation.GroupActions
import br.com.saqz.access.presentation.GroupAdministrationState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.action_back
import br.com.saqz.access.resources.action_cancel
import br.com.saqz.access.resources.group_invite
import br.com.saqz.access.resources.group_name
import br.com.saqz.access.resources.group_roles
import br.com.saqz.access.resources.group_settings
import br.com.saqz.access.resources.group_switch
import br.com.saqz.access.resources.group_timezone
import br.com.saqz.access.resources.logout
import br.com.saqz.access.resources.logout_confirm
import br.com.saqz.access.resources.logout_title
import br.com.saqz.access.resources.settings_conflict
import br.com.saqz.access.resources.settings_reload
import br.com.saqz.access.resources.settings_save
import br.com.saqz.access.resources.settings_title
import br.com.saqz.designsystem.component.SaqzBadge
import br.com.saqz.designsystem.component.SaqzBadgeVariant
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzDialog
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal object GroupContextTags {
    const val Switch = "context-switch"; const val Settings = "context-settings"
    const val Roles = "context-roles"; const val Invite = "context-invite"; const val Logout = "context-logout"
    const val Save = "settings-save"; const val Reload = "settings-reload"; const val LogoutConfirm = "logout-confirm"
}

@Composable
fun GroupContextScreen(
    state: GroupAdministrationState,
    onSwitch: () -> Unit, onSettings: () -> Unit, onRoles: () -> Unit,
    onInvite: () -> Unit, onLogout: () -> Unit,
) {
    val group = state.group?.group ?: return
    Column(
        Modifier.fillMaxSize().padding(SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(group.name, style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
            SaqzBadge(group.role.name, SaqzBadgeVariant.Neutral)
        }
        Action(stringResource(Res.string.group_switch), GroupContextTags.Switch, onSwitch)
        if (state.actions.canEditSettings) Action(stringResource(Res.string.group_settings), GroupContextTags.Settings, onSettings)
        if (state.actions.canManageRoles) Action(stringResource(Res.string.group_roles), GroupContextTags.Roles, onRoles)
        if (state.actions.canManageInvite) Action(stringResource(Res.string.group_invite), GroupContextTags.Invite, onInvite)
        Action(stringResource(Res.string.logout), GroupContextTags.Logout, onLogout, SaqzButtonVariant.Ghost)
    }
}

@Composable
fun GroupSettingsScreen(
    state: GroupAdministrationState, name: String, timeZone: String,
    onNameChange: (String) -> Unit, onTimeZoneChange: (String) -> Unit,
    onSave: () -> Unit, onReload: () -> Unit, onBack: () -> Unit,
) = ScrollColumn {
    Text(stringResource(Res.string.settings_title), style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
    SaqzInput(TextFieldValue(name), { onNameChange(it.text) }, stringResource(Res.string.group_name), enabled = !state.isLoading)
    SaqzInput(TextFieldValue(timeZone), { onTimeZoneChange(it.text) }, stringResource(Res.string.group_timezone), enabled = !state.isLoading)
    if (state.versionConflict) {
        Text(stringResource(Res.string.settings_conflict), color = SaqzTheme.colors.errorForeground)
        Action(stringResource(Res.string.settings_reload), GroupContextTags.Reload, onReload)
    }
    SaqzButton(stringResource(Res.string.settings_save), onSave, loading = state.isLoading,
        modifier = Modifier.fillMaxWidth().testTag(GroupContextTags.Save))
    SaqzButton(stringResource(Res.string.action_back), onBack, variant = SaqzButtonVariant.Ghost, enabled = !state.isLoading)
}

@Composable
fun LogoutConfirmationDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    SaqzDialog(
        title = stringResource(Res.string.logout_title), onCloseRequest = onCancel,
        primaryAction = { SaqzButton(stringResource(Res.string.logout_confirm), onConfirm, Modifier.testTag(GroupContextTags.LogoutConfirm)) },
    ) {
        SaqzButton(stringResource(Res.string.action_cancel), onCancel, variant = SaqzButtonVariant.Ghost)
    }
}

@Composable
private fun Action(label: String, tag: String, click: () -> Unit, variant: SaqzButtonVariant = SaqzButtonVariant.Secondary) =
    SaqzButton(label, click, Modifier.fillMaxWidth().testTag(tag), variant)

private val previewGroupState = GroupAdministrationState(
    group = VersionedGroupDto(GroupDto("preview-group", "Futebol de terça", "America/Sao_Paulo", 1, GroupRoleDto.OWNER), "preview-etag"),
    actions = GroupActions(canEditSettings = true, canManageRoles = true, canManageInvite = true),
)

@Preview
@Composable
private fun GroupContextScreenPreview() = SaqzTheme {
    GroupContextScreen(previewGroupState, {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun GroupSettingsScreenPreview() = SaqzTheme {
    GroupSettingsScreen(previewGroupState, "Futebol de terça", "America/Sao_Paulo", {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun LogoutConfirmationDialogPreview() = SaqzTheme { LogoutConfirmationDialog({}, {}) }
