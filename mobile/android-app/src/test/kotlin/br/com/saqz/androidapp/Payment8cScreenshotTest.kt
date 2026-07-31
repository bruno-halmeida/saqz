package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.presentation.payment.PaymentState
import br.com.saqz.subscriptions.presentation.payment.ui.PaymentScreen
import br.com.saqz.subscriptions.resources.Res
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
        state.submitError != null -> "8c-5-erro-generico"
        state.isSubmitting -> "8c-6-enviando"
        state.isCheckingNow -> "8c-9-confirmando"
        state.invoiceUrl != null -> "8c-8-cartao-aguardando"
        state.pixCopyPaste != null -> "8c-7-pix-aguardando"
        state.billingType == BillingType.CreditCard -> "8c-3-form-cartao"
        state.discountPercent != null -> "8c-2-form-pix-cupom"
        else -> "8c-1-form-pix"
    }

    private val base = PaymentState(
        plan = Plan.Organizador,
        cycle = SubscriptionCycle.Monthly,
        planName = "Organizador",
        priceCents = 4_990L,
    )

    // Pix escolhido (default), CPF/CNPJ vazio — o que o export desenha ao abrir a tela.
    @Test
    fun formPix() = screen(base)

    // Mesmo formulário, cupom aplicado no resumo — a linha de desconto é um elemento a mais.
    @Test
    fun formPixComCupom() = screen(
        base.copy(couponCode = "BEMVINDO10", discountPercent = 10, priceCents = 4_491L),
    )

    // Cartão selecionado — o segmentado muda de posição, mas o formulário é o mesmo.
    @Test
    fun formCartao() = screen(base.copy(billingType = BillingType.CreditCard))

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
}
