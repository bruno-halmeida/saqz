package br.com.saqz.receivables.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

object AthleteChargesTags {
    const val Screen = "athlete-charges"
    const val PayAll = "athlete-charges-pay-all"
    const val Nothing = "athlete-charges-nothing"
    const val AutopayOffer = "athlete-charges-autopay-offer"
    const val AutopayManage = "athlete-charges-autopay-manage"
    const val Refused = "athlete-charges-refused"
    fun pay(chargeId: String) = "athlete-charges-pay-$chargeId"
    fun paid(chargeId: String) = "athlete-charges-paid-$chargeId"
}

@Composable
fun AthleteChargesRoot(
    route: AthleteChargesRoute,
    onBack: () -> Unit,
    onOpenPayment: (List<String>) -> Unit,
    onOpenAutopay: () -> Unit,
    viewModel: AthleteChargesViewModel = koinViewModel(key = "charges/${route.groupId}",
        parameters = { parametersOf(GroupArgs(route.groupId, route.groupName)) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is AthleteChargesEffect.OpenPayment -> onOpenPayment(effect.chargeIds)
            AthleteChargesEffect.OpenAutopay -> onOpenAutopay()
        }
    }
    // Volta do pagamento ou do automático: o que foi pago sai da lista.
    RefreshWhenVisible { viewModel.onIntent(AthleteChargesIntent.Appeared) }
    AthleteChargesScreen(state, viewModel::onIntent, onBack)
}

@Composable
fun AthleteChargesScreen(state: AthleteChargesState, onIntent: (AthleteChargesIntent) -> Unit, onBack: () -> Unit,
    modifier: Modifier = Modifier) = ReceivablesPage(stringResource(Res.string.charges_title), AthleteChargesTags.Screen,
    onBack, modifier) {
    val metrics = SaqzTheme.metrics
    when {
        state.loading -> Box(Modifier.fillMaxWidth().padding(metrics.sectionGap)) {
            SaqzSpinner(Modifier.align(androidx.compose.ui.Alignment.Center))
        }
        state.error != null -> PaymentFailure(state.error, { onIntent(AthleteChargesIntent.Refresh) },
            Modifier.padding(metrics.horizontalPadding))
        !state.active -> ReceivablesNote(stringResource(Res.string.charges_inactive))
        else -> {
            (state.autopay as? AutopayCardUi.Refused)?.let { RefusedCard(it, onIntent) }
            if (state.hasOpen && state.autopay !is AutopayCardUi.Refused) ChargesHero(state, onIntent)
            if (!state.hasOpen) SaqzEmptyState(title = stringResource(Res.string.charges_nothing_open),
                modifier = Modifier.padding(metrics.horizontalPadding).testTag(AthleteChargesTags.Nothing))
            if (state.hasOpen && state.monthlyKicker == null) {
                ReceivablesBand(stringResource(Res.string.charges_open))
                state.open.forEach { charge ->
                    ReceivablesRow(charge.title, charge.support, supportWarning = charge.overdue) {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
                            Text(charge.amountLabel, style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                                color = SaqzTheme.colors.textPrimary)
                            SaqzButton(stringResource(Res.string.charges_pay), { onIntent(AthleteChargesIntent.Pay(charge.chargeId)) },
                                size = SaqzButtonSize.Sm, variant = SaqzButtonVariant.Secondary,
                                modifier = Modifier.testTag(AthleteChargesTags.pay(charge.chargeId)))
                        }
                    }
                    SaqzDivider()
                }
            }
            when (val autopay = state.autopay) {
                is AutopayCardUi.Offer -> AutopayOffer(autopay, onIntent)
                is AutopayCardUi.Active -> AutopayActive(autopay.nextChargeLabel, onIntent)
                is AutopayCardUi.Refused -> AutopayActive(autopay.nextChargeLabel, onIntent)
                AutopayCardUi.Authorizing -> {
                    ReceivablesBand(stringResource(Res.string.autopay_kicker))
                    ReceivablesRow(stringResource(Res.string.autopay_authorizing), null)
                }
                null -> Unit
            }
            if (state.history.isNotEmpty() || state.historyFailed) {
                ReceivablesBand(stringResource(Res.string.charges_history))
                if (state.historyFailed) ReceivablesNote(stringResource(Res.string.charges_history_failed))
                state.history.forEach { paid ->
                    ReceivablesRow(paid.title, paid.support, Modifier.testTag(AthleteChargesTags.paid(paid.chargeId))) {
                        SaqzStatusChip(stringResource(Res.string.charges_paid), tone = SaqzChipTone.Success)
                    }
                    SaqzDivider()
                }
            }
            ReceivablesNote(stringResource(Res.string.charges_outside_note))
        }
    }
}

@Composable
private fun ChargesHero(state: AthleteChargesState, onIntent: (AthleteChargesIntent) -> Unit) {
    val monthly = state.monthlyKicker != null
    ReceivablesHero(
        kicker = if (monthly) stringResource(Res.string.charges_kicker_monthly, state.groupName, state.monthlyKicker!!)
        else stringResource(Res.string.charges_kicker_open, state.groupName),
        amount = state.totalLabel,
        support = state.summaryLabel,
        detail = state.overdueLabel,
    ) {
        SaqzButton(stringResource(if (monthly) Res.string.charges_pay_monthly else Res.string.charges_pay_all),
            { onIntent(AthleteChargesIntent.PayAll) }, variant = SaqzButtonVariant.Inverse, fullWidth = true,
            modifier = Modifier.testTag(AthleteChargesTags.PayAll))
        if (!monthly) Text(stringResource(Res.string.charges_pay_note), Modifier.fillMaxWidth(),
            style = SaqzTheme.typography.caption, color = SaqzTheme.colors.onPrimary.copy(alpha = 0.82f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun RefusedCard(refused: AutopayCardUi.Refused, onIntent: (AthleteChargesIntent) -> Unit) {
    ReceivablesHero(kicker = stringResource(Res.string.autopay_kicker), amount = stringResource(Res.string.refused_title),
        support = stringResource(Res.string.refused_body, refused.monthLabel, refused.amountLabel),
        modifier = Modifier.testTag(AthleteChargesTags.Refused)) {
        SaqzButton(stringResource(Res.string.refused_pay_pix), { onIntent(AthleteChargesIntent.Pay(refused.chargeId)) },
            variant = SaqzButtonVariant.Inverse, fullWidth = true)
        SaqzButton(stringResource(Res.string.refused_update_card), { onIntent(AthleteChargesIntent.OpenAutopay) },
            variant = SaqzButtonVariant.Ghost, contentColor = SaqzTheme.colors.onPrimary,
            borderColor = SaqzTheme.colors.onPrimary, fullWidth = true)
    }
}

@Composable
private fun AutopayOffer(offer: AutopayCardUi.Offer, onIntent: (AthleteChargesIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    ReceivablesBand(stringResource(Res.string.autopay_kicker))
    Column(Modifier.fillMaxWidth().padding(metrics.horizontalPadding).testTag(AthleteChargesTags.AutopayOffer),
        verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
        Text(stringResource(Res.string.autopay_offer_title), style = SaqzTheme.typography.title, color = SaqzTheme.colors.textPrimary)
        Text(offer.dueDay?.let { stringResource(Res.string.autopay_offer_body, it) }
            ?: stringResource(Res.string.autopay_offer_body_no_day), style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary)
        SaqzButton(stringResource(Res.string.autopay_offer_action), { onIntent(AthleteChargesIntent.OpenAutopay) },
            fullWidth = true)
        Text(stringResource(Res.string.autopay_offer_note), style = SaqzTheme.typography.caption,
            color = SaqzTheme.colors.textSecondary)
    }
}

@Composable
private fun AutopayActive(next: String?, onIntent: (AthleteChargesIntent) -> Unit) {
    ReceivablesBand(stringResource(Res.string.autopay_kicker))
    ReceivablesRow(stringResource(Res.string.autopay_active_title),
        next?.let { stringResource(Res.string.autopay_active_next, it) }) {
        SaqzButton(stringResource(Res.string.autopay_manage), { onIntent(AthleteChargesIntent.OpenAutopay) },
            size = SaqzButtonSize.Sm, variant = SaqzButtonVariant.Secondary,
            modifier = Modifier.testTag(AthleteChargesTags.AutopayManage))
    }
}

internal fun previewAthleteCharges() = AthleteChargesState(
    groupName = "Vôlei do CERET", loading = false, totalLabel = "R$ 60,00", summaryLabel = "3 cobranças",
    overdueLabel = "1 vencida",
    open = listOf(
        OpenChargeUi("c1", "Jogo de terça · 21/07", "Venceu em 21/07", "R$ 20,00", true),
        OpenChargeUi("c2", "Jogo de terça · 28/07", "Vence em 28/07", "R$ 20,00", false),
        OpenChargeUi("c3", "Convidado: Rafa", "Jogo de terça · 28/07", "R$ 20,00", false),
    ),
    history = listOf(
        PaidChargeUi("p1", "Jogo de quinta · 16/07", "R$ 20,00 · pelo app, no Pix"),
        PaidChargeUi("p2", "Jogo de terça · 14/07", "R$ 20,00 · em dinheiro"),
    ),
)

@Preview(name = "Minhas cobranças · avulso", widthDp = 390)
@Composable
private fun AthleteChargesPreview() = SaqzTheme { AthleteChargesScreen(previewAthleteCharges(), {}, {}) }

@Preview(name = "Minhas cobranças · mensalista", widthDp = 390)
@Composable
private fun AthleteChargesMonthlyPreview() = SaqzTheme {
    AthleteChargesScreen(AthleteChargesState(groupName = "Vôlei do CERET", loading = false, totalLabel = "R$ 100,00",
        summaryLabel = "Vence em 10/08", monthlyKicker = "mensalidade de agosto",
        open = listOf(OpenChargeUi("m8", "Mensalidade · agosto", "Vence em 10/08", "R$ 100,00", false)),
        history = listOf(PaidChargeUi("m7", "Mensalidade · julho", "R$ 100,00 · pelo app, no Pix"),
            PaidChargeUi("m6", "Mensalidade · junho", "R$ 100,00 · Pix do organizador")),
        autopay = AutopayCardUi.Offer("10")), {}, {})
}

@Preview(name = "Minhas cobranças · cartão recusado", widthDp = 390)
@Composable
private fun AthleteChargesRefusedPreview() = SaqzTheme {
    AthleteChargesScreen(AthleteChargesState(groupName = "Vôlei do CERET", loading = false, totalLabel = "R$ 100,00",
        summaryLabel = "1 cobrança",
        open = listOf(OpenChargeUi("m9", "Mensalidade · setembro", "Venceu em 10/09 · cartão recusado", "R$ 100,00", true)),
        autopay = AutopayCardUi.Refused("m9", "setembro", "R$ 100,00", "10/10")), {}, {})
}
