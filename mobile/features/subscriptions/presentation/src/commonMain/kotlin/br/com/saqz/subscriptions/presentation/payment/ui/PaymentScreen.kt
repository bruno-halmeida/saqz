package br.com.saqz.subscriptions.presentation.payment.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.CardExpiryVisualTransformation
import br.com.saqz.designsystem.CardNumberVisualTransformation
import br.com.saqz.designsystem.CepVisualTransformation
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.CpfVisualTransformation
import br.com.saqz.designsystem.PhoneVisualTransformation
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzForm
import br.com.saqz.designsystem.SaqzFormScope
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute
import br.com.saqz.subscriptions.presentation.payment.CardFormError
import br.com.saqz.subscriptions.presentation.payment.CardFormState
import br.com.saqz.subscriptions.presentation.payment.PaymentEffect
import br.com.saqz.subscriptions.presentation.payment.PaymentIntent
import br.com.saqz.subscriptions.presentation.payment.PaymentState
import br.com.saqz.subscriptions.presentation.payment.PaymentViewModel
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.card_capture_address_complement_label
import br.com.saqz.subscriptions.resources.card_capture_address_number_error
import br.com.saqz.subscriptions.resources.card_capture_address_number_label
import br.com.saqz.subscriptions.resources.card_capture_cvv_error
import br.com.saqz.subscriptions.resources.card_capture_cvv_label
import br.com.saqz.subscriptions.resources.card_capture_expiry_error
import br.com.saqz.subscriptions.resources.card_capture_expiry_label
import br.com.saqz.subscriptions.resources.card_capture_holder_name_error
import br.com.saqz.subscriptions.resources.card_capture_holder_name_label
import br.com.saqz.subscriptions.resources.card_capture_mobile_phone_label
import br.com.saqz.subscriptions.resources.card_capture_number_error
import br.com.saqz.subscriptions.resources.card_capture_number_label
import br.com.saqz.subscriptions.resources.card_capture_phone_error
import br.com.saqz.subscriptions.resources.card_capture_phone_label
import br.com.saqz.subscriptions.resources.card_capture_postal_code_error
import br.com.saqz.subscriptions.resources.card_capture_postal_code_label
import br.com.saqz.subscriptions.resources.card_capture_section_title
import br.com.saqz.subscriptions.resources.payment_back_confirm_body
import br.com.saqz.subscriptions.resources.payment_back_confirm_leave
import br.com.saqz.subscriptions.resources.payment_back_confirm_stay
import br.com.saqz.subscriptions.resources.payment_back_confirm_title
import br.com.saqz.subscriptions.resources.payment_billing_card
import br.com.saqz.subscriptions.resources.payment_billing_pix
import br.com.saqz.subscriptions.resources.payment_card_copy_action
import br.com.saqz.subscriptions.resources.payment_card_title
import br.com.saqz.subscriptions.resources.payment_confirm_action
import br.com.saqz.subscriptions.resources.payment_cpf_cnpj_label
import br.com.saqz.subscriptions.resources.payment_error_conflict_pending_checkout
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
    const val BackConfirmationSheet = "payment-back-confirmation-sheet"
    const val CardNumber = "payment-card-number"
    const val CardExpiry = "payment-card-expiry"
    const val CardCvv = "payment-card-cvv"
    const val CardHolderName = "payment-card-holder-name"
    const val CardPostalCode = "payment-card-postal-code"
    const val CardAddressNumber = "payment-card-address-number"
    const val CardAddressComplement = "payment-card-address-complement"
    const val CardPhone = "payment-card-phone"
    const val CardMobilePhone = "payment-card-mobile-phone"
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PaymentScreen(
    state: PaymentState,
    onIntent: (PaymentIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    // Achado do Codex no PR #100: a seta do topo só cobria o toque — o back do
    // sistema/gesto ia direto pro `NavDisplay.onBack = pop` sem passar pela confirmação.
    // `!isBackConfirmationOpen` evita brigar com o `BackHandler` interno do `SaqzBottomSheet`
    // (que já fecha o sheet e é mais interno na composição, então ganharia de qualquer jeito).
    BackHandler(enabled = state.hasCheckout && !state.isBackConfirmationOpen) {
        onIntent(PaymentIntent.RequestBack)
    }
    Box(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SaqzTopAppBar(
                title = stringResource(Res.string.payment_title),
                // Checkout pendente por trás: sair sem avisar é o que abre espaço para o
                // conflito do VUL-119 (escolher outro plano com este ainda em aberto).
                onBack = { if (state.hasCheckout) onIntent(PaymentIntent.RequestBack) else onBack() },
            )
            SaqzForm(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                itemSpacing = metrics.blockGap,
            ) {
                PaymentSummaryCard(state)
                if (state.hasCheckout) {
                    PaymentCheckoutSection(state, onIntent)
                } else {
                    PaymentFormSection(state, onIntent)
                }
            }
        }
        PaymentBackConfirmationSheet(state = state, onIntent = onIntent, onConfirmBack = onBack)
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
private fun SaqzFormScope.PaymentFormSection(state: PaymentState, onIntent: (PaymentIntent) -> Unit) {
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
            visualTransformation = CpfVisualTransformation(),
            ime = if (state.billingType == BillingType.CreditCard) imeNext() else imeDone(),
            errorText = state.cpfCnpjError?.asString(),
            invalid = state.cpfCnpjError != null,
            modifier = Modifier.testTag(PaymentTags.CpfCnpj),
        )
        if (state.billingType == BillingType.CreditCard) {
            CardCaptureSection(state.cardForm, onIntent)
        }
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

/**
 * Número/validade/CVV/nome do cartão + CEP/número/telefone do portador (VUL-196).
 * `creditCardHolderInfo.name`/`email`/`cpfCnpj` reaproveitam sessão + CPF/CNPJ de cima —
 * ver doc de [CardFormState], por isso não têm campo próprio aqui.
 */
@Composable
private fun SaqzFormScope.CardCaptureSection(form: CardFormState, onIntent: (PaymentIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
        Text(
            stringResource(Res.string.card_capture_section_title),
            style = SaqzTheme.typography.label,
            color = SaqzTheme.colors.textPrimary,
        )
        SaqzInput(
            value = form.number,
            onValueChange = { onIntent(PaymentIntent.UpdateCardNumber(it)) },
            label = stringResource(Res.string.card_capture_number_label),
            keyboardType = KeyboardType.Number,
            visualTransformation = CardNumberVisualTransformation(),
            ime = imeNext(),
            errorText = if (CardFormError.NumberInvalid in form.errors) {
                stringResource(Res.string.card_capture_number_error)
            } else {
                null
            },
            invalid = CardFormError.NumberInvalid in form.errors,
            modifier = Modifier.testTag(PaymentTags.CardNumber),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            SaqzInput(
                value = form.expiry,
                onValueChange = { onIntent(PaymentIntent.UpdateCardExpiry(it)) },
                label = stringResource(Res.string.card_capture_expiry_label),
                keyboardType = KeyboardType.Number,
                visualTransformation = CardExpiryVisualTransformation(),
                ime = imeNext(),
                errorText = if (CardFormError.ExpiryInvalid in form.errors) {
                    stringResource(Res.string.card_capture_expiry_error)
                } else {
                    null
                },
                invalid = CardFormError.ExpiryInvalid in form.errors,
                modifier = Modifier.weight(1f).testTag(PaymentTags.CardExpiry),
            )
            // CVV: `Password` + `revealable = false` mascara sem oferecer olho de mostrar
            // (PCI, VUL-196) — o mesmo mecanismo do "confirmar nova senha" do 1g.
            SaqzInput(
                value = form.cvv,
                onValueChange = { onIntent(PaymentIntent.UpdateCardCvv(it)) },
                label = stringResource(Res.string.card_capture_cvv_label),
                kind = SaqzInputKind.Password,
                revealable = false,
                keyboardType = KeyboardType.NumberPassword,
                ime = imeNext(),
                errorText = if (CardFormError.CvvInvalid in form.errors) {
                    stringResource(Res.string.card_capture_cvv_error)
                } else {
                    null
                },
                invalid = CardFormError.CvvInvalid in form.errors,
                modifier = Modifier.weight(1f).testTag(PaymentTags.CardCvv),
            )
        }
        SaqzInput(
            value = form.holderName,
            onValueChange = { onIntent(PaymentIntent.UpdateCardHolderName(it)) },
            label = stringResource(Res.string.card_capture_holder_name_label),
            ime = imeNext(),
            errorText = if (CardFormError.HolderNameRequired in form.errors) {
                stringResource(Res.string.card_capture_holder_name_error)
            } else {
                null
            },
            invalid = CardFormError.HolderNameRequired in form.errors,
            modifier = Modifier.testTag(PaymentTags.CardHolderName),
        )
        SaqzInput(
            value = form.postalCode,
            onValueChange = { onIntent(PaymentIntent.UpdateCardPostalCode(it)) },
            label = stringResource(Res.string.card_capture_postal_code_label),
            keyboardType = KeyboardType.Number,
            visualTransformation = CepVisualTransformation(),
            ime = imeNext(),
            errorText = if (CardFormError.PostalCodeInvalid in form.errors) {
                stringResource(Res.string.card_capture_postal_code_error)
            } else {
                null
            },
            invalid = CardFormError.PostalCodeInvalid in form.errors,
            modifier = Modifier.testTag(PaymentTags.CardPostalCode),
        )
        SaqzInput(
            value = form.addressNumber,
            onValueChange = { onIntent(PaymentIntent.UpdateCardAddressNumber(it)) },
            label = stringResource(Res.string.card_capture_address_number_label),
            keyboardType = KeyboardType.Number,
            ime = imeNext(),
            errorText = if (CardFormError.AddressNumberRequired in form.errors) {
                stringResource(Res.string.card_capture_address_number_error)
            } else {
                null
            },
            invalid = CardFormError.AddressNumberRequired in form.errors,
            modifier = Modifier.testTag(PaymentTags.CardAddressNumber),
        )
        SaqzInput(
            value = form.addressComplement,
            onValueChange = { onIntent(PaymentIntent.UpdateCardAddressComplement(it)) },
            label = stringResource(Res.string.card_capture_address_complement_label),
            ime = imeNext(),
            modifier = Modifier.testTag(PaymentTags.CardAddressComplement),
        )
        SaqzInput(
            value = form.phone,
            onValueChange = { onIntent(PaymentIntent.UpdateCardPhone(it)) },
            label = stringResource(Res.string.card_capture_phone_label),
            kind = SaqzInputKind.Phone,
            visualTransformation = PhoneVisualTransformation(),
            ime = imeNext(),
            errorText = if (CardFormError.PhoneInvalid in form.errors) {
                stringResource(Res.string.card_capture_phone_error)
            } else {
                null
            },
            invalid = CardFormError.PhoneInvalid in form.errors,
            modifier = Modifier.testTag(PaymentTags.CardPhone),
        )
        SaqzInput(
            value = form.mobilePhone,
            onValueChange = { onIntent(PaymentIntent.UpdateCardMobilePhone(it)) },
            label = stringResource(Res.string.card_capture_mobile_phone_label),
            kind = SaqzInputKind.Phone,
            visualTransformation = PhoneVisualTransformation(),
            ime = imeDone(),
            modifier = Modifier.testTag(PaymentTags.CardMobilePhone),
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

        // Só o RegeneratePix chega aqui com um `submitError` — o Submit original vive na
        // seção de formulário, que some assim que `hasCheckout` liga (ver PaymentScreen).
        state.submitError?.let {
            Text(it.asString(), style = typography.support, color = colors.errorForeground)
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

@Composable
private fun PaymentBackConfirmationSheet(
    state: PaymentState,
    onIntent: (PaymentIntent) -> Unit,
    onConfirmBack: () -> Unit,
) {
    SaqzBottomSheet(
        open = state.isBackConfirmationOpen,
        onClose = { onIntent(PaymentIntent.DismissBackConfirmation) },
        modifier = Modifier.testTag(PaymentTags.BackConfirmationSheet),
        title = stringResource(Res.string.payment_back_confirm_title),
        description = stringResource(Res.string.payment_back_confirm_body),
        splitFooter = {
            SaqzButton(
                label = stringResource(Res.string.payment_back_confirm_stay),
                onClick = { onIntent(PaymentIntent.DismissBackConfirmation) },
                variant = SaqzButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            SaqzButton(
                label = stringResource(Res.string.payment_back_confirm_leave),
                onClick = onConfirmBack,
                variant = SaqzButtonVariant.Danger,
                modifier = Modifier.weight(1f),
            )
        },
        content = {},
    )
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
private fun PaymentCardFormPreview() = SaqzTheme {
    PaymentScreen(
        state = PreviewFormState.copy(
            billingType = BillingType.CreditCard,
            cardForm = CardFormState(
                number = "4111111111111111",
                expiry = "1228",
                cvv = "123",
                holderName = "Ana Silva",
                postalCode = "01310100",
                addressNumber = "1000",
                phone = "11999990000",
            ),
        ),
        onIntent = {},
        onBack = {},
    )
}

// VUL-196 — recusa do Asaas (402 card_declined): mensagem em PT-BR, dados do portador intactos.
@Preview
@Composable
private fun PaymentCardDeclinedPreview() = SaqzTheme {
    PaymentScreen(
        state = PreviewFormState.copy(
            billingType = BillingType.CreditCard,
            cardForm = CardFormState(
                number = "4111111111111111",
                expiry = "1228",
                cvv = "123",
                holderName = "Ana Silva",
                postalCode = "01310100",
                addressNumber = "1000",
                phone = "11999990000",
            ),
            submitError = UiText.Raw("Cartão recusado pela operadora. Tente outro cartão."),
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

// VUL-119 — RegeneratePix batendo num checkout pendente desta mesma sessão.
@Preview
@Composable
private fun PaymentConflictPendingCheckoutPreview() = SaqzTheme {
    PaymentScreen(
        state = PreviewFormState.copy(
            pixCopyPaste = "00020126580014BR.GOV.BCB.PIX0136chave-fake-1234",
            isWaitingConfirmation = true,
            submitError = UiText.Res(Res.string.payment_error_conflict_pending_checkout),
        ),
        onIntent = {},
        onBack = {},
    )
}

// VUL-119 — seta do topo com checkout pendente: confirma antes de sair.
@Preview
@Composable
private fun PaymentBackConfirmationPreview() = SaqzTheme {
    PaymentScreen(
        state = PreviewFormState.copy(
            pixCopyPaste = "00020126580014BR.GOV.BCB.PIX0136chave-fake-1234",
            isWaitingConfirmation = true,
            isBackConfirmationOpen = true,
        ),
        onIntent = {},
        onBack = {},
    )
}
