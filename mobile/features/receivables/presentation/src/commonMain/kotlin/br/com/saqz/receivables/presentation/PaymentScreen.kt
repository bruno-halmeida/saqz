package br.com.saqz.receivables.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.PaymentError
import br.com.saqz.receivables.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

object PaymentScreenTags {
    const val Screen = "receivable-payment"
    const val TabPix = "receivable-payment-tab-pix"
    const val TabCard = "receivable-payment-tab-card"
    const val Copy = "receivable-payment-copy"
    const val Payload = "receivable-payment-payload"
    const val Cpf = "receivable-payment-cpf"
    const val ContinueCard = "receivable-payment-continue-card"
    const val NewCode = "receivable-payment-new-code"
    const val Nothing = "receivable-payment-nothing"
    const val Confirmed = "receivable-payment-confirmed"
    const val Offer = "receivable-payment-offer"
    const val AcceptOffer = "receivable-payment-offer-accept"
    const val DeclineOffer = "receivable-payment-offer-decline"
    const val Done = "receivable-payment-done"
}

@Composable
fun PaymentRoot(
    route: ReceivablePaymentRoute,
    onClose: () -> Unit,
    onOpenAutopay: () -> Unit,
    viewModel: PaymentViewModel = koinViewModel(key = "payment/${route.groupId}/${route.chargeIds}/${route.gameId}/${route.billingMonth}",
        parameters = { parametersOf(route.args()) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is PaymentEffect.Copy -> clipboard.setText(AnnotatedString(effect.text))
            is PaymentEffect.OpenUrl -> runCatching { uri.openUri(effect.url) }
            PaymentEffect.OpenAutopay -> onOpenAutopay()
        }
    }
    // Volta da página do Asaas: consulta o código na hora, sem esperar a próxima volta.
    RefreshWhenVisible { viewModel.onIntent(PaymentIntent.Resumed) }
    PaymentScreen(state, viewModel::onIntent, onClose)
}

@Composable
fun PaymentScreen(state: PaymentState, onIntent: (PaymentIntent) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val confirmed = state.confirmed
    if (confirmed != null) {
        ConfirmedScreen(confirmed, onIntent, onClose, modifier)
        return
    }
    ReceivablesPage(stringResource(when (state.subject) {
        PaymentSubject.GAME -> Res.string.pay_title_game
        PaymentSubject.MONTHLY -> Res.string.pay_title_monthly
        PaymentSubject.MANY -> Res.string.pay_title_many
    }), PaymentScreenTags.Screen, onClose, modifier) {
        val metrics = SaqzTheme.metrics
        val padded = Modifier.fillMaxWidth().padding(horizontal = metrics.horizontalPadding)
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(metrics.sectionGap)) { SaqzSpinner(Modifier.align(Alignment.Center)) }
            state.nothingOpen -> SaqzEmptyState(title = stringResource(Res.string.charges_nothing_open),
                description = state.error?.let { paymentErrorText(it) }, modifier = padded.testTag(PaymentScreenTags.Nothing))
            state.error != null && state.amountLabel.isEmpty() -> PaymentFailure(state.error, { onIntent(PaymentIntent.Retry) }, padded)
            else -> {
                Summary(state, padded.padding(top = metrics.sectionGap - metrics.subGrid))
                if (state.pixAvailable && state.cardAvailable) {
                    SaqzSegmented(
                        options = listOf(stringResource(Res.string.pay_tab_pix), stringResource(Res.string.pay_tab_card)),
                        selected = if (state.tab == PayTab.PIX) 0 else 1,
                        onSelect = { onIntent(PaymentIntent.SelectTab(if (it == 0) PayTab.PIX else PayTab.CARD)) },
                        optionTags = listOf(PaymentScreenTags.TabPix, PaymentScreenTags.TabCard),
                        modifier = padded.padding(top = metrics.sectionGap - metrics.subGrid),
                    )
                }
                Column(padded.padding(vertical = metrics.sectionGap - metrics.subGrid),
                    verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
                    state.error?.let { Text(paymentErrorText(it), style = SaqzTheme.typography.support,
                        color = SaqzTheme.colors.errorForeground, modifier = Modifier.testTag(PaymentTags.Error)) }
                    when {
                        state.codeExpired -> Expired(onIntent)
                        state.tab == PayTab.PIX -> PixContent(state, onIntent)
                        else -> CardContent(state, onIntent)
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(state: PaymentState, modifier: Modifier = Modifier) = Column(modifier,
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
    Text(state.kicker.uppercase(), style = SaqzTheme.typography.eyebrow, color = SaqzTheme.colors.textSecondary)
    Text(state.amountLabel, style = SaqzTheme.typography.display, color = SaqzTheme.colors.textPrimary)
    state.lines.forEach { line ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(line.label, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
            Text(line.amountLabel, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun ColumnScope.PixContent(state: PaymentState, onIntent: (PaymentIntent) -> Unit) {
    val payload = state.pixPayload
    if (state.creatingCode || payload == null) {
        Text(stringResource(Res.string.pay_creating), style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
        if (state.creatingCode) SaqzSpinner(Modifier.align(Alignment.CenterHorizontally))
        return
    }
    state.pixImage?.let { image ->
        Text(stringResource(Res.string.pay_qr), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
        PaymentQrImage(image)
    }
    SaqzCard(tone = SaqzCardTone.Soft) {
        Text(stringResource(Res.string.pay_copy_paste), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
        Text(payload, maxLines = 2, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textPrimary,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.testTag(PaymentScreenTags.Payload))
    }
    SaqzButton(stringResource(if (state.copied) Res.string.pay_copied else Res.string.pay_copy), { onIntent(PaymentIntent.CopyPix) },
        fullWidth = true, modifier = Modifier.testTag(PaymentScreenTags.Copy))
    state.payeeName?.let {
        Text(stringResource(Res.string.pay_payee, it), style = SaqzTheme.typography.support, color = SaqzTheme.colors.textPrimary)
    }
    Text(stringResource(Res.string.pay_value_fixed), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
    Waiting(stringResource(Res.string.pay_waiting))
}

@Composable
private fun ColumnScope.CardContent(state: PaymentState, onIntent: (PaymentIntent) -> Unit) {
    if (state.needsDocument) {
        Text(stringResource(Res.string.pay_cpf_intro), style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
        SaqzInput(value = state.cpf, onValueChange = { onIntent(PaymentIntent.CpfChanged(it)) },
            label = stringResource(Res.string.pay_cpf_label), helperText = stringResource(Res.string.pay_cpf_support),
            errorText = if (state.cpfInvalid) stringResource(Res.string.pay_cpf_invalid) else null, invalid = state.cpfInvalid,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, visualTransformation = CpfVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag(PaymentScreenTags.Cpf))
    }
    Text(stringResource(Res.string.pay_card_note), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
    SaqzButton(stringResource(Res.string.pay_card_continue), { onIntent(PaymentIntent.ContinueCard) }, fullWidth = true,
        enabled = state.canContinueCard, loading = state.creatingCode, modifier = Modifier.testTag(PaymentScreenTags.ContinueCard))
    if (state.waitingCard) Waiting(stringResource(Res.string.pay_card_waiting))
}

@Composable
private fun Waiting(text: String) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
    SaqzSpinner(size = SaqzTheme.metrics.grid * 3)
    Text(text, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary, modifier = Modifier.weight(1f))
}

@Composable
private fun ColumnScope.Expired(onIntent: (PaymentIntent) -> Unit) {
    Text(stringResource(Res.string.pay_expired), style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
    SaqzButton(stringResource(Res.string.pay_new_code), { onIntent(PaymentIntent.NewCode) }, fullWidth = true,
        modifier = Modifier.testTag(PaymentScreenTags.NewCode))
}

@Composable
private fun ConfirmedScreen(confirmed: PaymentConfirmedUi, onIntent: (PaymentIntent) -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier) = ReceivablesPage(stringResource(Res.string.confirmed_title), PaymentScreenTags.Confirmed, onClose,
    modifier,
    footer = {
        SaqzButton(stringResource(Res.string.confirmed_back), onClose, fullWidth = true,
            variant = if (confirmed.offer) SaqzButtonVariant.Ghost else SaqzButtonVariant.Primary,
            modifier = Modifier.testTag(PaymentScreenTags.Done))
    }) {
    val metrics = SaqzTheme.metrics
    Column(Modifier.fillMaxWidth().padding(metrics.horizontalPadding).padding(top = metrics.sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
        SaqzIcon(SaqzIcons.Check, tint = SaqzTheme.colors.successForeground, size = metrics.grid * 6)
        Text(stringResource(Res.string.confirmed_title), style = SaqzTheme.typography.headline, color = SaqzTheme.colors.textPrimary,
            textAlign = TextAlign.Center)
        Text(confirmed.summary, style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = SaqzTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Text(stringResource(Res.string.confirmed_body), style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary,
            textAlign = TextAlign.Center)
    }
    if (confirmed.offer) {
        ReceivablesHero(kicker = stringResource(Res.string.autopay_kicker), amount = stringResource(Res.string.confirmed_offer_title),
            support = confirmed.dueDay?.let { stringResource(Res.string.confirmed_offer_body, it) }
                ?: stringResource(Res.string.confirmed_offer_body_no_day),
            modifier = Modifier.padding(top = metrics.sectionGap).testTag(PaymentScreenTags.Offer)) {
            SaqzButton(stringResource(Res.string.confirmed_offer_accept), { onIntent(PaymentIntent.AcceptOffer) },
                variant = SaqzButtonVariant.Inverse, fullWidth = true, modifier = Modifier.testTag(PaymentScreenTags.AcceptOffer))
            SaqzButton(stringResource(Res.string.confirmed_offer_decline), { onIntent(PaymentIntent.DeclineOffer) },
                variant = SaqzButtonVariant.Ghost, contentColor = SaqzTheme.colors.onPrimary, fullWidth = true,
                modifier = Modifier.testTag(PaymentScreenTags.DeclineOffer))
        }
    }
}

private val previewPix = PaymentState(groupName = "Vôlei do CERET", loading = false, subject = PaymentSubject.GAME,
    kicker = "Vôlei do CERET · terça, 28/07", amountLabel = "R$ 40,00",
    lines = listOf(PaymentLineUi("Sua diária", "R$ 20,00"), PaymentLineUi("Convidado: Rafa", "R$ 20,00")),
    pixPayload = "00020101021226850014br.gov.bcb.pix2563qr…", payeeName = "Marcos Lima", needsDocument = true)

@Preview(name = "Pagar com Pix", widthDp = 390)
@Composable
private fun PaymentPixPreview() = SaqzTheme { PaymentScreen(previewPix, {}, {}) }

@Preview(name = "Pagar no cartão · CPF uma vez", widthDp = 390)
@Composable
private fun PaymentCardPreview() = SaqzTheme { PaymentScreen(previewPix.copy(tab = PayTab.CARD), {}, {}) }

@Preview(name = "Pagamento confirmado + oferta", widthDp = 390)
@Composable
private fun PaymentConfirmedPreview() = SaqzTheme {
    PaymentScreen(previewPix.copy(confirmed = PaymentConfirmedUi("R$ 100,00 · mensalidade de agosto", true, "10")), {}, {})
}

@Preview(name = "Nada em aberto", widthDp = 390)
@Composable
private fun PaymentNothingPreview() = SaqzTheme {
    PaymentScreen(PaymentState(groupName = "Vôlei do CERET", loading = false, nothingOpen = true), {}, {})
}

@Preview(name = "Pix ainda não pronto", widthDp = 390)
@Composable
private fun PaymentPixNotReadyPreview() = SaqzTheme {
    PaymentScreen(previewPix.copy(pixAvailable = false, tab = PayTab.CARD, error = PaymentError.PIX_NOT_READY), {}, {})
}
