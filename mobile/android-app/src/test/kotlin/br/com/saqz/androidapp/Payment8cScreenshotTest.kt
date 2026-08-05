package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.presentation.payment.CardFormError
import br.com.saqz.subscriptions.presentation.payment.CardFormState
import br.com.saqz.subscriptions.presentation.payment.PaymentState
import br.com.saqz.subscriptions.presentation.payment.ui.PaymentScreen
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.payment_error_conflict_pending_checkout
import br.com.saqz.subscriptions.resources.payment_error_cpf_cnpj
import br.com.saqz.subscriptions.resources.payment_error_generic
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 8c — cada estado que `PaymentScreen` desenha, para o print obrigatório no corpo do PR
 * (AGENTS.md §"Prints no corpo do PR"). Arquivo próprio, não uma cena a mais no
 * `SaqzScreenshotTest` compartilhado — mesmo motivo do `Login1aScreenshotTest`.
 *
 * Gravar: `./gradlew :android-app:recordRoborazziDevDebug`. Saída em
 * `android-app/screenshots/`, ignorada pelo git — os PNGs vivem na branch órfã `screenshots`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class Payment8cScreenshotTest {

    private companion object {
        const val SHUTTER_MILLIS = 600L
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { SaqzTheme { content() } }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private fun screen(state: PaymentState) = capture(name(state)) {
        PaymentScreen(state = state, onIntent = {}, onBack = {})
    }

    private fun name(state: PaymentState) = when {
        state.cpfCnpjError != null -> "8c-4-cpf-cnpj-invalido"
        // VUL-196: recusa do cartão é distinta do erro genérico — mostra a mensagem PT-BR
        // do backend por cima do formulário já preenchido, não do card de erro solto.
        state.billingType == BillingType.CreditCard && state.submitError != null -> "8c-3d-cartao-recusado"
        state.submitError != null -> "8c-5-erro-generico"
        state.isSubmitting -> "8c-6-enviando"
        state.isCheckingNow -> "8c-9-confirmando"
        state.invoiceUrl != null -> "8c-8-cartao-aguardando"
        state.pixCopyPaste != null -> "8c-7-pix-aguardando"
        state.billingType == BillingType.CreditCard && state.cardForm.errors.isNotEmpty() -> "8c-3c-form-cartao-erros"
        state.billingType == BillingType.CreditCard && state.cardForm.number.isNotBlank() -> "8c-3b-form-cartao-preenchido"
        state.billingType == BillingType.CreditCard -> "8c-3-form-cartao"
        state.discountPercent != null -> "8c-2-form-pix-cupom"
        else -> "8c-1-form-pix"
    }

    private val base = PaymentState(
        plan = Plan.Organizador,
        cycle = SubscriptionCycle.Monthly,
        planName = "Organizador",
        priceCents = 4_990L,
        cpfCnpj = "12345678909",
    )

    // VUL-196 — cartão preenchido com dado realista, para conferir todas as máscaras juntas.
    private val filledCardForm = CardFormState(
        number = "4111111111111111",
        expiry = "1228",
        cvv = "123",
        holderName = "Ana Silva",
        postalCode = "01310100",
        addressNumber = "1000",
        phone = "11999990000",
    )

    // Pix escolhido (default), com CPF/CNPJ preenchido para conferir a máscara.
    @Test
    fun formPix() = screen(base)

    // Mesmo formulário, cupom aplicado no resumo — a linha de desconto é um elemento a mais.
    @Test
    fun formPixComCupom() = screen(
        base.copy(couponCode = "BEMVINDO10", discountPercent = 10, priceCents = 4_491L),
    )

    // Cartão selecionado, campos de captura ainda vazios (VUL-196).
    @Test
    fun formCartao() = screen(base.copy(billingType = BillingType.CreditCard))

    // Cartão preenchido — todas as máscaras (número, validade, CEP, telefone) juntas.
    @Test
    fun formCartaoPreenchido() = screen(
        base.copy(billingType = BillingType.CreditCard, cardForm = filledCardForm),
    )

    // Validação campo a campo (VUL-196): cada erro embaixo do campo dele, não um card genérico.
    @Test
    fun formCartaoComErros() = screen(
        base.copy(
            billingType = BillingType.CreditCard,
            cardForm = CardFormState(
                errors = setOf(
                    CardFormError.NumberInvalid,
                    CardFormError.ExpiryInvalid,
                    CardFormError.CvvInvalid,
                    CardFormError.HolderNameRequired,
                    CardFormError.PostalCodeInvalid,
                    CardFormError.AddressNumberRequired,
                    CardFormError.PhoneInvalid,
                ),
            ),
        ),
    )

    // 402 card_declined (VUL-194/VUL-196): mensagem PT-BR do backend, dados do portador intactos.
    @Test
    fun cartaoRecusado() = screen(
        base.copy(
            billingType = BillingType.CreditCard,
            cardForm = filledCardForm,
            submitError = UiText.Raw("Cartão recusado pela operadora. Tente outro cartão."),
        ),
    )

    // CPF/CNPJ com menos de 11 dígitos, erro embaixo do campo.
    @Test
    fun cpfCnpjInvalido() = screen(
        base.copy(cpfCnpj = "123", cpfCnpjError = UiText.Res(Res.string.payment_error_cpf_cnpj)),
    )

    // `create()` recusado — banner de erro genérico acima do botão.
    @Test
    fun erroGenerico() = screen(
        base.copy(submitError = UiText.Res(Res.string.payment_error_generic)),
    )

    // Botão "Pagar" em carregamento — rótulo muda para "Processando…".
    @Test
    fun enviando() = screen(base.copy(isSubmitting = true))

    // Checkout Pix criado: código copia-e-cola, copiar, regenerar e o card de espera.
    @Test
    fun pixAguardando() = screen(
        base.copy(
            pixCopyPaste = "00020126580014BR.GOV.BCB.PIX0136chave-fake-1234",
            isWaitingConfirmation = true,
        ),
    )

    // Checkout cartão criado: link do Asaas e o card de espera, sem botão de regenerar.
    @Test
    fun cartaoAguardando() = screen(
        base.copy(
            billingType = BillingType.CreditCard,
            invoiceUrl = "https://checkout.asaas.com/i/abc123",
            isWaitingConfirmation = true,
        ),
    )

    // "Já paguei · confirmar" em carregamento — checagem manual disparada pelo usuário.
    @Test
    fun confirmando() = screen(
        base.copy(
            pixCopyPaste = "00020126580014BR.GOV.BCB.PIX0136chave-fake-1234",
            isWaitingConfirmation = true,
            isCheckingNow = true,
        ),
    )

    // Achado #4 do Codex (PR #96): o backend busca os dois campos de checkout independente
    // do `billingType` escolhido — com Pix selecionado e os DOIS preenchidos, só o card do
    // Pix pode aparecer; nunca os dois juntos confundindo qual forma de pagamento usar.
    @Test
    fun pixSelecionadoComFaturaTambemPreenchida() = capture("8c-10-pix-com-fatura-oculta") {
        PaymentScreen(
            state = base.copy(
                billingType = BillingType.Pix,
                pixCopyPaste = "00020126580014BR.GOV.BCB.PIX0136chave-fake-1234",
                invoiceUrl = "https://checkout.asaas.com/i/abc123",
                isWaitingConfirmation = true,
            ),
            onIntent = {},
            onBack = {},
        )
    }

    // VUL-119 — RegeneratePix batendo num checkout que esta mesma sessão criou: mensagem
    // acionável no lugar do erro genérico, agora visível na seção de checkout também.
    @Test
    fun conflitoComCheckoutPendente() = capture("8c-11-conflito-checkout-pendente") {
        PaymentScreen(
            state = base.copy(
                pixCopyPaste = "00020126580014BR.GOV.BCB.PIX0136chave-fake-1234",
                isWaitingConfirmation = true,
                submitError = UiText.Res(Res.string.payment_error_conflict_pending_checkout),
            ),
            onIntent = {},
            onBack = {},
        )
    }

    // VUL-119 — seta do topo com checkout pendente: confirma antes de sair, em vez de
    // deixar o usuário reproduzir o conflito acima ao escolher outro plano.
    @Test
    fun confirmacaoDeVoltarComCheckoutPendente() = capture("8c-12-confirmacao-de-voltar") {
        PaymentScreen(
            state = base.copy(
                pixCopyPaste = "00020126580014BR.GOV.BCB.PIX0136chave-fake-1234",
                isWaitingConfirmation = true,
                isBackConfirmationOpen = true,
            ),
            onIntent = {},
            onBack = {},
        )
    }
}
