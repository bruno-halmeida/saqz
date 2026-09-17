package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.ui.components.WaitingRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.communication_failure
import br.com.saqz.groups.resources.communication_reminded
import br.com.saqz.groups.resources.group_details_waiting_notify
import br.com.saqz.groups.resources.group_details_waiting_quorum
import br.com.saqz.groups.resources.group_details_waiting_quorum_one
import br.com.saqz.groups.resources.home_admin_waiting_entry_chip
import br.com.saqz.groups.resources.home_admin_waiting_title
import org.jetbrains.compose.resources.stringResource

// Mesmo véu do `SaqzStatusChip` no tom Success.
private const val SentBadgeAlpha = 0.12f

/**
 * "Esperando você": o que ESTE grupo espera do gestor. Um card flush com até quatro linhas —
 * quórum (com o botão "Avisar" e o retorno dele), pedidos de entrada, mensalidades a receber e
 * acerto de jogo. Só gestor; sem nenhuma linha o bloco não emite nada.
 *
 * O texto de sucesso (`communication_reminded`) e a tag `NotifyPending` são contrato do e2e
 * (`ReminderE2eTest`). A linha de mensalidades usa `WaitingMonthly`: a tag `Cashbox` é única e
 * mora na Gestão.
 */
@Composable
internal fun GroupWaitingBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isAdmin) return
    val chevron: @Composable () -> Unit = { SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary) }
    val rows = buildList<@Composable () -> Unit> {
        if (state.showsQuorum()) add { QuorumRow(state = state, onIntent = onIntent) }
        state.waiting?.entryRequests?.let { row ->
            add {
                WaitingRow(
                    icon = SaqzIcons.Users,
                    title = row.title,
                    meta = row.meta.ifEmpty { null },
                    contentDescription = row.contentDescription,
                    onClick = { onIntent(GroupDetailsIntent.InviteByLink) },
                    tag = GroupDetailsTags.WaitingEntryRequests,
                    trailing = {
                        SaqzStatusChip(
                            text = stringResource(Res.string.home_admin_waiting_entry_chip, row.count),
                            tone = SaqzChipTone.Warning,
                            dot = true,
                        )
                    },
                )
            }
        }
        state.waiting?.monthly?.let { row ->
            add {
                WaitingRow(
                    icon = SaqzIcons.CreditCard,
                    title = row.title,
                    meta = row.meta.ifEmpty { null },
                    contentDescription = row.contentDescription,
                    onClick = { onIntent(GroupDetailsIntent.OpenCashbox) },
                    tag = GroupDetailsTags.WaitingMonthly,
                    trailing = chevron,
                )
            }
        }
        state.waiting?.settle?.let { row ->
            add {
                WaitingRow(
                    icon = SaqzIcons.Calendar,
                    title = row.title,
                    meta = row.meta.ifEmpty { null },
                    contentDescription = row.contentDescription,
                    onClick = { onIntent(GroupDetailsIntent.OpenSettlement(row.gameId)) },
                    tag = GroupDetailsTags.WaitingSettle,
                    trailing = chevron,
                )
            }
        }
    }
    if (rows.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.Waiting),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.home_admin_waiting_title))
        SaqzCard(padded = false) {
            rows.forEachIndexed { index, row ->
                if (index > 0) SaqzDivider()
                row()
            }
        }
    }
}

/**
 * A linha existe enquanto dá para avisar (confirmações abertas e alguém sem resposta) e
 * continua depois de um aviso enviado, mesmo que o pendente zere: o retorno é contrato do e2e.
 */
private fun GroupDetailsState.showsQuorum(): Boolean =
    notifiedCount != null || (nextGame?.confirmationOpen == true && (attendance?.pending ?: 0) > 0)

@Composable
private fun QuorumRow(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending = state.attendance?.pending ?: 0
    val title =
        if (pending == 1) stringResource(Res.string.group_details_waiting_quorum_one)
        else stringResource(Res.string.group_details_waiting_quorum, pending)
    val notified = state.notifiedCount
    val meta = when {
        notified != null -> stringResource(Res.string.communication_reminded, notified)
        state.notificationFailed -> stringResource(Res.string.communication_failure)
        else -> state.nextGame?.deadlineShort?.ifEmpty { null }
    }
    val sentBadge: (@Composable () -> Unit)? = if (notified != null) { { QuorumSentBadge() } } else null
    WaitingRow(
        icon = if (notified != null) SaqzIcons.Check else SaqzIcons.Megaphone,
        title = title,
        meta = meta,
        contentDescription = listOfNotNull(title, meta).joinToString(". "),
        onClick = null,
        tag = GroupDetailsTags.WaitingQuorum,
        modifier = modifier,
        metaMaxLines = 2,
        leading = sentBadge,
        trailing = {
            if (notified == null) {
                SaqzButton(
                    label = stringResource(Res.string.group_details_waiting_notify),
                    onClick = { onIntent(GroupDetailsIntent.NotifyPending) },
                    modifier = Modifier.testTag(GroupDetailsTags.NotifyPending),
                    variant = SaqzButtonVariant.Secondary,
                    size = SaqzButtonSize.Sm,
                    enabled = !state.notifying,
                    loading = state.notifying,
                )
            }
        },
    )
}

/** Aviso enviado: o círculo do ícone vira verde com o check (mock `GestorAvisado`). */
@Composable
private fun QuorumSentBadge(modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    Box(
        modifier = modifier
            .size(SaqzTheme.metrics.grid * 5)
            .clip(CircleShape)
            .background(colors.success.copy(alpha = SentBadgeAlpha), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        SaqzIcon(icon = SaqzIcons.Check, tint = colors.success)
    }
}
