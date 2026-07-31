package br.com.saqz.subscriptions.presentation.payment.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute
import br.com.saqz.subscriptions.presentation.payment.PaymentEffect
import br.com.saqz.subscriptions.presentation.payment.PaymentIntent
import br.com.saqz.subscriptions.presentation.payment.PaymentState
import br.com.saqz.subscriptions.presentation.payment.PaymentViewModel
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.payment_billing_card
import br.com.saqz.subscriptions.resources.payment_billing_pix
import br.com.saqz.subscriptions.resources.payment_card_copy_action
import br.com.saqz.subscriptions.resources.payment_card_title
import br.com.saqz.subscriptions.resources.payment_confirm_action
import br.com.saqz.subscriptions.resources.payment_cpf_cnpj_label
import br.com.saqz.subscriptions.resources.payment_pix_copy_action
import br.com.saqz.subscriptions.resources.payment_pix_regenerate_action
import br.com.saqz.subscriptions.resources.payment_pix_title
import br.com.saqz.subscriptions.resources.payment_submit_action
import br.com.saqz.subscriptions.resources.payment_submit_loading
import br.com.saqz.subscriptions.resources.payment_summary_coupon
import br.com.saqz.subscriptions.resources.payment_title
import br.com.saqz.subscriptions.resources.payment_waiting_body
import br.com.saqz.subscriptions.resources.payment_waiting_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

internal object PaymentTags {
    const val CpfCnpj = "payment-cpf-cnpj"
    const val Submit = "payment-submit"
    const val PixCode = "payment-pix-code"
    const val PixCopy = "payment-pix-copy"
    const val PixRegenerate = "payment-pix-regenerate"
    const val CardCopy = "payment-card-copy"
    const val Confirm = "payment-confirm"
}

/**
 * A navegação sobe por [onEffect] — quem liga o `NavDisplay` a esta rota é o VUL-113
 * (mobile/AGENTS.md §6), não esta tela.
 */
@Composable
fun PaymentRoot(
    route: SubscriptionsRoute.Payment,
    onBack: () -> Unit,
    onEffect: (PaymentEffect) -> Unit,
    viewModel: PaymentViewModel = koinViewModel(parameters = { parametersOf(route) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects, onEvent = onEffect)
    PaymentScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@Composable
fun PaymentScreen(
    state: PaymentState,
    onIntent: (PaymentIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Box(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SaqzTopAppBar(title = stringResource(Res.string.payment_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.grid),
                verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
            ) {
                PaymentSummaryCard(state)
                if (state.hasCheckout) {
                    PaymentCheckoutSection(state, onIntent)
                } else {
                    PaymentFormSection(state, onIntent)
                }
            }
        }
    }
}

@Composable
private fun PaymentSummaryCard(state: PaymentState) {
    val colors = SaqzTheme.colors
    val typography = SaqzTheme.typography
    SaqzCard {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(state.planName.ifBlank { state.plan.name }, style = typography.title, color = colors.textPrimary)
            Text(state.cycle.label(), style = typography.support, color = colors.textSecondary)
            state.priceCents?.let { cents ->
                Text(formatBrl(cents), style = typography.headline, color = colors.textPrimary)
            }
            if (state.couponCode != null && state.discountPercent != null) {
                Text(
                    stringResource(Res.string.payment_summary_coupon, state.couponCode, state.discountPercent),
                    style = typography.support,
                    color = colors.success,
                )
            }
        }
    }
}

@Composable
private fun PaymentFormSection(state: PaymentState, onIntent: (PaymentIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    val options = listOf(stringResource(Res.string.payment_billing_pix), stringResource(Res.string.payment_billing_card))
    Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
        SaqzSegmented(
            options = options,
            selected = if (state.billingType == BillingType.Pix) 0 else 1,
            onSelect = { index ->
                onIntent(PaymentIntent.SelectBillingType(if (index == 0) BillingType.Pix else BillingType.CreditCard))
            },
        )
        SaqzInput(
            value = state.cpfCnpj,
            onValueChange = { onIntent(PaymentIntent.UpdateCpfCnpj(it)) },
            label = stringResource(Res.string.payment_cpf_cnpj_label),
            kind = SaqzInputKind.Text,
            errorText = state.cpfCnpjError?.asString(),
            invalid = state.cpfCnpjError != null,
            modifier = Modifier.testTag(PaymentTags.CpfCnpj),
        )
        state.submitError?.let {
            Text(it.asString(), style = SaqzTheme.typography.support, color = SaqzTheme.colors.errorForeground)
        }
        SaqzButton(
            label = stringResource(if (state.isSubmitting) Res.string.payment_submit_loading else Res.string.payment_submit_action),
            onClick = { onIntent(PaymentIntent.Submit) },
            loading = state.isSubmitting,
            fullWidth = true,
            modifier = Modifier.testTag(PaymentTags.Submit),
        )
    }
}

@Composable
private fun PaymentCheckoutSection(state: PaymentState, onIntent: (PaymentIntent) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val colors = SaqzTheme.colors
    val typography = SaqzTheme.typography
    val metrics = SaqzTheme.metrics

    Column(verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        // O backend busca os dois campos independente do `billingType` escolhido (uma
        // cobrança Pix pode legitimamente ter `invoiceUrl` também preenchido) — o gate é
        // sempre pela escolha do usuário, nunca só por valor não-nulo, senão as duas seções
        // aparecem juntas e confundem qual forma de pagamento usar.
        state.pixCopyPaste?.takeIf { state.billingType == BillingType.Pix }?.let { code ->
            SaqzCard {
                Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
                    Text(stringResource(Res.string.payment_pix_title), style = typography.label, color = colors.textPrimary)
                    Text(
                        code,
                        style = typography.support,
                        color = colors.textSecondary,
                        modifier = Modifier.testTag(PaymentTags.PixCode),
                    )
                    SaqzButton(
                        label = stringResource(Res.string.payment_pix_copy_action),
                        onClick = { clipboard.setText(AnnotatedString(code)) },
                        variant = SaqzButtonVariant.Secondary,
                        fullWidth = true,
                        modifier = Modifier.testTag(PaymentTags.PixCopy),
                    )
                    SaqzButton(
                        label = stringResource(Res.string.payment_pix_regenerate_action),
                        onClick = { onIntent(PaymentIntent.RegeneratePix) },
                        variant = SaqzButtonVariant.Secondary,
                        fullWidth = true,
                        modifier = Modifier.testTag(PaymentTags.PixRegenerate),
                    )
                }
            }
        }

        state.invoiceUrl?.takeIf { state.billingType == BillingType.CreditCard }?.let { url ->
            SaqzCard {
                Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
                    Text(stringResource(Res.string.payment_card_title), style = typography.label, color = colors.textPrimary)
                    Text(url, style = typography.support, color = colors.textSecondary)
                    SaqzButton(
                        label = stringResource(Res.string.payment_card_copy_action),
                        onClick = { clipboard.setText(AnnotatedString(url)) },
                        variant = SaqzButtonVariant.Secondary,
                        fullWidth = true,
                        modifier = Modifier.testTag(PaymentTags.CardCopy),
                    )
                }
            }
        }

        if (state.isWaitingConfirmation) {
            SaqzCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
                ) {
                    SaqzSpinner()
                    Text(stringResource(Res.string.payment_waiting_title), style = typography.label, color = colors.textPrimary)
                    Text(stringResource(Res.string.payment_waiting_body), style = typography.support, color = colors.textSecondary)
                }
            }
        }

        SaqzButton(
            label = stringResource(Res.string.payment_confirm_action),
            onClick = { onIntent(PaymentIntent.ConfirmPayment) },
            loading = state.isCheckingNow,
            fullWidth = true,
            modifier = Modifier.testTag(PaymentTags.Confirm),
        )
    }
}

private fun SubscriptionCycle.label(): String = when (this) {
    SubscriptionCycle.Monthly -> "Mensal"
    SubscriptionCycle.Annual -> "Anual"
}

// --- Previews: um estado real por fase da tela -------------------------------------

private val PreviewFormState = PaymentState(
    plan = Plan.Organizador,
    cycle = SubscriptionCycle.Monthly,
    couponCode = "BEMVINDO10",
    planName = "Organizador",
    priceCents = 4_491L,
    discountPercent = 10,
)

@Preview
@Composable
private fun PaymentFormPreview() = SaqzTheme {
    PaymentScreen(state = PreviewFormState, onIntent = {}, onBack = {})
}

@Preview
@Composable
private fun PaymentWaitingPixPreview() = SaqzTheme {
    PaymentScreen(
        state = PreviewFormState.copy(
            pixCopyPaste = "00020126580014BR.GOV.BCB.PIX0136chave-fake-1234",
            isWaitingConfirmation = true,
        ),
        onIntent = {},
        onBack = {},
    )
}

@Preview
@Composable
private fun PaymentCardCheckoutPreview() = SaqzTheme {
    PaymentScreen(
        state = PreviewFormState.copy(
            billingType = BillingType.CreditCard,
            invoiceUrl = "https://checkout.asaas.com/i/abc123",
            isWaitingConfirmation = true,
        ),
        onIntent = {},
        onBack = {},
    )
}
