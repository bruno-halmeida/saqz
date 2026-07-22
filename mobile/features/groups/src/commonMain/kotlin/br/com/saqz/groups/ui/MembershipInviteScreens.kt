package br.com.saqz.groups.ui

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
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.AssignableGroupRole
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.presentation.GroupActions
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.InviteUiError
import br.com.saqz.groups.presentation.InviteToolState
import br.com.saqz.groups.resources.*
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

sealed interface MembershipAdministrationIntent {
    data class ChangeRole(val userId: String, val role: AssignableGroupRole) : MembershipAdministrationIntent
    data object Back : MembershipAdministrationIntent
}

@Immutable
data class InviteManagementUiState(
    val actions: GroupActions,
    val invite: InviteToolState,
)

sealed interface InviteManagementIntent {
    data object Generate : InviteManagementIntent
    data class Share(val url: String) : InviteManagementIntent
    data object RequestExpire : InviteManagementIntent
    data object Retry : InviteManagementIntent
    data object Back : InviteManagementIntent
}

sealed interface ExpireInviteConfirmationIntent {
    data object Confirm : ExpireInviteConfirmationIntent
    data object Cancel : ExpireInviteConfirmationIntent
}

@Composable
fun MembershipAdministrationScreen(
    state: GroupAdministrationState,
    onIntent: (MembershipAdministrationIntent) -> Unit,
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
                    GroupRole.OWNER -> null
                    GroupRole.ADMIN -> ({
                        RoleButton(
                            stringResource(Res.string.membership_make_athlete),
                            member.userId,
                            state.isLoading,
                        ) {
                            onIntent(MembershipAdministrationIntent.ChangeRole(member.userId, AssignableGroupRole.ATHLETE))
                        }
                    })
                    GroupRole.ATHLETE -> ({
                        RoleButton(
                            stringResource(Res.string.membership_make_admin),
                            member.userId,
                            state.isLoading,
                        ) {
                            onIntent(MembershipAdministrationIntent.ChangeRole(member.userId, AssignableGroupRole.ADMIN))
                        }
                    })
                },
            )
        }
        SaqzButton(
            stringResource(Res.string.action_back),
            { onIntent(MembershipAdministrationIntent.Back) },
            variant = SaqzButtonVariant.Ghost,
            enabled = !state.isLoading,
        )
    }
}

@Composable
fun InviteManagementScreen(
    state: InviteManagementUiState,
    onIntent: (InviteManagementIntent) -> Unit,
) {
    if (!state.actions.canManageInvite) return
    ScrollColumn {
        Text(
            stringResource(Res.string.invite_title),
            style = SaqzTheme.typography.lead,
            color = SaqzTheme.colors.textPrimary,
        )
        InviteFeedback(state.invite) { onIntent(InviteManagementIntent.Retry) }
        val inviteUrl = state.invite.inviteUrl
        if (inviteUrl == null) {
            SaqzButton(
                stringResource(Res.string.invite_generate),
                { onIntent(InviteManagementIntent.Generate) },
                Modifier.fillMaxWidth().testTag(MembershipInviteTags.Generate),
                loading = state.invite.isLoading,
            )
        } else {
            Text(stringResource(Res.string.invite_ready), color = SaqzTheme.colors.textSecondary)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
            ) {
                ShareInviteButton(inviteUrl, state.invite.isLoading) {
                    onIntent(InviteManagementIntent.Share(it))
                }
                SaqzButton(
                    stringResource(Res.string.invite_rotate),
                    { onIntent(InviteManagementIntent.Generate) },
                    Modifier.testTag(MembershipInviteTags.Rotate),
                    variant = SaqzButtonVariant.Secondary,
                    enabled = !state.invite.isLoading,
                )
            }
            SaqzButton(
                stringResource(Res.string.invite_expire),
                { onIntent(InviteManagementIntent.RequestExpire) },
                Modifier.fillMaxWidth().testTag(MembershipInviteTags.Expire),
                variant = SaqzButtonVariant.Destructive,
                enabled = !state.invite.isLoading,
            )
        }
        SaqzButton(
            stringResource(Res.string.action_back),
            { onIntent(InviteManagementIntent.Back) },
            variant = SaqzButtonVariant.Ghost,
            enabled = !state.invite.isLoading,
        )
    }
}

@Composable
fun ExpireInviteConfirmationDialog(onIntent: (ExpireInviteConfirmationIntent) -> Unit) {
    SaqzDialog(
        title = stringResource(Res.string.invite_expire_title),
        onCloseRequest = { onIntent(ExpireInviteConfirmationIntent.Cancel) },
        primaryAction = {
            SaqzButton(
                stringResource(Res.string.invite_expire_confirm),
                { onIntent(ExpireInviteConfirmationIntent.Confirm) },
                Modifier.testTag(MembershipInviteTags.ExpireConfirm),
                variant = SaqzButtonVariant.Destructive,
            )
        },
    ) {
        Text(stringResource(Res.string.invite_expire_body), color = SaqzTheme.colors.textSecondary)
        SaqzButton(
            stringResource(Res.string.action_cancel),
            { onIntent(ExpireInviteConfirmationIntent.Cancel) },
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
        GroupAdministrationState(
            actions = previewInviteActions,
            memberships = listOf(GroupMembership("preview-athlete", "Bruno", GroupRole.ATHLETE)),
        ),
        {},
    )
}

@Preview
@Composable
private fun InviteManagementScreenPreview() = SaqzTheme {
    InviteManagementScreen(
        InviteManagementUiState(previewInviteActions, InviteToolState(inviteUrl = "https://saqz.app/i/preview")),
    ) {}
}

@Preview
@Composable
private fun ExpireInviteConfirmationDialogPreview() = SaqzTheme { ExpireInviteConfirmationDialog {} }
