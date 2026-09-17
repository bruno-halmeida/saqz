package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.GroupOwnDebtUi
import br.com.saqz.groups.presentation.details.OwnChargeStatusUi
import br.com.saqz.groups.presentation.details.OwnChargeUi
import br.com.saqz.groups.presentation.details.OwnChargesUi
import br.com.saqz.groups.presentation.ui.components.OwnChargeTicket
import br.com.saqz.groups.presentation.ui.components.OwnChargeTicketTags
import br.com.saqz.groups.presentation.ui.components.WaitingRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_cashbox_pix_copy
import br.com.saqz.groups.resources.group_details_own_charges_history_hide
import br.com.saqz.groups.resources.group_details_own_charges_history_show
import br.com.saqz.groups.resources.group_details_own_charges_no_pix
import br.com.saqz.groups.resources.group_details_own_charges_settled_meta
import br.com.saqz.groups.resources.group_details_own_charges_settled_title
import br.com.saqz.groups.resources.home_own_charge_copied
import br.com.saqz.groups.resources.own_charges_failure
import br.com.saqz.groups.resources.own_charges_note
import br.com.saqz.groups.resources.own_charges_retry
import br.com.saqz.groups.resources.own_charges_status_cancelled
import br.com.saqz.groups.resources.own_charges_status_paid
import br.com.saqz.groups.resources.own_charges_status_pending
import br.com.saqz.groups.resources.own_charges_status_waived
import br.com.saqz.groups.resources.own_charges_title
import org.jetbrains.compose.resources.stringResource

// Medidas do mock fora da grade de 4/8.
private val ChargeLineVerticalPadding = 10.dp
private val FailureIconSize = 20.dp
private val PixReceiverIconSize = 16.dp
private val SkeletonAmountHeight = 34.dp
private val SkeletonCountHeight = 14.dp

/**
 * "O que eu devo neste grupo", logo abaixo do jogo. Só existe enquanto há o que dizer:
 * carregando, falha ou pendência. Em dia, quem fala é [GroupOwnChargesSettledBlock], no fim
 * da tela — os dois nunca emitem juntos, e é isso que mantém cada tag uma vez só na árvore.
 *
 * Pagar é manual (decisão do fluxo 5): o único verbo é copiar a chave Pix. A seção antiga
 * (`GroupOwnChargesSection`) continua existindo para Perfil → Mensalidades.
 */
@Composable
internal fun GroupOwnDebtBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ownCharges = state.ownCharges ?: return
    val debt = ownCharges.debt?.takeIf { ownCharges.pending.isNotEmpty() }
    if (!ownCharges.isLoading && !ownCharges.failed && debt == null) return
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.OwnCharges),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.own_charges_title))
        when {
            ownCharges.isLoading -> OwnDebtSkeleton()
            ownCharges.failed -> OwnDebtFailure(onIntent = onIntent)
            debt != null -> OwnDebtContent(
                ownCharges = ownCharges,
                debt = debt,
                pixCopied = state.pixCopied,
                onIntent = onIntent,
            )
        }
    }
}

/**
 * A linha "Tudo em dia" do fim da tela: só com cobranças carregadas, nenhuma pendência e
 * algum histórico — que ela abre e fecha. Sem histórico não há o que mostrar.
 *
 * [onIntent] fica sem uso de propósito: a assinatura é a de todo bloco do detalhe e este
 * não tem intent para emitir (abrir o histórico é estado visual).
 */
@Suppress("UnusedParameter")
@Composable
internal fun GroupOwnChargesSettledBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ownCharges = state.ownCharges ?: return
    val settled = !ownCharges.isLoading && !ownCharges.failed &&
        ownCharges.pending.isEmpty() && ownCharges.history.isNotEmpty()
    if (!settled) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(Res.string.group_details_own_charges_settled_title)
    val meta = stringResource(Res.string.group_details_own_charges_settled_meta)
    val toggleLabel = historyToggleLabel(expanded = expanded, count = ownCharges.history.size)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.own_charges_title))
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
            SaqzCard(padded = false) {
                WaitingRow(
                    icon = SaqzIcons.Check,
                    title = title,
                    meta = meta,
                    contentDescription = "$title. $meta",
                    onClick = { expanded = !expanded },
                    tag = GroupDetailsTags.OwnChargesSettled,
                ) {
                    Text(
                        text = toggleLabel,
                        style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                        color = SaqzTheme.colors.primary,
                    )
                }
            }
            if (expanded) {
                OwnChargesHistoryCard(history = ownCharges.history)
            }
        }
    }
}

@Composable
private fun historyToggleLabel(expanded: Boolean, count: Int): String = if (expanded) {
    stringResource(Res.string.group_details_own_charges_history_hide)
} else {
    stringResource(Res.string.group_details_own_charges_history_show, count)
}

@Composable
private fun OwnDebtContent(
    ownCharges: OwnChargesUi,
    debt: GroupOwnDebtUi,
    pixCopied: Boolean,
    onIntent: (GroupDetailsIntent) -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
    val pix = ownCharges.pix
    OwnChargeTicket(
        eyebrow = debt.eyebrow,
        amountLabel = debt.totalLabel,
        dueChipLabel = debt.dueLabel,
        dueChipOverdue = debt.overdue,
        headerChip = null,
        countLabel = debt.countLabel,
        receiverLabel = null,
        copied = pixCopied,
        copyLabel = stringResource(Res.string.group_cashbox_pix_copy),
        copiedLabel = stringResource(Res.string.home_own_charge_copied),
        onCopy = if (pix != null) {
            { onIntent(GroupDetailsIntent.CopyPix) }
        } else {
            null
        },
        onClick = null,
        contentDescription = null,
        tags = OwnChargeTicketTags(card = GroupDetailsTags.OwnDebt, copy = GroupDetailsTags.OwnChargesPixCopy),
        footnote = stringResource(
            if (pix != null) Res.string.own_charges_note else Res.string.group_details_own_charges_no_pix,
        ),
    ) {
        OwnDebtPendingLines(pending = ownCharges.pending)
        if (pix != null) {
            OwnDebtPix(key = pix.key, receiverLabel = debt.receiverLabel)
        }
    }
    OwnDebtHistory(history = ownCharges.history)
}

/** Uma linha por pendência, entre divisórias. A tag de cada linha é contrato do e2e. */
@Composable
private fun OwnDebtPendingLines(pending: List<OwnChargeUi>) = Column(
    modifier = Modifier.fillMaxWidth().testTag(GroupDetailsTags.OwnChargesPending),
) {
    SaqzDivider()
    pending.forEach { charge ->
        OwnChargeLine(charge = charge, padding = PaddingValues(vertical = ChargeLineVerticalPadding))
        SaqzDivider()
    }
}

/**
 * Recebedor + chave. A chave é um `Text` PRÓPRIO com o texto exato dela: o e2e
 * `monthly-history` a procura por `onNodeWithText`.
 */
@Composable
private fun OwnDebtPix(key: String, receiverLabel: String?) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier.fillMaxWidth().testTag(GroupDetailsTags.OwnChargesPix),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid / 2),
    ) {
        if (receiverLabel != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(metrics.grid),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SaqzIcon(SaqzIcons.CreditCard, tint = colors.textSecondary, size = PixReceiverIconSize)
                Text(text = receiverLabel, style = SaqzTheme.typography.support, color = colors.textSecondary)
            }
        }
        Text(
            text = key,
            style = SaqzTheme.typography.compactTitle,
            color = colors.textPrimary,
            // Alinha a chave com o texto do recebedor: ícone de 16 + respiro de 8.
            modifier = if (receiverLabel != null) Modifier.padding(start = metrics.grid * 3) else Modifier,
        )
    }
}

/** Histórico recolhido: o card entra ACIMA do botão, como no mock. Sem histórico, nada. */
@Composable
private fun ColumnScope.OwnDebtHistory(history: List<OwnChargeUi>) {
    if (history.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (expanded) {
        OwnChargesHistoryCard(history = history)
    }
    SaqzButton(
        label = historyToggleLabel(expanded = expanded, count = history.size),
        onClick = { expanded = !expanded },
        variant = SaqzButtonVariant.Ghost,
        size = SaqzButtonSize.Sm,
        fullWidth = true,
        modifier = Modifier
            .heightIn(min = SaqzTheme.metrics.minimumTouchTarget)
            .testTag(GroupDetailsTags.OwnChargesHistoryToggle),
    )
}

@Composable
private fun OwnChargesHistoryCard(history: List<OwnChargeUi>) = SaqzCard(
    padded = false,
    modifier = Modifier.testTag(GroupDetailsTags.OwnChargesHistory),
) {
    val metrics = SaqzTheme.metrics
    history.forEachIndexed { index, charge ->
        if (index > 0) {
            SaqzDivider()
        }
        OwnChargeLine(
            charge = charge,
            padding = PaddingValues(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        )
    }
}

/**
 * Título + vencimento à esquerda, valor + status à direita — os quatro são DESCENDENTES da
 * tag da linha na árvore não mesclada (contrato do e2e). O vencimento é sempre neutro: o
 * estado não diz qual linha venceu, só o chip do ticket (`GroupOwnDebtUi.overdue`).
 */
@Composable
private fun OwnChargeLine(charge: OwnChargeUi, padding: PaddingValues) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(GroupDetailsTags.ownCharge(charge.id))
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(metrics.subGrid / 2),
        ) {
            Text(text = charge.title, style = SaqzTheme.typography.compactTitle, color = colors.textPrimary)
            Text(text = charge.dueLabel, style = SaqzTheme.typography.compactMeta, color = colors.textSecondary)
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            Text(text = charge.amountLabel, style = SaqzTheme.typography.compactTitle, color = colors.textPrimary)
            OwnChargeStatusChip(status = charge.status)
        }
    }
}

@Composable
private fun OwnChargeStatusChip(status: OwnChargeStatusUi) = when (status) {
    OwnChargeStatusUi.Pending -> SaqzStatusChip(
        text = stringResource(Res.string.own_charges_status_pending),
        tone = SaqzChipTone.Warning,
        dot = true,
    )
    OwnChargeStatusUi.Paid -> SaqzStatusChip(
        text = stringResource(Res.string.own_charges_status_paid),
        tone = SaqzChipTone.Success,
        dot = true,
    )
    OwnChargeStatusUi.Waived -> SaqzStatusChip(
        text = stringResource(Res.string.own_charges_status_waived),
        tone = SaqzChipTone.Neutral,
    )
    OwnChargeStatusUi.Cancelled -> SaqzStatusChip(
        text = stringResource(Res.string.own_charges_status_cancelled),
        tone = SaqzChipTone.Neutral,
    )
}

/** O esqueleto tem a silhueta do ticket: eyebrow, valor, contagem e o botão em pílula. */
@Composable
private fun OwnDebtSkeleton() {
    val metrics = SaqzTheme.metrics
    SaqzCard(
        cornerRadius = metrics.blockRadius,
        modifier = Modifier.testTag(GroupDetailsTags.OwnChargesSkeleton),
    ) {
        SaqzSkeleton(width = metrics.grid * 11, height = metrics.blockGap)
        SaqzSkeleton(width = metrics.grid * 20, height = SkeletonAmountHeight)
        SaqzSkeleton(width = metrics.grid * 31, height = SkeletonCountHeight)
        SaqzSkeleton(height = metrics.buttonHeight, radius = metrics.buttonHeight / 2)
    }
}

@Composable
private fun OwnDebtFailure(onIntent: (GroupDetailsIntent) -> Unit) = SaqzCard(
    modifier = Modifier.testTag(GroupDetailsTags.OwnChargesFailure),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(SaqzIcons.CircleAlert, tint = SaqzTheme.colors.errorForeground, size = FailureIconSize)
        Text(
            text = stringResource(Res.string.own_charges_failure),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
    SaqzButton(
        label = stringResource(Res.string.own_charges_retry),
        onClick = { onIntent(GroupDetailsIntent.RetryOwnCharges) },
        variant = SaqzButtonVariant.Secondary,
        size = SaqzButtonSize.Sm,
        modifier = Modifier.testTag(GroupDetailsTags.OwnChargesRetry),
    )
}

@Preview
@Composable
private fun GroupOwnDebtBlockPreview() = SaqzTheme {
    GroupOwnDebtBlock(state = GroupOwnDebtPreviewData.owesTwo, onIntent = {})
}

@Preview
@Composable
private fun GroupOwnChargesSettledBlockPreview() = SaqzTheme {
    GroupOwnChargesSettledBlock(state = GroupOwnDebtPreviewData.settled, onIntent = {})
}
