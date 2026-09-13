package br.com.saqz.androidapp

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.presentation.*
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class MemberPaymentScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun historyStatesAndOrderSelection() {
        val state = mutableStateOf(MemberPaymentHistoryState())
        val intents = mutableListOf<MemberPaymentHistoryIntent>()
        compose.setContent { SaqzTheme { MemberPaymentHistoryScreen(state.value, intents::add, {}) } }
        capture("historico-carregando")
        compose.runOnIdle { state.value = MemberPaymentHistoryState(loading = false) }; capture("historico-vazio")
        compose.runOnIdle { state.value = MemberPaymentHistoryState(loading = false, error = ReceiptError.NETWORK) };
            capture("historico-erro")
        compose.runOnIdle { state.value = MemberPaymentHistoryState(loading = false, orders = listOf(order),
            nextCursor = order.id) }
        capture("historico-pagina")
        compose.onNodeWithTag(MemberPaymentTags.order(order.id)).performClick()
        assertEquals(listOf(MemberPaymentHistoryIntent.Open(order.id)), intents)
    }
    @Test fun reviewRequiresTermsAndAcceptanceAndShowsServerTotals() {
        val state = mutableStateOf(review.copy(method = null, terms = null))
        val intents = mutableListOf<MemberPaymentIntent>()
        compose.setContent { SaqzTheme { MemberPaymentScreen(state.value, intents::add, {}) } }
        capture("escolher-meio")
        compose.onNodeWithTag(MemberPaymentTags.method(ReceiptMethod.PIX)).performClick()
        assertEquals(listOf(MemberPaymentIntent.Method(ReceiptMethod.PIX)), intents)
        compose.runOnIdle { state.value = review.copy(terms = null, error = ReceiptError.UNAVAILABLE) }
        compose.onNodeWithTag(MemberPaymentTags.Accept).performScrollTo().assertIsNotEnabled(); capture("termos-indisponiveis")
        compose.runOnIdle { state.value = review }
        compose.onNodeWithText("Base: R$\u00a0100,00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Taxas de serviço e pagamento: R$\u00a06,58").assertIsDisplayed()
        compose.onNodeWithText("Total para o pagador: R$\u00a0106,58").assertIsDisplayed()
        compose.onNodeWithText("Pagar R$\u00a0106,58").assertExists()
        compose.runOnIdle { state.value = review.copy(method = ReceiptMethod.CARD) }
        compose.onNodeWithText("Taxas de serviço e pagamento: R$\u00a09,00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Total para o pagador: R$\u00a0109,00").assertIsDisplayed()
        compose.onNodeWithTag(MemberPaymentTags.Pay).assertTextEquals("Pagar R$\u00a0109,00").assertIsNotEnabled()
        capture("revisao-cartao")
        compose.runOnIdle { state.value = review }
        compose.onNodeWithTag(MemberPaymentTags.Pay).performScrollTo().assertIsNotEnabled(); capture("revisao-sem-aceite")
        compose.runOnIdle { state.value = review.copy(accepted = true) }
        compose.onNodeWithTag(MemberPaymentTags.Pay).performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Pagar pelo app").assertIsDisplayed()
        capture("revisao-aceita")
        compose.onNodeWithTag(MemberPaymentTags.Pay).performClick()
        assertEquals(MemberPaymentIntent.Pay, intents.last())
        compose.runOnIdle { state.value = review.copy(accepted = true, document = "123") }
        compose.onNodeWithTag(MemberPaymentTags.Pay).assertIsNotEnabled(); capture("documento-incompleto")
        compose.runOnIdle { state.value = review.copy(pending = true, canReplay = true, error = ReceiptError.UNCERTAIN) }
        compose.onNodeWithText("Recuperar tentativa").performScrollTo(); capture("tentativa-incerta")
        compose.onNodeWithTag(MemberPaymentTags.Pay).performScrollTo().assertIsNotEnabled()
    }
    @Test fun paymentStatusesNeverMistakeRefundOrUncertaintyForSuccess() {
        val state = mutableStateOf(MemberPaymentState(loading = true))
        compose.setContent { SaqzTheme { MemberPaymentScreen(state.value, {}, {}) } }
        capture("pagamento-carregando")
        compose.runOnIdle { state.value = MemberPaymentState(loading = false, pending = true) }; capture("restauracao-pendente")
        val statuses = listOf("ACTIVE" to "Aguardando pagamento", "UNKNOWN" to "Pagamento em verificação",
            "CREATING" to "Pagamento em verificação", "CONFIRMED" to "Pagamento confirmado",
                "AVAILABLE" to "Pagamento confirmado",
            "REFUNDED" to "Pagamento reembolsado", "DISPUTED" to "Pagamento em contestação",
                "RECOVERY_PENDING" to "Pagamento em verificação",
            "CANCEL_PENDING" to "Cancelamento em andamento", "CANCELLED" to "Tentativa cancelada", "EXPIRED" to "Pix expirado",
            "FUTURE_STATUS" to "Situação em atualização")
        statuses.forEach { (status, label) ->
            compose.runOnIdle { state.value = active.copy(detail = MemberPaymentDetail(order, listOf(instrument.copy(
                status = status,
                confirmed = true, available = true))), pixExpired = status == "EXPIRED") }
            compose.onNodeWithTag(MemberPaymentTags.Status).performScrollTo().assertTextEquals(label)
            if (status != "ACTIVE") compose.onNodeWithTag(MemberPaymentTags.Copy).assertDoesNotExist()
            capture("status-${status.lowercase()}")
        }
        compose.runOnIdle { state.value = active.copy(pixExpired = true) }
        compose.onNodeWithTag(MemberPaymentTags.Copy).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(renewalDueDate = "2026-09-21") }
        compose.onNodeWithTag(MemberPaymentTags.Renew).performScrollTo().assertIsEnabled(); capture("prazo-pix-vencido-renovacao")
        compose.runOnIdle { state.value = active.copy(detail = MemberPaymentDetail(order,
            listOf(instrument.copy(status = "CONFIRMED"))), receiptShared = true) }
        compose.onNodeWithTag(MemberPaymentTags.Export).performScrollTo().assertIsEnabled(); capture("comprovante-compartilhado")
        compose.runOnIdle { state.value = state.value.copy(receiptShared = false, receiptShareFailed = true) }
        capture("comprovante-falha")
        compose.runOnIdle { state.value = active.copy(detail = MemberPaymentDetail(order.copy(status = "REFUNDED"),
            listOf(instrument.copy(status = "AVAILABLE")))) }
        compose.onNodeWithTag(MemberPaymentTags.Status).performScrollTo().assertTextEquals("Pagamento reembolsado")
        capture("reembolso-prevalece")
    }
    @Test fun pixQrCopyFallbackAndHostedCardActions() {
        val state = mutableStateOf(active)
        val intents = mutableListOf<MemberPaymentIntent>()
        compose.setContent { SaqzTheme { MemberPaymentScreen(state.value, intents::add, {}) } }
        compose.onNodeWithContentDescription("QR Code para pagar com Pix").assertExists()
        capture("pix-qr")
        compose.onNodeWithTag(MemberPaymentTags.Copy).performScrollTo().performClick()
        assertEquals(listOf(MemberPaymentIntent.CopyPix), intents); capture("pix-copiar")
        compose.runOnIdle { state.value = active.copy(copied = true) }
        compose.onNodeWithText("Código Pix copiado").performScrollTo(); capture("pix-copiado")
        compose.runOnIdle { state.value = active.copy(detail = MemberPaymentDetail(order, listOf(instrument.copy(
            pixImage = "invalid")))) }
        compose.onNodeWithText("Não foi possível exibir o QR Code.", substring = true).performScrollTo().assertExists()
        compose.onNodeWithTag(MemberPaymentTags.Copy).assertIsEnabled(); capture("pix-imagem-indisponivel")
        compose.runOnIdle { state.value = active.copy(detail = MemberPaymentDetail(order, listOf(instrument.copy(quote =
            quote.copy(method = ReceiptMethod.CARD))))) }
        compose.onNodeWithTag(MemberPaymentTags.Card).performScrollTo().performClick()
        assertEquals(MemberPaymentIntent.OpenCard, intents.last()); capture("cartao-hospedado")
        compose.runOnIdle { state.value = state.value.copy(openFailed = true) }
        compose.onNodeWithText("Não foi possível abrir o pagamento. Tente novamente.").performScrollTo(); capture(
            "cartao-erro-abertura")
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val directory = File("../build/reports/member-payment-ui").apply { mkdirs() }
        val tag = if (compose.onAllNodesWithTag(MemberPaymentTags.Screen).fetchSemanticsNodes().isNotEmpty())
            MemberPaymentTags.Screen else MemberPaymentTags.History
        compose.onNodeWithTag(tag).captureRoboImage(File(directory, "$name.png").path)
    }
    private val quote = MemberPaymentQuote("schedule", "v1", ReceiptMethod.PIX, 10000, 658, 10658, 10000, 300, 358)
    private val order = MemberPaymentOrder("00000000-0000-4000-8000-000000001234", "account", "charge", "group", "payer",
        "2026-09-20", "ISSUED", listOf(quote, quote.copy(method = ReceiptMethod.CARD, feesCents = 900, totalCents =
            10900, providerFeeCents = 600)), "a".repeat(64))
    private val instrument = MemberPaymentInstrument("instrument", "account", order.id, quote, "ACTIVE", "pay_demo123", null,
        "PIX DE TESTE SEM VALIDADE FINANCEIRA", Base64.getEncoder().encodeToString(qrcode.QRCode.ofSquares().withSize(10)
            .build("SAQZ TESTE SEM VALIDADE FINANCEIRA").renderToBytes()), "https://asaas.com/i/demo", false, false,
                false, false, "2026-09-20T23:00:00Z")
    private val active = MemberPaymentState(loading = false, detail = MemberPaymentDetail(order, listOf(instrument)))
    private val review = MemberPaymentState(loading = false, detail = MemberPaymentDetail(order, emptyList()), method =
        ReceiptMethod.PIX,
        terms = ReceiptTerms("v1",
            "Termos de demonstração: confira o valor da cobrança, as taxas e o total antes de aceitar o pagamento."),
        name = "Pessoa de Teste", document = "12345678909")
}
