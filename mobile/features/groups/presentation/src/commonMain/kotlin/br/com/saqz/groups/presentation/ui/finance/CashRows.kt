package br.com.saqz.groups.presentation.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.theme.SaqzTheme

/** Faixa cinza-clara de borda a borda que abre um grupo da lista: "Mensalidades de outubro · 9 de 12 pagaram". */
@Composable
internal fun CashGroupHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SaqzTheme.colors.surfaceSoft)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        Text(
            text = title,
            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (!trailing.isNullOrEmpty()) {
            Text(
                text = trailing,
                style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = SaqzTheme.colors.textSecondary,
            )
        }
    }
}

/** Linha branca da lista, com a divisória por cima a partir da segunda. */
@Composable
internal fun CashListRow(
    first: Boolean,
    modifier: Modifier = Modifier,
    below: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val metrics = SaqzTheme.metrics
    Column(modifier.fillMaxWidth().background(SaqzTheme.colors.surface)) {
        if (!first) SaqzDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = metrics.minimumTouchTarget + metrics.grid * 2)
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
            content = content,
        )
        below?.let {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    start = metrics.horizontalPadding,
                    end = metrics.horizontalPadding,
                    bottom = metrics.blockGap,
                ),
                content = it,
            )
        }
    }
}

/** Nome em semibold e a linha de apoio cinza; ocupa o meio da linha. */
@Composable
internal fun androidx.compose.foundation.layout.RowScope.CashRowText(title: String, subtitle: String?, muted: Boolean = false) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid / 2)) {
        Text(
            text = title,
            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = if (muted) SaqzTheme.colors.textSecondary else SaqzTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrEmpty()) {
            Text(text = subtitle, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
        }
    }
}

enum class CashTileTone { In, Out, Brand }

/** Ícone redondo de 40: verde da entrada, vermelho da saída ou azul da marca. */
@Composable
internal fun CashIconTile(icon: ImageVector, tone: CashTileTone) {
    val colors = SaqzTheme.colors
    val tint = when (tone) {
        CashTileTone.In -> colors.successForeground
        CashTileTone.Out -> colors.errorForeground
        CashTileTone.Brand -> colors.primary
    }
    Box(
        modifier = Modifier.size(SaqzTheme.metrics.grid * 5).background(tint.copy(alpha = TILE_ALPHA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        SaqzIcon(icon, tint = tint)
    }
}

/** Entrada ou saída do caixa e do extrato: ícone, título, apoio e o valor (saída em vermelho, entrada em verde). */
@Composable
internal fun CashLedgerRow(
    title: String,
    subtitle: String,
    amount: String,
    out: Boolean,
    first: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) = CashListRow(first = first, modifier = modifier) {
    CashIconTile(if (out) SaqzIcons.MoneyOut else SaqzIcons.MoneyIn, if (out) CashTileTone.Out else CashTileTone.In)
    CashRowText(title, subtitle)
    Text(
        text = amount,
        style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.Bold),
        color = if (out) SaqzTheme.colors.errorForeground else SaqzTheme.colors.successForeground,
    )
    trailing?.invoke()
}

/**
 * A faixa translúcida dentro do azul: "6 jogaram sem cobrança · Em 2 jogos de outubro" com **Revisar** em branco
 * (FIN-016). Usada no caixa e no acerto.
 */
@Composable
internal fun CashPendingStrip(
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionTag: String? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.onPrimary.copy(alpha = STRIP_ALPHA), RoundedCornerShape(metrics.cardRadius))
            .padding(start = metrics.blockGap, end = metrics.grid, top = metrics.grid, bottom = metrics.grid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzIcon(SaqzIcons.Users, tint = colors.onPrimary)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPrimary,
            )
            Text(
                text = subtitle,
                style = SaqzTheme.typography.caption,
                color = colors.onPrimary.copy(alpha = STRIP_SUB_ALPHA),
            )
        }
        SaqzButton(
            label = action,
            onClick = onAction,
            variant = SaqzButtonVariant.Inverse,
            size = SaqzButtonSize.Sm,
            modifier = actionTag?.let { Modifier.testTag(it) } ?: Modifier,
        )
    }
}

/** Campo de dinheiro com "R$" à frente (saldo inicial e valor da revisão). */
@Composable
internal fun CashMoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helperText: String? = null,
    errorText: String? = null,
    showLabel: Boolean = true,
) = SaqzInput(
    value = value,
    onValueChange = onValueChange,
    label = label,
    modifier = modifier,
    helperText = helperText,
    errorText = errorText,
    invalid = errorText != null,
    showLabel = showLabel,
    placeholder = "0,00",
    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
    leadingContent = {
        Text(
            text = "R$",
            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = SaqzTheme.colors.textSecondary,
        )
    },
)

private const val TILE_ALPHA = 0.10f
private const val STRIP_ALPHA = 0.12f
private const val STRIP_SUB_ALPHA = 0.82f
