package br.com.saqz.receivables.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.resources.action_close
import br.com.saqz.designsystem.resources.saqz_volleyball
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.PaymentError
import br.com.saqz.receivables.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import br.com.saqz.designsystem.resources.Res as DesignRes

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
    const val Close = "receivable-payment-close"
    const val Offer = "receivable-payment-offer"
    const val AcceptOffer = "receivable-payment-offer-accept"
    const val DeclineOffer = "receivable-payment-offer-decline"
    const val Done = "receivable-payment-done"
    const val UpdateCard = "receivable-payment-update-card"
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
    PaymentScreen(state, viewModel::onIntent, onClose,
        onUpdateCard = if (route.offerCardUpdate) onOpenAutopay else null)
}

@Composable
fun PaymentScreen(state: PaymentState, onIntent: (PaymentIntent) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier,
    onUpdateCard: (() -> Unit)? = null) {
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
                    onUpdateCard?.let {
                        SaqzButton(stringResource(Res.string.refused_update_card), it, fullWidth = true,
                            variant = SaqzButtonVariant.Secondary, modifier = Modifier.testTag(PaymentScreenTags.UpdateCard))
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

/**
 * Proposta A do canvas do financeiro (escolhida pelo dono em 29/10, DEB-149): a confirmação é a faixa verde de borda a
 * borda, desde o topo da janela, com a bola da marca ao fundo. Sai a barra cinza com o título repetido (fica só o X) e a
 * oferta do automático vira cartão branco, para o azul não disputar com o verde.
 */
@Composable
private fun ConfirmedScreen(confirmed: PaymentConfirmedUi, onIntent: (PaymentIntent) -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(modifier.fillMaxSize().background(colors.surface).testTag(PaymentScreenTags.Confirmed)) {
        // O app é edge-to-edge: o verde passa por trás da barra de status e fica parado quando o conteúdo rola.
        Spacer(Modifier.fillMaxWidth().background(colors.successForeground).windowInsetsTopHeight(WindowInsets.statusBars))
        Column(Modifier.weight(1f).clipToBounds().verticalScroll(rememberScrollState())) {
            ConfirmedHero(confirmed.summary, onClose)
            if (confirmed.offer) AutopayOffer(confirmed.dueDay, onIntent,
                Modifier.padding(horizontal = metrics.horizontalPadding).padding(top = metrics.sectionGap - metrics.subGrid))
        }
        Box(Modifier.fillMaxWidth().padding(metrics.horizontalPadding)) {
            SaqzButton(stringResource(Res.string.confirmed_back), onClose, fullWidth = true,
                variant = if (confirmed.offer) SaqzButtonVariant.Secondary else SaqzButtonVariant.Primary,
                modifier = Modifier.testTag(PaymentScreenTags.Done))
        }
    }
}

@Composable
private fun ConfirmedHero(summary: String, onClose: () -> Unit) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val soft = colors.onPrimary.copy(alpha = HERO_SUPPORT_ALPHA)
    Box(Modifier.fillMaxWidth().background(colors.successForeground).clipToBounds()) {
        // Numa caixa do tamanho da faixa, como no SaqzHeroCard: a bola de 240 não dita a altura.
        Box(Modifier.matchParentSize()) {
            Image(painterResource(DesignRes.drawable.saqz_volleyball), contentDescription = null,
                colorFilter = ColorFilter.tint(colors.onPrimary),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = ConfirmedBallOffsetX, y = ConfirmedBallOffsetY)
                    .wrapContentSize(Alignment.TopEnd, unbounded = true).size(ConfirmedBallSize)
                    .alpha(CONFIRMED_BALL_ALPHA).clearAndSetSemantics {})
        }
        Column(Modifier.fillMaxWidth().padding(bottom = metrics.sectionGap)) {
            SaqzIconButton(onClick = onClose, contentDescription = stringResource(DesignRes.string.action_close),
                modifier = Modifier.padding(start = metrics.subGrid, top = metrics.subGrid).testTag(PaymentScreenTags.Close)) {
                SaqzIcon(SaqzIcons.Close, tint = colors.onPrimary, size = ConfirmedCloseIconSize)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = ConfirmedHeroPadding)
                .padding(top = metrics.sectionGap - metrics.subGrid), verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
                Box(Modifier.size(ConfirmedCheckSize).background(colors.onPrimary, CircleShape), contentAlignment = Alignment.Center) {
                    SaqzIcon(SaqzIcons.Check, tint = colors.successForeground, size = ConfirmedCheckIconSize)
                }
                Text(stringResource(Res.string.confirmed_title), style = SaqzTheme.typography.display, color = colors.onPrimary,
                    modifier = Modifier.semantics { heading() })
                Text(summary, style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = colors.onPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.grid), verticalAlignment = Alignment.CenterVertically) {
                    SaqzIcon(SaqzIcons.Wallet, tint = soft, size = ConfirmedNoteIconSize)
                    Text(stringResource(Res.string.confirmed_body), style = SaqzTheme.typography.support, color = soft)
                }
            }
        }
    }
}

@Composable
private fun AutopayOffer(dueDay: String?, onIntent: (PaymentIntent) -> Unit, modifier: Modifier = Modifier) =
    SaqzCard(modifier.testTag(PaymentScreenTags.Offer)) {
        val colors = SaqzTheme.colors
        Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid), verticalAlignment = Alignment.CenterVertically) {
            SaqzIcon(SaqzIcons.CreditCard, tint = colors.primary, size = OfferKickerIconSize)
            Text(stringResource(Res.string.autopay_kicker).uppercase(), style = SaqzTheme.typography.eyebrow, color = colors.primary)
        }
        Text(stringResource(Res.string.confirmed_offer_title),
            style = SaqzTheme.typography.display.copy(fontSize = OfferTitleSize, lineHeight = OfferTitleLineHeight),
            color = colors.textPrimary)
        Text(dueDay?.let { stringResource(Res.string.confirmed_offer_body, it) }
            ?: stringResource(Res.string.confirmed_offer_body_no_day), style = SaqzTheme.typography.support,
            color = colors.textSecondary)
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            SaqzButton(stringResource(Res.string.confirmed_offer_accept), { onIntent(PaymentIntent.AcceptOffer) }, fullWidth = true,
                modifier = Modifier.testTag(PaymentScreenTags.AcceptOffer))
            SaqzButton(stringResource(Res.string.confirmed_offer_decline), { onIntent(PaymentIntent.DeclineOffer) },
                variant = SaqzButtonVariant.Ghost, fullWidth = true, modifier = Modifier.testTag(PaymentScreenTags.DeclineOffer))
        }
    }

// Medidas da proposta A do canvas: bola de 240 deslocada para a direita e para baixo, check de 30 num círculo de 56.
private val ConfirmedHeroPadding = 20.dp
private val ConfirmedBallSize = 240.dp
private val ConfirmedBallOffsetX = 70.dp
private val ConfirmedBallOffsetY = 46.dp
private const val CONFIRMED_BALL_ALPHA = 0.20f
private val ConfirmedCloseIconSize = 24.dp
private val ConfirmedCheckSize = 56.dp
private val ConfirmedCheckIconSize = 30.dp
private val ConfirmedNoteIconSize = 18.dp
private val OfferKickerIconSize = 16.dp
private val OfferTitleSize = 24.sp
private val OfferTitleLineHeight = 28.sp

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

@Preview(name = "Pagamento confirmado · diária", widthDp = 390)
@Composable
private fun PaymentConfirmedGamePreview() = SaqzTheme {
    PaymentScreen(previewPix.copy(confirmed = PaymentConfirmedUi("R$ 20,00 · diária de terça, 27/10", false, null)), {}, {})
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
