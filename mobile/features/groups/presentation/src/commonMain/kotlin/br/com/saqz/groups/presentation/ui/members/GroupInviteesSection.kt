package br.com.saqz.groups.presentation.ui.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzMemberRow
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.membership.MonthlyInviteLinkState
import br.com.saqz.groups.domain.membership.MonthlyInviteeStatus
import br.com.saqz.groups.presentation.members.GroupInviteesIntent
import br.com.saqz.groups.presentation.members.GroupInviteesNotice
import br.com.saqz.groups.presentation.members.GroupInviteesState
import br.com.saqz.groups.presentation.members.InviteeUi
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteRules
import br.com.saqz.groups.resources.*
import org.jetbrains.compose.resources.stringResource

internal object GroupInviteesTags {
    const val Section = "group-invitees-section"
    const val Entry = "group-invitees-entry"
    const val Invite = "group-invitees-invite"
    const val ValidUntil = "group-invitees-valid-until"
    const val Paused = "group-invitees-paused"
    const val Expired = "group-invitees-expired"
    const val ResendAll = "group-invitees-resend-all"
    const val Invalidate = "group-invitees-invalidate"
    const val CopyLink = "group-invitees-copy-link"
    const val SheetCopyLink = "group-invitees-sheet-copy-link"
    const val Sheet = "group-invitees-sheet"
    const val Resend = "group-invitees-resend"
    const val Remove = "group-invitees-remove"
    const val ConfirmInvalidate = "group-invitees-confirm-invalidate"
    const val ConfirmInvalidateAction = "group-invitees-confirm-invalidate-action"

    fun row(id: String) = "group-invitees-row-$id"
}

/**
 * Seção "Convidados" (CONV-010): sem convidados, o cartão de entrada; com eles, o cabeçalho com "Convidar mensalista",
 * a validade do link, os avisos de pausa e de link vencido, as linhas com o estado e as ações da seção.
 */
internal fun LazyListScope.inviteesContent(state: GroupInviteesState, onIntent: (GroupInviteesIntent) -> Unit) {
    if (!state.visible) return
    if (state.invitees.isEmpty()) {
        item(key = "invitees-entry") { GroupInviteesEntryCard(onIntent) }
        return
    }
    item(key = "invitees") { GroupInviteesSection(state, onIntent) }
}

@Composable
private fun GroupInviteesEntryCard(onIntent: (GroupInviteesIntent) -> Unit) {
    SaqzCard(modifier = Modifier.testTag(GroupInviteesTags.Entry)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            SaqzIcon(SaqzIcons.MessageSquare, tint = SaqzTheme.colors.primary)
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                Strong(stringResource(Res.string.group_invitees_entry_title))
                Support(stringResource(Res.string.group_invitees_entry_text))
            }
        }
        SaqzButton(
            label = stringResource(Res.string.group_invitees_invite),
            onClick = { onIntent(GroupInviteesIntent.InviteMonthly) },
            variant = SaqzButtonVariant.Secondary,
            fullWidth = true,
            modifier = Modifier.testTag(GroupInviteesTags.Invite),
        )
    }
}

@Composable
private fun GroupInviteesSection(state: GroupInviteesState, onIntent: (GroupInviteesIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier.fillMaxWidth().testTag(GroupInviteesTags.Section),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzSectionHeader(
            title = "${stringResource(Res.string.group_invitees_title)} ${state.invitees.size}",
            action = stringResource(Res.string.group_invitees_invite),
            onAction = { onIntent(GroupInviteesIntent.InviteMonthly) },
            modifier = Modifier.testTag(GroupInviteesTags.Invite),
        )
        if (state.linkState == MonthlyInviteLinkState.ACTIVE && state.linkUntil != null) {
            Text(
                stringResource(Res.string.group_invitees_valid_until, state.linkUntil),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
                modifier = Modifier.testTag(GroupInviteesTags.ValidUntil),
            )
        }
        when {
            state.pausedRetryAt != null || state.invitees.any { it.status == MonthlyInviteeStatus.PAUSED } ->
                GroupInviteesNoticeCard(
                    title = stringResource(Res.string.group_invitees_paused_title),
                    text = state.pausedRetryAt?.let { stringResource(Res.string.group_invitees_paused_text, it) }
                        ?: stringResource(Res.string.group_invitees_paused_text_later),
                    action = stringResource(Res.string.group_invitees_copy_link),
                    actionTag = GroupInviteesTags.CopyLink,
                    tag = GroupInviteesTags.Paused,
                    onAction = { onIntent(GroupInviteesIntent.CopyLink) },
                )
            state.linkState == MonthlyInviteLinkState.EXPIRED -> GroupInviteesNoticeCard(
                title = stringResource(Res.string.group_invitees_expired_title),
                text = stringResource(Res.string.group_invitees_expired_text),
                action = stringResource(Res.string.group_invitees_resend_all),
                actionTag = GroupInviteesTags.ResendAll,
                tag = GroupInviteesTags.Expired,
                onAction = { onIntent(GroupInviteesIntent.ResendAll) },
            )
            else -> Unit
        }
        SaqzCard(padded = false) {
            state.invitees.forEachIndexed { index, invitee ->
                if (index > 0) SaqzDivider()
                SaqzMemberRow(
                    name = invitee.name,
                    meta = MonthlyInviteRules.formatPhone(invitee.phone),
                    onClick = { onIntent(GroupInviteesIntent.Open(invitee.id)) },
                    trailing = { InviteeStatusChip(invitee.status) },
                    stackTrailingOnLargeFont = true,
                    modifier = Modifier.testTag(GroupInviteesTags.row(invitee.id)),
                )
            }
        }
        val expiredNoticeShown = state.linkState == MonthlyInviteLinkState.EXPIRED && state.pausedRetryAt == null &&
            state.invitees.none { it.status == MonthlyInviteeStatus.PAUSED }
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.grid)) {
            if (state.canResendAll && !expiredNoticeShown) {
                SaqzButton(
                    label = stringResource(Res.string.group_invitees_resend_all),
                    onClick = { onIntent(GroupInviteesIntent.ResendAll) },
                    variant = SaqzButtonVariant.Secondary,
                    size = SaqzButtonSize.Sm,
                    enabled = !state.busy,
                    modifier = Modifier.testTag(GroupInviteesTags.ResendAll),
                )
            }
            if (state.canInvalidate) {
                SaqzButton(
                    label = stringResource(Res.string.group_invitees_invalidate),
                    onClick = { onIntent(GroupInviteesIntent.AskInvalidate) },
                    variant = SaqzButtonVariant.Ghost,
                    size = SaqzButtonSize.Sm,
                    enabled = !state.busy,
                    modifier = Modifier.testTag(GroupInviteesTags.Invalidate),
                )
            }
        }
    }
}

@Composable
private fun GroupInviteesNoticeCard(
    title: String,
    text: String,
    action: String,
    actionTag: String,
    tag: String,
    onAction: () -> Unit,
) {
    SaqzCard(modifier = Modifier.testTag(tag)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            SaqzIcon(SaqzIcons.Clock, tint = SaqzTheme.colors.warningForeground)
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                Strong(title)
                Support(text)
            }
        }
        SaqzButton(
            label = action,
            onClick = onAction,
            variant = SaqzButtonVariant.Secondary,
            fullWidth = true,
            modifier = Modifier.testTag(actionTag),
        )
    }
}

@Composable
internal fun InviteeStatusChip(status: MonthlyInviteeStatus) {
    val (label, tone) = when (status) {
        MonthlyInviteeStatus.QUEUED -> Res.string.group_invitees_status_queued to SaqzChipTone.Neutral
        MonthlyInviteeStatus.WAITING -> Res.string.group_invitees_status_waiting to SaqzChipTone.Brand
        MonthlyInviteeStatus.FAILED -> Res.string.group_invitees_status_failed to SaqzChipTone.Error
        MonthlyInviteeStatus.NO_WHATSAPP -> Res.string.group_invitees_status_no_whatsapp to SaqzChipTone.Warning
        MonthlyInviteeStatus.PAUSED -> Res.string.group_invitees_status_paused to SaqzChipTone.Warning
        MonthlyInviteeStatus.EXPIRED, MonthlyInviteeStatus.UNKNOWN -> Res.string.group_invitees_status_expired to SaqzChipTone.Neutral
    }
    SaqzStatusChip(text = stringResource(label), tone = tone)
}

/** Folha da linha: "Reenviar convite", "Copiar link" e "Remover da lista" (remover não invalida o link). */
@Composable
internal fun GroupInviteeActionsSheet(selected: InviteeUi?, onIntent: (GroupInviteesIntent) -> Unit) {
    val phone = selected?.phone?.let(MonthlyInviteRules::formatPhone).orEmpty()
    SaqzBottomSheet(
        open = selected != null,
        onClose = { onIntent(GroupInviteesIntent.DismissSheet) },
        title = selected?.name,
        description = if (selected?.status == MonthlyInviteeStatus.FAILED) {
            stringResource(Res.string.group_invitees_failed_detail, phone)
        } else {
            phone
        },
        modifier = Modifier.testTag(GroupInviteesTags.Sheet),
    ) {
        if (selected?.status != MonthlyInviteeStatus.QUEUED) {
            SheetAction(stringResource(Res.string.group_invitees_resend), GroupInviteesTags.Resend) {
                onIntent(GroupInviteesIntent.ResendSelected)
            }
        }
        SheetAction(stringResource(Res.string.group_invitees_copy_link), GroupInviteesTags.SheetCopyLink) {
            onIntent(GroupInviteesIntent.CopyLink)
        }
        SheetAction(stringResource(Res.string.group_invitees_remove), GroupInviteesTags.Remove, danger = true) {
            onIntent(GroupInviteesIntent.RemoveSelected)
        }
    }
}

@Composable
private fun SheetAction(label: String, tag: String, danger: Boolean = false, onClick: () -> Unit) {
    SaqzButton(
        label = label,
        onClick = onClick,
        variant = SaqzButtonVariant.Ghost,
        fullWidth = true,
        contentColor = if (danger) SaqzTheme.colors.errorForeground else null,
        modifier = Modifier.testTag(tag).semantics { contentDescription = label },
    )
}

/** Mesma confirmação do "Trocar o código" do convite público. */
@Composable
internal fun GroupInviteesInvalidateConfirmation(open: Boolean, onIntent: (GroupInviteesIntent) -> Unit) {
    SaqzBottomSheet(
        open = open,
        onClose = { onIntent(GroupInviteesIntent.DismissInvalidate) },
        title = stringResource(Res.string.group_invitees_invalidate_title),
        description = stringResource(Res.string.group_invitees_invalidate_text),
        modifier = Modifier.testTag(GroupInviteesTags.ConfirmInvalidate),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                SaqzButton(
                    label = stringResource(Res.string.group_invitees_invalidate),
                    onClick = { onIntent(GroupInviteesIntent.ConfirmInvalidate) },
                    variant = SaqzButtonVariant.Danger,
                    fullWidth = true,
                    modifier = Modifier.testTag(GroupInviteesTags.ConfirmInvalidateAction),
                )
                SaqzButton(
                    label = stringResource(Res.string.group_invitees_cancel),
                    onClick = { onIntent(GroupInviteesIntent.DismissInvalidate) },
                    variant = SaqzButtonVariant.Ghost,
                    fullWidth = true,
                )
            }
        },
    )
}

@Composable
internal fun inviteesNoticeText(notice: GroupInviteesNotice): String = when (notice) {
    is GroupInviteesNotice.Resent -> if (notice.count == 1) {
        stringResource(Res.string.group_invitees_notice_resent_one)
    } else {
        stringResource(Res.string.group_invitees_notice_resent_many, notice.count)
    }
    is GroupInviteesNotice.Removed -> stringResource(Res.string.group_invitees_notice_removed, MonthlyInviteRules.firstName(notice.name))
    GroupInviteesNotice.Invalidated -> stringResource(Res.string.group_invitees_notice_invalidated)
    GroupInviteesNotice.LinkCopied -> stringResource(Res.string.group_invitees_notice_copied)
    GroupInviteesNotice.WhatsAppUnavailable -> stringResource(Res.string.group_invitees_notice_unavailable)
    GroupInviteesNotice.Failed -> stringResource(Res.string.group_invitees_notice_failed)
}

@Composable
private fun Strong(text: String) = Text(text, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)

@Composable
private fun Support(text: String) = Text(text, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
