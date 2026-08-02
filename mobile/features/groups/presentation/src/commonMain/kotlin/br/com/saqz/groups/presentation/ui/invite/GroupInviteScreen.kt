package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzSwitch
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.invite.GroupInviteError
import br.com.saqz.groups.presentation.invite.GroupInviteIntent
import br.com.saqz.groups.presentation.invite.GroupInviteState
import br.com.saqz.groups.presentation.invite.JoinedAtUnit
import br.com.saqz.groups.presentation.invite.InviteStatus
import br.com.saqz.groups.presentation.invite.PendingEntryRequestUi
import br.com.saqz.groups.presentation.invite.RecentMemberUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_invite_active_expires
import br.com.saqz.groups.resources.group_invite_approve
import br.com.saqz.groups.resources.group_invite_approval
import br.com.saqz.groups.resources.group_invite_copy_link
import br.com.saqz.groups.resources.group_invite_copied_toast
import br.com.saqz.groups.resources.group_invite_deactivate
import br.com.saqz.groups.resources.group_invite_empty_description
import br.com.saqz.groups.resources.group_invite_empty_title
import br.com.saqz.groups.resources.group_invite_generate_code
import br.com.saqz.groups.resources.group_invite_generate_link
import br.com.saqz.groups.resources.group_invite_joined_days
import br.com.saqz.groups.resources.group_invite_joined_hours
import br.com.saqz.groups.resources.group_invite_joined_minutes
import br.com.saqz.groups.resources.group_invite_joined_recently
import br.com.saqz.groups.resources.group_invite_load_error
import br.com.saqz.groups.resources.group_invite_operation_error
import br.com.saqz.groups.resources.group_invite_people
import br.com.saqz.groups.resources.group_invite_pending
import br.com.saqz.groups.resources.group_invite_reject
import br.com.saqz.groups.resources.group_invite_retry
import br.com.saqz.groups.resources.group_invite_rotate_warning
import br.com.saqz.groups.resources.group_invite_share
import br.com.saqz.groups.resources.group_invite_share_image
import br.com.saqz.groups.resources.group_invite_show_qr
import br.com.saqz.groups.resources.group_invite_title
import br.com.saqz.groups.resources.group_invite_whatsapp
import br.com.saqz.groups.resources.group_invite_whatsapp_primary
import org.jetbrains.compose.resources.stringResource

internal object GroupInviteTags {
    const val Screen = "group-invite"
    const val Generate = "group-invite-generate"
    const val Copy = "group-invite-copy"
    const val Share = "group-invite-share"
    const val Approval = "group-invite-approval"
    const val Qr = "group-invite-qr"
}

@Composable
internal fun GroupInviteScreen(
    state: GroupInviteState,
    onBack: () -> Unit,
    onIntent: (GroupInviteIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().testTag(GroupInviteTags.Screen)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SaqzTopAppBar(title = stringResource(Res.string.group_invite_title), onBack = onBack)
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SaqzSpinner() }
            } else if (state.loadFailed) {
                SaqzEmptyState(
                    title = stringResource(Res.string.group_invite_load_error),
                    action = stringResource(Res.string.group_invite_retry),
                    onAction = { onIntent(GroupInviteIntent.Retry) },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(SaqzTheme.metrics.horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
                ) {
                    Text(state.groupName, style = SaqzTheme.typography.title, color = SaqzTheme.colors.textPrimary)
                    if (state.inviteStatus == InviteStatus.Active) {
                        InviteActiveCard(state = state, onIntent = onIntent)
                    } else {
                        SaqzEmptyState(
                            title = stringResource(Res.string.group_invite_empty_title),
                            description = stringResource(Res.string.group_invite_empty_description),
                            action = stringResource(Res.string.group_invite_generate_link),
                            onAction = { onIntent(GroupInviteIntent.GenerateInvite) },
                        )
                    }
                    ApprovalRow(state = state, onIntent = onIntent)
                    EntryRequests(
                        requests = state.pendingRequests,
                        pendingActions = state.pendingActionIds,
                        onIntent = onIntent,
                    )
                    RecentMembers(members = state.recentMembers)
                    state.error?.let { error ->
                        Text(
                            text = stringResource(
                                if (error == GroupInviteError.Load) Res.string.group_invite_load_error
                                else Res.string.group_invite_operation_error,
                            ),
                            color = SaqzTheme.colors.errorForeground,
                            style = SaqzTheme.typography.support,
                        )
                    }
                }
            }
        }
        SaqzBottomSheet(
            open = state.isShareSheetVisible,
            onClose = { onIntent(GroupInviteIntent.CloseShareSheet) },
            title = stringResource(Res.string.group_invite_share),
        ) {
            SaqzButton(
                label = stringResource(Res.string.group_invite_whatsapp),
                onClick = { onIntent(GroupInviteIntent.OpenMessagePreview) },
                fullWidth = true,
            )
            SaqzButton(
                label = stringResource(Res.string.group_invite_copy_link),
                onClick = { onIntent(GroupInviteIntent.CopyLink) },
                variant = SaqzButtonVariant.Secondary,
                fullWidth = true,
                modifier = Modifier.padding(top = SaqzTheme.metrics.grid),
            )
            SaqzButton(
                label = stringResource(Res.string.group_invite_show_qr),
                onClick = { onIntent(GroupInviteIntent.OpenQr) },
                variant = SaqzButtonVariant.Secondary,
                fullWidth = true,
                modifier = Modifier.padding(top = SaqzTheme.metrics.grid).testTag(GroupInviteTags.Qr),
            )
            SaqzButton(
                label = stringResource(Res.string.group_invite_share_image),
                onClick = { onIntent(GroupInviteIntent.ShareImage) },
                variant = SaqzButtonVariant.Ghost,
                size = SaqzButtonSize.Sm,
                fullWidth = true,
                modifier = Modifier.padding(top = SaqzTheme.metrics.grid),
            )
        }
        SaqzToast(
            visible = state.toast != null,
            onDismiss = { onIntent(GroupInviteIntent.ClearToast) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(SaqzTheme.metrics.horizontalPadding),
        ) {
            SaqzToastText(stringResource(Res.string.group_invite_copied_toast))
        }
    }
}

@Composable
private fun InviteActiveCard(state: GroupInviteState, onIntent: (GroupInviteIntent) -> Unit) {
    SaqzCard {
        state.expiresLabel?.let {
            Text(
                stringResource(Res.string.group_invite_active_expires, it),
                style = SaqzTheme.typography.subtitle,
            )
        }
        state.inviteUrl?.let {
            Text(
                it,
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = SaqzTheme.metrics.grid),
            )
        }
            Text(
                stringResource(Res.string.group_invite_rotate_warning),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
        Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
            SaqzButton(
                label = stringResource(
                    if (state.inviteUrl == null) Res.string.group_invite_generate_link
                    else Res.string.group_invite_generate_code,
                ),
                onClick = { onIntent(GroupInviteIntent.GenerateInvite) },
                loading = state.isGenerating,
                modifier = Modifier.weight(1f).testTag(GroupInviteTags.Generate),
            )
            if (state.inviteUrl != null) {
                SaqzButton(
                    label = stringResource(Res.string.group_invite_copy_link),
                    onClick = { onIntent(GroupInviteIntent.CopyLink) },
                    variant = SaqzButtonVariant.Secondary,
                    modifier = Modifier.weight(1f).testTag(GroupInviteTags.Copy),
                )
            }
        }
        state.inviteUrl?.let {
            SaqzButton(
                label = stringResource(Res.string.group_invite_whatsapp_primary),
                onClick = { onIntent(GroupInviteIntent.OpenMessagePreview) },
                fullWidth = true,
                modifier = Modifier.testTag(GroupInviteTags.Share),
            )
            SaqzButton(
                label = stringResource(Res.string.group_invite_share),
                onClick = { onIntent(GroupInviteIntent.OpenShareSheet) },
                variant = SaqzButtonVariant.Secondary,
                fullWidth = true,
                modifier = Modifier.padding(top = SaqzTheme.metrics.grid),
            )
        }
        SaqzButton(
            label = stringResource(Res.string.group_invite_deactivate),
            onClick = { onIntent(GroupInviteIntent.DeactivateInvite) },
            variant = SaqzButtonVariant.Ghost,
            size = SaqzButtonSize.Sm,
            enabled = !state.isDeactivating,
            fullWidth = true,
        )
    }
}

@Composable
private fun ApprovalRow(state: GroupInviteState, onIntent: (GroupInviteIntent) -> Unit) {
    SaqzCard {
        SaqzSwitch(
            checked = state.entryRequiresApproval,
            onCheckedChange = { onIntent(GroupInviteIntent.ToggleApproval(it)) },
            label = stringResource(Res.string.group_invite_approval),
            enabled = !state.isUpdatingApproval,
            modifier = Modifier.testTag(GroupInviteTags.Approval),
        )
    }
}

@Composable
private fun EntryRequests(
    requests: List<PendingEntryRequestUi>,
    pendingActions: Set<String>,
    onIntent: (GroupInviteIntent) -> Unit,
) {
    if (requests.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
        SaqzSectionHeader(title = stringResource(Res.string.group_invite_pending))
        requests.forEach { request ->
            SaqzCard {
                Text(request.displayName, style = SaqzTheme.typography.subtitle)
                Text(
                    request.requestedAtLabel,
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
                    modifier = Modifier.padding(top = SaqzTheme.metrics.grid),
                ) {
                    SaqzButton(
                        label = stringResource(Res.string.group_invite_approve),
                        onClick = { onIntent(GroupInviteIntent.ApproveRequest(request.userId)) },
                        size = SaqzButtonSize.Sm,
                        enabled = request.userId !in pendingActions,
                    )
                    SaqzButton(
                        label = stringResource(Res.string.group_invite_reject),
                        onClick = { onIntent(GroupInviteIntent.RejectRequest(request.userId)) },
                        size = SaqzButtonSize.Sm,
                        variant = SaqzButtonVariant.Ghost,
                        enabled = request.userId !in pendingActions,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentMembers(members: List<RecentMemberUi>) {
    if (members.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
        SaqzSectionHeader(title = stringResource(Res.string.group_invite_people))
        members.forEach { member ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = SaqzTheme.metrics.grid),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(member.displayName, style = SaqzTheme.typography.body)
                Text(
                    member.joinedAtLabel(),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun RecentMemberUi.joinedAtLabel(): String = when (joinedAtUnit) {
    JoinedAtUnit.Minutes -> stringResource(Res.string.group_invite_joined_minutes, joinedAtCount ?: 0)
    JoinedAtUnit.Hours -> stringResource(Res.string.group_invite_joined_hours, joinedAtCount ?: 0)
    JoinedAtUnit.Days -> stringResource(Res.string.group_invite_joined_days, joinedAtCount ?: 0)
    JoinedAtUnit.Recently -> stringResource(Res.string.group_invite_joined_recently)
}

// SPEC_DEVIATION VUL-141: a digitação de código e o bloco “CERET-8K2P” foram cortados.
// Esta tela mantém o foco em link, compartilhar e QR; não há “Copiar código”.
