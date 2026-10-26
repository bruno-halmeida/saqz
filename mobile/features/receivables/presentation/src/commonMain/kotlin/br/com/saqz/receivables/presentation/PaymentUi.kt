package br.com.saqz.receivables.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.zIndex
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.PaymentError
import br.com.saqz.receivables.domain.ReceiptError
import br.com.saqz.receivables.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PaymentPage(title: String, tag: String, onBack: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(tag)) {
        SaqzTopAppBar(title = title, onBack = onBack, modifier = Modifier.zIndex(1f))
        Column(Modifier.weight(1f).clipToBounds().verticalScroll(rememberScrollState()).padding(SaqzTheme.metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap), content = content)
    }
}

/** Página das telas novas do recebimento: barra no cinza, o resto em branco e de borda a borda (como nos `.dc.html`). */
@Composable
internal fun ReceivablesPage(title: String, tag: String, onBack: () -> Unit, modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().background(SaqzTheme.colors.surface).testTag(tag)) {
        SaqzTopAppBar(title = title, onBack = onBack, modifier = Modifier.zIndex(1f))
        Column(Modifier.weight(1f).clipToBounds().verticalScroll(rememberScrollState()), content = content)
        if (footer != null) Column(Modifier.fillMaxWidth().padding(SaqzTheme.metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid), content = footer)
    }
}

@Composable
internal fun ReceiptErrorText(error: ReceiptError) = Text(stringResource(when (error) {
    ReceiptError.DENIED -> Res.string.receipt_error_DENIED
    ReceiptError.STALE -> Res.string.receipt_error_STALE
    ReceiptError.INVALID -> Res.string.receipt_error_INVALID
    ReceiptError.UNAVAILABLE -> Res.string.receipt_error_UNAVAILABLE
    ReceiptError.NETWORK -> Res.string.receipt_error_NETWORK
    ReceiptError.UNCERTAIN -> Res.string.receipt_error_UNCERTAIN
    ReceiptError.SIGNED_OUT -> Res.string.receipt_error_SIGNED_OUT
}))

@Composable
internal fun paymentErrorText(error: PaymentError) = stringResource(when (error) {
    PaymentError.SIGNED_OUT -> Res.string.payment_error_SIGNED_OUT
    PaymentError.NETWORK -> Res.string.payment_error_NETWORK
    PaymentError.UNAVAILABLE -> Res.string.payment_error_UNAVAILABLE
    PaymentError.INVALID -> Res.string.payment_error_INVALID
    PaymentError.PIX_NOT_READY -> Res.string.payment_error_PIX_NOT_READY
    PaymentError.CHARGE_NOT_PAYABLE -> Res.string.payment_error_CHARGE_NOT_PAYABLE
    PaymentError.DOCUMENT_REQUIRED -> Res.string.payment_error_DOCUMENT_REQUIRED
    PaymentError.INVALID_DOCUMENT -> Res.string.payment_error_INVALID_DOCUMENT
})

/** Falha curta com "Tentar de novo"; a tela nunca trava esperando rota que ainda não existe. */
@Composable
internal fun PaymentFailure(error: PaymentError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    SaqzEmptyState(title = paymentErrorText(error), modifier = modifier.testTag(PaymentTags.Error),
        action = onRetry?.let { stringResource(Res.string.payment_retry) }, onAction = onRetry)
}

/**
 * O destaque azul da marca, de borda a borda, com a bola ao fundo (o `SaqzHeroCard` reto). O valor vai no título em
 * Anybody; [detail] é o pedaço em verde-limão ("1 vencida"), a única cor de acento sobre o azul.
 */
@Composable
internal fun ReceivablesHero(
    kicker: String,
    amount: String,
    support: String?,
    modifier: Modifier = Modifier,
    detail: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) = SaqzHeroCard(kicker = kicker, title = amount, shape = RectangleShape, modifier = modifier.testTag(PaymentTags.Hero)) {
    if (support != null || detail != null) {
        val colors = SaqzTheme.colors
        Text(buildAnnotatedString {
            support?.let { append(it) }
            if (support != null && detail != null) append(" · ")
            detail?.let { withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold)) { append(it) } }
        }, style = SaqzTheme.typography.support, color = colors.onPrimary.copy(alpha = HERO_SUPPORT_ALPHA))
    }
    content?.invoke(this)
}

/** Faixa de seção ("Em aberto", "Histórico"), no gelo, como nos protótipos. */
@Composable
internal fun ReceivablesBand(title: String, modifier: Modifier = Modifier) = Box(
    modifier.fillMaxWidth().background(SaqzTheme.colors.surfaceSoft)
        .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.grid),
) { Text(title, style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = SaqzTheme.colors.textPrimary) }

/** Linha de cobrança: título, apoio e, à direita, o valor e a ação (ou o selo). */
@Composable
internal fun ReceivablesRow(
    title: String,
    support: String?,
    modifier: Modifier = Modifier,
    supportWarning: Boolean = false,
    trailing: @Composable () -> Unit = {},
) = Row(
    modifier.fillMaxWidth().padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.blockGap),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid / 2)) {
        Text(title, style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = SaqzTheme.colors.textPrimary)
        support?.let {
            Text(it, style = SaqzTheme.typography.caption,
                color = if (supportWarning) SaqzTheme.colors.warningForeground else SaqzTheme.colors.textSecondary)
        }
    }
    trailing()
}

/** Nota de rodapé curta, centralizada. */
@Composable
internal fun ReceivablesNote(text: String, modifier: Modifier = Modifier) = Text(
    text, modifier.fillMaxWidth().padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.blockGap),
    style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary,
)

object PaymentTags {
    const val Hero = "receivables-hero"
    const val Error = "receivables-error"
}

private const val HERO_SUPPORT_ALPHA = 0.82f
