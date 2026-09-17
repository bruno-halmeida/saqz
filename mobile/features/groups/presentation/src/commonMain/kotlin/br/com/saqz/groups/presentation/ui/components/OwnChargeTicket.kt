package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme

/** `testTag` do ticket e do botão de copiar: cada tela responde pelo próprio inventário. */
@Immutable
internal data class OwnChargeTicketTags(
    val card: String,
    val copy: String,
)

/**
 * Ticket de "o que eu devo" (VUL-220): eyebrow azul em caixa alta, valor em `display`, chip do
 * vencimento, recebedor do Pix e UM verbo — copiar a chave. Pagar é manual (decisão do fluxo 5).
 *
 * - [headerChip] é o chip Brand ao lado do eyebrow (na Início, o nome do grupo). Com ele, o
 *   chip do vencimento fica ao lado do valor; sem ele (detalhe do grupo), o chip do vencimento
 *   sobe para a linha do eyebrow e o valor fica sozinho.
 * - [dueChipOverdue] escolhe o tom: vencida é âmbar com ponto — lembrete, não alarme (decisão
 *   do VUL-202); no prazo é neutro, sem ponto.
 * - [onCopy] nulo (grupo sem chave Pix) esconde a linha do recebedor e o botão.
 * - [onClick] nulo = card sem clique. Com [onClick], [contentDescription] é o rótulo do clique e
 *   a descrição do card (na Início, o nome do grupo).
 * - [details] entra entre o valor e o recebedor (o detalhe lista ali as cobranças em aberto) e
 *   [footnote] fecha o card em `caption`, depois do botão.
 */
@Composable
internal fun OwnChargeTicket(
    eyebrow: String,
    amountLabel: String,
    dueChipLabel: String,
    dueChipOverdue: Boolean,
    headerChip: String?,
    countLabel: String?,
    receiverLabel: String?,
    copied: Boolean,
    copyLabel: String,
    copiedLabel: String,
    onCopy: (() -> Unit)?,
    onClick: (() -> Unit)?,
    contentDescription: String?,
    tags: OwnChargeTicketTags,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    details: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    SaqzCard(
        cornerRadius = metrics.blockRadius,
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
            .testTag(tags.card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.grid),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Eyebrow em caixa alta no próprio texto: a escala não transforma.
            Text(
                text = eyebrow.uppercase(),
                style = SaqzTheme.typography.eyebrow,
                color = colors.primary,
                modifier = Modifier.weight(1f),
            )
            if (headerChip != null) {
                SaqzStatusChip(text = headerChip, tone = SaqzChipTone.Brand)
            } else {
                DueChip(label = dueChipLabel, overdue = dueChipOverdue)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = amountLabel,
                style = SaqzTheme.typography.display,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (headerChip != null) {
                DueChip(label = dueChipLabel, overdue = dueChipOverdue)
            }
        }
        countLabel?.let {
            Text(text = it, style = SaqzTheme.typography.support, color = colors.textSecondary)
        }
        details?.invoke(this)
        if (onCopy != null) {
            receiverLabel?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(metrics.grid),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SaqzIcon(SaqzIcons.CreditCard, tint = colors.textSecondary, size = PixIconSize)
                    Text(text = it, style = SaqzTheme.typography.support, color = colors.textSecondary)
                }
            }
            SaqzButton(
                label = if (copied) copiedLabel else copyLabel,
                onClick = onCopy,
                variant = if (copied) SaqzButtonVariant.Secondary else SaqzButtonVariant.Primary,
                fullWidth = true,
                leadingContent = if (copied) {
                    { tint -> SaqzIcon(SaqzIcons.Check, tint = tint, size = PixCheckSize) }
                } else {
                    null
                },
                modifier = Modifier.testTag(tags.copy),
            )
        }
        footnote?.let {
            Text(text = it, style = SaqzTheme.typography.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun DueChip(label: String, overdue: Boolean) = SaqzStatusChip(
    text = label,
    tone = if (overdue) SaqzChipTone.Warning else SaqzChipTone.Neutral,
    dot = overdue,
)

private val PixIconSize = 16.dp
private val PixCheckSize = 18.dp
