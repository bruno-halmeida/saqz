package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
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
import br.com.saqz.groups.presentation.invite.GroupInviteToast
import br.com.saqz.groups.presentation.invite.JoinedAtUnit
import br.com.saqz.groups.presentation.invite.InviteStatus
import br.com.saqz.groups.presentation.invite.PendingEntryRequestUi
import br.com.saqz.groups.presentation.invite.RecentMemberUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_invite_approval_hint
import br.com.saqz.groups.resources.group_invite_never_expires
import br.com.saqz.groups.resources.group_invite_approve
import br.com.saqz.groups.resources.group_invite_approval
import br.com.saqz.groups.resources.group_invite_copy_link
import br.com.saqz.groups.resources.group_invite_code_copied_toast
import br.com.saqz.groups.resources.group_invite_code_title
import br.com.saqz.groups.resources.group_invite_copied_toast
import br.com.saqz.groups.resources.group_invite_copy_code
import br.com.saqz.groups.resources.group_invite_link_title
import br.com.saqz.groups.resources.group_invite_rotate_body
import br.com.saqz.groups.resources.group_invite_rotate_cancel
import br.com.saqz.groups.resources.group_invite_rotate_code
import br.com.saqz.groups.resources.group_invite_rotate_title
import br.com.saqz.groups.resources.group_invite_empty_description
import br.com.saqz.groups.resources.group_invite_empty_title
import br.com.saqz.groups.resources.group_invite_game_created_text
import br.com.saqz.groups.resources.group_invite_game_created_title
import br.com.saqz.groups.resources.group_invite_generate_code
import br.com.saqz.groups.resources.group_invite_generate_link
import br.com.saqz.groups.resources.group_invite_generate_link_note
import br.com.saqz.groups.resources.group_invite_joined_days
import br.com.saqz.groups.resources.group_invite_joined_hours
import br.com.saqz.groups.resources.group_invite_joined_minutes
import br.com.saqz.groups.resources.group_invite_joined_recently
import br.com.saqz.groups.resources.group_invite_load_error
import br.com.saqz.groups.resources.group_invite_operation_error
import br.com.saqz.groups.resources.group_invite_people
import br.com.saqz.groups.resources.group_invite_pending
import br.com.saqz.groups.resources.group_invite_reject
import br.com.saqz.groups.resources.group_invite_reject_body
import br.com.saqz.groups.resources.group_invite_reject_title
import br.com.saqz.groups.resources.group_invite_retry
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
    const val GenerateLink = "group-invite-generate-link"
    const val GenerateLinkNote = "group-invite-generate-link-note"
    const val Code = "group-invite-code"
    const val CopyCode = "group-invite-copy-code"
    const val Rotate = "group-invite-rotate"
    const val RotateConfirm = "group-invite-rotate-confirm"
    const val RejectConfirm = "group-invite-reject-confirm"
    const val RejectConfirmAction = "group-invite-reject-confirm-action"
    const val Copy = "group-invite-copy"
    const val Share = "group-invite-share"
    const val Approval = "group-invite-approval"
    const val Qr = "group-invite-qr"
    const val GameCreated = "group-invite-game-created"
}

@Composable
internal fun GroupInviteScreen(
    state: GroupInviteState,
    onBack: () -> Unit,
    onIntent: (GroupInviteIntent) -> Unit,
    modifier: Modifier = Modifier,
    gameCreated: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(GroupInviteTags.Screen)) {
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
                    if (gameCreated) GameCreatedHeader()
                    Text(state.groupName, style = SaqzTheme.typography.title, color = SaqzTheme.colors.textPrimary)
                    if (state.inviteStatus == InviteStatus.Active) {
                        InviteActiveCard(state = state, onIntent = onIntent)
                    } else if (state.isGenerating) {
                        // O link nasce sozinho ao abrir sem convite ativo: carregamento, não o vazio.
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = SaqzTheme.metrics.blockGap),
                            contentAlignment = Alignment.Center,
                        ) { SaqzSpinner() }
                    } else {
                        SaqzEmptyState(
                            title = stringResource(Res.string.group_invite_empty_title),
                            description = stringResource(Res.string.group_invite_empty_description),
                            action = stringResource(Res.string.group_invite_generate_code),
                            onAction = { onIntent(GroupInviteIntent.ConfirmRotate) },
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
        SaqzBottomSheet(
            open = state.isRotateConfirmVisible,
            onClose = { onIntent(GroupInviteIntent.DismissRotate) },
            title = stringResource(Res.string.group_invite_rotate_title),
            description = stringResource(Res.string.group_invite_rotate_body),
            splitFooter = {
                SaqzButton(
                    label = stringResource(Res.string.group_invite_rotate_cancel),
                    onClick = { onIntent(GroupInviteIntent.DismissRotate) },
                    variant = SaqzButtonVariant.Ghost,
                    modifier = Modifier.weight(1f),
                )
                SaqzButton(
                    label = stringResource(Res.string.group_invite_rotate_code),
                    onClick = { onIntent(GroupInviteIntent.ConfirmRotate) },
                    loading = state.isGenerating,
                    modifier = Modifier.weight(1f).testTag(GroupInviteTags.RotateConfirm),
                )
            },
        ) {}
        SaqzBottomSheet(
            open = state.rejectCandidate != null,
            onClose = { onIntent(GroupInviteIntent.DismissReject) },
            modifier = Modifier.testTag(GroupInviteTags.RejectConfirm),
            title = stringResource(Res.string.group_invite_reject_title, state.rejectCandidate?.displayName.orEmpty()),
            description = stringResource(Res.string.group_invite_reject_body),
            splitFooter = {
                SaqzButton(
                    label = stringResource(Res.string.group_invite_rotate_cancel),
                    onClick = { onIntent(GroupInviteIntent.DismissReject) },
                    variant = SaqzButtonVariant.Ghost,
                    modifier = Modifier.weight(1f),
                )
                SaqzButton(
                    label = stringResource(Res.string.group_invite_reject),
                    onClick = { state.rejectCandidate?.let { onIntent(GroupInviteIntent.RejectRequest(it.userId)) } },
                    variant = SaqzButtonVariant.Danger,
                    modifier = Modifier.weight(1f).testTag(GroupInviteTags.RejectConfirmAction),
                )
            },
        ) {}
        SaqzToast(
            visible = state.toast != null,
            onDismiss = { onIntent(GroupInviteIntent.ClearToast) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(SaqzTheme.metrics.horizontalPadding),
        ) {
            SaqzToastText(
                stringResource(
                    if (state.toast == GroupInviteToast.CodeCopied) Res.string.group_invite_code_copied_toast
                    else Res.string.group_invite_copied_toast,
                ),
            )
        }
    }
}

/** Sucesso de quem acabou de marcar o primeiro jogo; o próximo passo é o link logo abaixo. */
@Composable
private fun GameCreatedHeader() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(GroupInviteTags.GameCreated),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        Text(
            stringResource(Res.string.group_invite_game_created_title),
            style = SaqzTheme.typography.title,
            color = SaqzTheme.colors.textPrimary,
        )
        Text(
            stringResource(Res.string.group_invite_game_created_text),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun InviteActiveCard(state: GroupInviteState, onIntent: (GroupInviteIntent) -> Unit) {
    SaqzCard {
        state.inviteCode?.let { code ->
            Text(
                stringResource(Res.string.group_invite_code_title),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
            // O código que a galera digita em "Tenho um convite": grande, para ler e ditar sem erro.
            Text(
                code,
                style = SaqzTheme.typography.title.copy(fontSize = CODE_FONT_SIZE, letterSpacing = CODE_LETTER_SPACING),
                color = SaqzTheme.colors.textPrimary,
                modifier = Modifier.padding(vertical = SaqzTheme.metrics.grid).testTag(GroupInviteTags.Code),
            )
            SaqzButton(
                label = stringResource(Res.string.group_invite_copy_code),
                onClick = { onIntent(GroupInviteIntent.CopyCode) },
                variant = SaqzButtonVariant.Secondary,
                fullWidth = true,
                modifier = Modifier.testTag(GroupInviteTags.CopyCode),
            )
        }
        state.inviteUrl?.let {
            Text(
                stringResource(Res.string.group_invite_link_title),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
                modifier = Modifier.padding(top = SaqzTheme.metrics.grid),
            )
            Text(it, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textPrimary)
            Text(
                stringResource(Res.string.group_invite_never_expires),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
            SaqzButton(
                label = stringResource(Res.string.group_invite_copy_link),
                onClick = { onIntent(GroupInviteIntent.CopyLink) },
                variant = SaqzButtonVariant.Secondary,
                fullWidth = true,
                modifier = Modifier.padding(top = SaqzTheme.metrics.grid).testTag(GroupInviteTags.Copy),
            )
            SaqzButton(
                label = stringResource(Res.string.group_invite_whatsapp_primary),
                onClick = { onIntent(GroupInviteIntent.OpenMessagePreview) },
                fullWidth = true,
                modifier = Modifier.padding(top = SaqzTheme.metrics.grid).testTag(GroupInviteTags.Share),
            )
            SaqzButton(
                label = stringResource(Res.string.group_invite_share),
                onClick = { onIntent(GroupInviteIntent.OpenShareSheet) },
                variant = SaqzButtonVariant.Secondary,
                fullWidth = true,
                modifier = Modifier.padding(top = SaqzTheme.metrics.grid),
            )
        }
        if (state.inviteCode == null && state.inviteUrl == null) {
            GenerateLinkAction(state, onIntent)
        } else {
            // Trocar o código derruba o que já foi enviado: fica fora do destaque e sempre pede confirmação.
            SaqzButton(
                label = stringResource(Res.string.group_invite_rotate_code),
                onClick = { onIntent(GroupInviteIntent.RequestRotate) },
                variant = SaqzButtonVariant.Ghost,
                size = SaqzButtonSize.Sm,
                enabled = !state.isGenerating,
                fullWidth = true,
                modifier = Modifier.padding(top = SaqzTheme.metrics.grid).testTag(GroupInviteTags.Rotate),
            )
        }
    }
}

/**
 * Convite ativo, mas sem link nem código para mostrar: o backend v.0.0.17 só devolve o link na hora em
 * que ele é criado e a leitura seguinte vem sem ele. Sem nada para copiar ou compartilhar, a ação
 * principal é gerar um link novo, e a nota avisa o que isso custa (como no app antigo). Com o backend
 * atual a leitura traz o link e esta ação nunca aparece.
 */
@Composable
private fun GenerateLinkAction(state: GroupInviteState, onIntent: (GroupInviteIntent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = SaqzTheme.metrics.grid),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        SaqzButton(
            label = stringResource(Res.string.group_invite_generate_link),
            onClick = { onIntent(GroupInviteIntent.ConfirmRotate) },
            enabled = !state.isGenerating,
            loading = state.isGenerating,
            fullWidth = true,
            modifier = Modifier.testTag(GroupInviteTags.GenerateLink),
        )
        Text(
            stringResource(Res.string.group_invite_generate_link_note),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
            modifier = Modifier.testTag(GroupInviteTags.GenerateLinkNote),
        )
    }
}

private val CODE_FONT_SIZE = 36.sp
private val CODE_LETTER_SPACING = 4.sp

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
        // A consequência em uma linha: o que muda para quem abre o link.
        Text(
            text = stringResource(Res.string.group_invite_approval_hint),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
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
                        onClick = { onIntent(GroupInviteIntent.RequestReject(request.userId)) },
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

