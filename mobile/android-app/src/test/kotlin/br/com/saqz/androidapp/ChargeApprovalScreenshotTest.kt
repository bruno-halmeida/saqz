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
import org.junit.Assert.assertEquals
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ChargeApprovalScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun explicitAccountAndAllMethodPricesPrecedeAcceptance() {
        val state = mutableStateOf(ChargeApprovalState(loading = false, accounts = listOf(account)))
        val intents = mutableListOf<ChargeApprovalIntent>()
        compose.setContent { SaqzTheme { ChargeApprovalScreen(state.value, intents::add, {}) } }
        compose.onNodeWithTag(ChargeApprovalTags.account("account")).performClick()
        assertEquals(listOf(ChargeApprovalIntent.Account("account")), intents); capture("contas")
        compose.runOnIdle { state.value = reviewed }
        compose.onAllNodesWithText("Valor sem taxas: R$\u00a0100,00").assertCountEquals(2)
        compose.onNodeWithText("Vencimento: 20/09/2026").assertExists()
        compose.onNodeWithText("Termos · versão v1").assertExists()
        compose.onNodeWithText("Termos · versão v2").assertExists()
        compose.onNodeWithText("Taxas de serviço e pagamento: R$\u00a06,58").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Total a pagar: R$\u00a0106,58").assertIsDisplayed()
        compose.onNodeWithText("Taxas de serviço e pagamento: R$\u00a09,00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Total a pagar: R$\u00a0109,00").assertIsDisplayed()
        compose.onNodeWithText("Termos Pix de teste").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Termos cartão de teste").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ChargeApprovalTags.Submit).performScrollTo().assertIsNotEnabled(); capture("revisao-sem-aceite")
        compose.onNodeWithTag(ChargeApprovalTags.Accept).performClick()
        assertEquals(ChargeApprovalIntent.Accept(true), intents.last())
        compose.runOnIdle { state.value = reviewed.copy(accepted = true) }
        compose.onNodeWithTag(ChargeApprovalTags.Submit).performClick()
        assertEquals(ChargeApprovalIntent.Approve, intents.last()); capture("revisao-aceita")
        compose.runOnIdle { state.value = reviewed.copy(terms = reviewed.terms.take(1)) }
        compose.onNodeWithTag(ChargeApprovalTags.Submit).assertIsNotEnabled(); capture("termo-ausente")
    }
    @Test fun loadingEmptyErrorUncertaintyAndConflictNeverEnableAnotherApproval() {
        val state = mutableStateOf(ChargeApprovalState())
        compose.setContent { SaqzTheme { ChargeApprovalScreen(state.value, {}, {}) } }
        capture("carregando")
        compose.runOnIdle { state.value = ChargeApprovalState(loading = false) }
        compose.onNodeWithText("Ainda não há conta financeira disponível para você.").assertIsDisplayed(); capture("vazio")
        compose.runOnIdle { state.value = ChargeApprovalState(loading = false, error = ReceiptError.NETWORK) }; capture("erro")
        compose.runOnIdle { state.value = reviewed.copy(error = ReceiptError.STALE) }
        compose.onNodeWithTag(ChargeApprovalTags.Submit).performScrollTo().assertIsNotEnabled(); capture("conflito")
        compose.runOnIdle { state.value = reviewed.copy(attempt = attempt, error = ReceiptError.UNCERTAIN) }
        compose.onNodeWithTag(ChargeApprovalTags.Replay).performScrollTo().assertIsEnabled(); capture("incerto")
        compose.onNodeWithTag(ChargeApprovalTags.Submit).performScrollTo().assertIsNotEnabled()
    }
    @Test fun cancellationIsExplicitAndPendingIsNotConfirmed() {
        val state = mutableStateOf(ChargeApprovalState(loading = false, accountId = "account", detail =
            MemberPaymentDetail(order, emptyList())))
        val intents = mutableListOf<ChargeApprovalIntent>()
        compose.setContent { SaqzTheme { ChargeApprovalScreen(state.value, intents::add, {}) } }
        compose.onNodeWithTag(ChargeApprovalTags.Confirm).assertDoesNotExist(); capture("liberada")
        compose.onNodeWithTag(ChargeApprovalTags.Cancel).performClick()
        assertEquals(listOf(ChargeApprovalIntent.RequestCancel), intents)
        compose.runOnIdle { state.value = state.value.copy(confirmCancel = true) }
        compose.onNodeWithText("Quer cancelar esta ordem?", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ChargeApprovalTags.Confirm).performScrollTo().performClick()
        assertEquals(ChargeApprovalIntent.ConfirmCancel, intents.last()); capture("confirmar-cancelamento")
        for ((status, label) in listOf("CANCEL_PENDING" to "Cancelamento em andamento", "CANCELLED" to "Cobrança cancelada",
            "PAID" to "Pagamento confirmado", "REFUNDED" to "Pagamento reembolsado")) {
            compose.runOnIdle { state.value = ChargeApprovalState(loading = false, accountId = "account",
                detail = MemberPaymentDetail(order.copy(status = status), emptyList())) }
            compose.onNodeWithText(label).assertIsDisplayed()
            compose.onNodeWithTag(ChargeApprovalTags.Cancel).assertDoesNotExist(); capture(status.lowercase())
        }
    }
    @Test fun priorAcceptanceCannotAuthorizeMissingMismatchedOrBlankTerms() {
        val state = mutableStateOf(reviewed.copy(accepted = true))
        compose.setContent { SaqzTheme { ChargeApprovalScreen(state.value, {}, {}) } }
        compose.onNodeWithTag(ChargeApprovalTags.Submit).performScrollTo().assertIsEnabled()
        for (terms in listOf(reviewed.terms.take(1), listOf(reviewed.terms.first(), ReceiptTerms("v3", "Outro")),
            listOf(reviewed.terms.first(), ReceiptTerms("v2", " ")))) {
            compose.runOnIdle { state.value = reviewed.copy(accepted = true, terms = terms) }
            compose.onNodeWithTag(ChargeApprovalTags.Submit).performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag(ChargeApprovalTags.Accept).assertIsNotEnabled()
        }
    }
    @Test fun headerBackPreservesPendingCommandUntilResolution() {
        val state = mutableStateOf(reviewed.copy(attempt = attempt)); var exits = 0
        compose.setContent { SaqzTheme { ChargeApprovalScreen(state.value, {}, { exits++ }) } }
        compose.onNodeWithContentDescription("Voltar").performClick()
        assertEquals(0, exits); assertEquals(attempt, state.value.attempt)
        compose.runOnIdle { state.value = reviewed }
        compose.onNodeWithContentDescription("Voltar").performClick(); assertEquals(1, exits)
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        val dir = File("../build/reports/charge-approval").apply { mkdirs() }
        compose.onNodeWithTag(ChargeApprovalTags.Screen).captureRoboImage(File(dir, "$name.png").path)
    }
    private val account = ReceiptAccount("account", AccountRegistration.APPROVED, true)
    private val target = ChargeApprovalTarget("group", "charge", "account")
    private val pix = MemberPaymentQuote("schedule", "v1", ReceiptMethod.PIX, 10000, 658, 10658, 10000, 300, 358)
    private val review = ChargeApprovalReview(target, "payer", "2026-09-20", "2026-08-01",
        listOf(pix, pix.copy(method = ReceiptMethod.CARD, termsVersion = "v2", feesCents = 900, totalCents = 10900,
            providerFeeCents = 600)), "a".repeat(64))
    private val reviewed = ChargeApprovalState(loading = false, accounts = listOf(account), accountId = "account",
        review = review,
        terms = listOf(ReceiptTerms("v1", "Termos Pix de teste"), ReceiptTerms("v2", "Termos cartão de teste")))
    private val order = MemberPaymentOrder("order", "account", "charge", "group", "payer", "2026-09-20", "ISSUED",
        review.quotes, review.fingerprint)
    private val attempt = ApprovalAttempt("owner", "group", "charge", "account", "request", review.fingerprint)
}
