package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.OwnChargeStatusUi
import br.com.saqz.groups.presentation.details.OwnChargeUi
import br.com.saqz.groups.presentation.details.OwnChargesUi
import br.com.saqz.groups.presentation.ui.finance.PixCard
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.own_charges_failure
import br.com.saqz.groups.resources.own_charges_history_title
import br.com.saqz.groups.resources.own_charges_note
import br.com.saqz.groups.resources.own_charges_pending_title
import br.com.saqz.groups.resources.own_charges_retry
import br.com.saqz.groups.resources.own_charges_status_cancelled
import br.com.saqz.groups.resources.own_charges_status_paid
import br.com.saqz.groups.resources.own_charges_status_pending
import br.com.saqz.groups.resources.own_charges_status_waived
import br.com.saqz.groups.resources.own_charges_title
import org.jetbrains.compose.resources.stringResource

/**
 * VUL-203 — "o que eu devo neste grupo". Pendências em destaque no bloco ice, histórico
 * abaixo em card branco, e o Pix do grupo só quando há o que pagar.
 *
 * Pagar é manual por decisão do fluxo 5: a seção não tem verbo além de copiar a chave —
 * quem baixa a cobrança é o organizador, pelo caixa.
 */
@Composable
internal fun GroupOwnChargesSection(
    ownCharges: OwnChargesUi,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.OwnCharges),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
) {
    SaqzSectionHeader(title = stringResource(Res.string.own_charges_title))
    when {
        ownCharges.isLoading -> OwnChargesSkeleton()
        ownCharges.failed -> OwnChargesFailure(onIntent = onIntent)
        else -> {
            if (ownCharges.pending.isNotEmpty()) {
                OwnChargesGroup(
                    title = stringResource(Res.string.own_charges_pending_title),
                    charges = ownCharges.pending,
                    tag = GroupDetailsTags.OwnChargesPending,
                    tone = SaqzCardTone.Soft,
                )
            }
            if (ownCharges.history.isNotEmpty()) {
                OwnChargesGroup(
                    title = stringResource(Res.string.own_charges_history_title),
                    charges = ownCharges.history,
                    tag = GroupDetailsTags.OwnChargesHistory,
                    tone = SaqzCardTone.Default,
                )
            }
            ownCharges.pix?.let {
                PixCard(
                    pix = it,
                    onCopy = { onIntent(GroupDetailsIntent.CopyPix) },
                    cardTag = GroupDetailsTags.OwnChargesPix,
                    copyTag = GroupDetailsTags.OwnChargesPixCopy,
                )
                Text(
                    text = stringResource(Res.string.own_charges_note),
                    style = SaqzTheme.typography.caption,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun OwnChargesGroup(
    title: String,
    charges: List<OwnChargeUi>,
    tag: String,
    tone: SaqzCardTone,
) = Column(
    modifier = Modifier.fillMaxWidth().testTag(tag),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
) {
    Text(
        text = title,
        style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = SaqzTheme.colors.textSecondary,
    )
    SaqzCard(padded = false, tone = tone) {
        charges.forEachIndexed { index, charge ->
            if (index > 0) {
                SaqzDivider()
            }
            OwnChargeRow(charge)
        }
    }
}

@Composable
private fun OwnChargeRow(charge: OwnChargeUi) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
            .testTag(GroupDetailsTags.ownCharge(charge.id)),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            Text(
                text = charge.title,
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                text = charge.dueLabel,
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            Text(
                text = charge.amountLabel,
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = SaqzTheme.colors.textPrimary,
            )
            OwnChargeStatusChip(charge.status)
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

@Composable
private fun OwnChargesSkeleton() = SaqzCard(modifier = Modifier.testTag(GroupDetailsTags.OwnChargesSkeleton)) {
    val metrics = SaqzTheme.metrics
    repeat(SkeletonRows) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
            ) {
                SaqzSkeleton(width = metrics.grid * 16)
                SaqzSkeleton(width = metrics.grid * 9, height = metrics.blockGap)
            }
            SaqzSkeleton(width = metrics.grid * 8)
        }
    }
}

private const val SkeletonRows = 2

@Composable
private fun OwnChargesFailure(onIntent: (GroupDetailsIntent) -> Unit) = SaqzCard(
    modifier = Modifier.testTag(GroupDetailsTags.OwnChargesFailure),
) {
    Text(
        text = stringResource(Res.string.own_charges_failure),
        style = SaqzTheme.typography.support,
        color = SaqzTheme.colors.textSecondary,
    )
    SaqzButton(
        label = stringResource(Res.string.own_charges_retry),
        onClick = { onIntent(GroupDetailsIntent.RetryOwnCharges) },
        variant = SaqzButtonVariant.Secondary,
        size = SaqzButtonSize.Sm,
        modifier = Modifier.testTag(GroupDetailsTags.OwnChargesRetry),
    )
}
