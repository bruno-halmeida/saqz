package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.access.data.GroupRoleDto
import br.com.saqz.access.data.MembershipDto
import br.com.saqz.access.data.PersistedRoleDto
import br.com.saqz.access.presentation.GroupActions
import br.com.saqz.access.presentation.GroupAdministrationState
import br.com.saqz.access.presentation.InviteUiError
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.action_back
import br.com.saqz.access.resources.action_retry
import br.com.saqz.access.resources.invite_expire
import br.com.saqz.access.resources.invite_expire_body
import br.com.saqz.access.resources.invite_expire_confirm
import br.com.saqz.access.resources.invite_expire_title
import br.com.saqz.access.resources.invite_generate
import br.com.saqz.access.resources.invite_invalid
import br.com.saqz.access.resources.invite_rate_limit
import br.com.saqz.access.resources.invite_ready
import br.com.saqz.access.resources.invite_rotate
import br.com.saqz.access.resources.invite_share
import br.com.saqz.access.resources.invite_title
import br.com.saqz.access.resources.invite_unavailable
import br.com.saqz.access.resources.membership_make_admin
import br.com.saqz.access.resources.membership_make_athlete
import br.com.saqz.access.resources.memberships_title
import br.com.saqz.access.resources.action_cancel
import br.com.saqz.designsystem.component.SaqzBadge
import br.com.saqz.designsystem.component.SaqzBadgeVariant
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzDialog
import br.com.saqz.designsystem.component.SaqzListItem
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal object MembershipInviteTags {
    const val Generate = "invite-generate"
    const val Rotate = "invite-rotate"
    const val Share = "invite-share"
    const val Expire = "invite-expire"
    const val Retry = "invite-retry"
    const val ExpireConfirm = "invite-expire-confirm"
    const val ExpireCancel = "invite-expire-cancel"

    fun role(userId: String) = "membership-role-$userId"
}

@Immutable
data class InviteToolState(
    val inviteUrl: String? = null,
    val isLoading: Boolean = false,
    val error: InviteUiError? = null,
    val retryAfterSeconds: Int? = null,
)

@Composable
fun MembershipAdministrationScreen(
    state: GroupAdministrationState,
    onRoleChange: (String, PersistedRoleDto) -> Unit,
    onBack: () -> Unit,
) {
    if (!state.actions.canManageRoles) return
    ScrollColumn {
        Text(
            stringResource(Res.string.memberships_title),
            style = SaqzTheme.typography.lead,
            color = SaqzTheme.colors.textPrimary,
        )
        state.memberships.forEach { member ->
            SaqzListItem(
                headline = member.displayName,
                supportingContent = { SaqzBadge(member.role.name, SaqzBadgeVariant.Neutral) },
                trailingContent = when (member.role) {
                    GroupRoleDto.OWNER -> null
                    GroupRoleDto.ADMIN -> ({
                        RoleButton(
                            stringResource(Res.string.membership_make_athlete),
                            member.userId,
                            state.isLoading,
                        ) { onRoleChange(member.userId, PersistedRoleDto.ATHLETE) }
                    })
                    GroupRoleDto.ATHLETE -> ({
                        RoleButton(
                            stringResource(Res.string.membership_make_admin),
                            member.userId,
                            state.isLoading,
                        ) { onRoleChange(member.userId, PersistedRoleDto.ADMIN) }
                    })
                },
            )
        }
        SaqzButton(
            stringResource(Res.string.action_back),
            onBack,
            variant = SaqzButtonVariant.Ghost,
            enabled = !state.isLoading,
        )
    }
}

@Composable
fun InviteManagementScreen(
    actions: GroupActions,
    state: InviteToolState,
    onGenerate: () -> Unit,
    onShare: (String) -> Unit,
    onExpireRequest: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    if (!actions.canManageInvite) return
    ScrollColumn {
        Text(
            stringResource(Res.string.invite_title),
            style = SaqzTheme.typography.lead,
            color = SaqzTheme.colors.textPrimary,
        )
        InviteFeedback(state, onRetry)
        val inviteUrl = state.inviteUrl
        if (inviteUrl == null) {
            SaqzButton(
                stringResource(Res.string.invite_generate),
                onGenerate,
                Modifier.fillMaxWidth().testTag(MembershipInviteTags.Generate),
                loading = state.isLoading,
            )
        } else {
            Text(stringResource(Res.string.invite_ready), color = SaqzTheme.colors.textSecondary)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
            ) {
                ShareInviteButton(inviteUrl, state.isLoading, onShare)
                SaqzButton(
                    stringResource(Res.string.invite_rotate),
                    onGenerate,
                    Modifier.testTag(MembershipInviteTags.Rotate),
                    variant = SaqzButtonVariant.Secondary,
                    enabled = !state.isLoading,
                )
            }
            SaqzButton(
                stringResource(Res.string.invite_expire),
                onExpireRequest,
                Modifier.fillMaxWidth().testTag(MembershipInviteTags.Expire),
                variant = SaqzButtonVariant.Destructive,
                enabled = !state.isLoading,
            )
        }
        SaqzButton(
            stringResource(Res.string.action_back),
            onBack,
            variant = SaqzButtonVariant.Ghost,
            enabled = !state.isLoading,
        )
    }
}

@Composable
fun ExpireInviteConfirmationDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    SaqzDialog(
        title = stringResource(Res.string.invite_expire_title),
        onCloseRequest = onCancel,
        primaryAction = {
            SaqzButton(
                stringResource(Res.string.invite_expire_confirm),
                onConfirm,
                Modifier.testTag(MembershipInviteTags.ExpireConfirm),
                variant = SaqzButtonVariant.Destructive,
            )
        },
    ) {
        Text(stringResource(Res.string.invite_expire_body), color = SaqzTheme.colors.textSecondary)
        SaqzButton(
            stringResource(Res.string.action_cancel),
            onCancel,
            Modifier.testTag(MembershipInviteTags.ExpireCancel),
            variant = SaqzButtonVariant.Ghost,
        )
    }
}

@Composable
private fun RoleButton(label: String, userId: String, loading: Boolean, onClick: () -> Unit) {
    SaqzButton(
        label,
        onClick,
        Modifier.testTag(MembershipInviteTags.role(userId)),
        variant = SaqzButtonVariant.Secondary,
        enabled = !loading,
    )
}

@Composable
private fun ShareInviteButton(inviteUrl: String, loading: Boolean, onShare: (String) -> Unit) {
    val description = stringResource(Res.string.invite_share)
    IconButton(
        onClick = { onShare(inviteUrl) },
        enabled = !loading,
        modifier = Modifier
            .testTag(MembershipInviteTags.Share)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = "↗",
            style = SaqzTheme.typography.lead,
            color = SaqzTheme.colors.primary,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun InviteFeedback(state: InviteToolState, onRetry: () -> Unit) {
    val error = state.error ?: return
    val message = when (error) {
        InviteUiError.INVALID_OR_EXPIRED -> stringResource(Res.string.invite_invalid)
        InviteUiError.ATTEMPT_LIMIT -> stringResource(
            Res.string.invite_rate_limit,
            state.retryAfterSeconds ?: 0,
        )
        InviteUiError.UNAVAILABLE -> stringResource(Res.string.invite_unavailable)
    }
    Text(message, color = SaqzTheme.colors.errorForeground)
    SaqzButton(
        stringResource(Res.string.action_retry),
        onClick = onRetry,
        modifier = Modifier.testTag(MembershipInviteTags.Retry),
        variant = SaqzButtonVariant.Secondary,
        enabled = error != InviteUiError.ATTEMPT_LIMIT && !state.isLoading,
    )
}

private val previewInviteActions = GroupActions(true, true, true)

@Preview
@Composable
private fun MembershipAdministrationScreenPreview() = SaqzTheme {
    MembershipAdministrationScreen(
        GroupAdministrationState(actions = previewInviteActions, memberships = listOf(MembershipDto("preview-athlete", "Bruno", GroupRoleDto.ATHLETE))),
        { _, _ -> },
        {},
    )
}

@Preview
@Composable
private fun InviteManagementScreenPreview() = SaqzTheme {
    InviteManagementScreen(previewInviteActions, InviteToolState(inviteUrl = "https://saqz.app/i/preview"), {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun ExpireInviteConfirmationDialogPreview() = SaqzTheme { ExpireInviteConfirmationDialog({}, {}) }
