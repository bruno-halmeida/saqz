package br.com.saqz.receivables.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RecurrenceScreenTest {
    @Test fun reviewRequiresRealTermsAcceptanceAndShowsLiteralMoneyOnIos() = runComposeUiTest {
        val state = mutableStateOf(RecurrenceState("account", "group", loading = false, firstDueDate = "2026-10-10"))
        val intents = mutableListOf<RecurrenceIntent>()
        setContent { SaqzTheme { RecurrenceScreen(state.value, intents::add, {}) } }
        onNodeWithText("No Pix, uma nova cobrança é gerada a cada mês para você pagar manualmente.").assertIsDisplayed()
        onNodeWithTag(RecurrenceTags.Preview).assertIsEnabled().performClick()
        assertEquals(listOf<RecurrenceIntent>(RecurrenceIntent.Preview), intents)
        runOnIdle { state.value = state.value.copy(review = review, terms = ReceiptTerms("terms", "Termos publicados"),
            name = "Pessoa Teste", document = "12345678909") }
        onNodeWithText("Base: R$\u00a0100,00").performScrollTo().assertIsDisplayed()
        onNodeWithText("Total para o pagador: R$\u00a0104,90").assertIsDisplayed()
        onNodeWithTag(RecurrenceTags.Submit).performScrollTo().assertIsNotEnabled()
        runOnIdle { state.value = state.value.copy(accepted = true) }
        onNodeWithTag(RecurrenceTags.Submit).assertIsEnabled().performClick()
        assertEquals(RecurrenceIntent.Submit, intents.last())
    }

    @Test fun stoppedPendingAndHostedCardRenderOnlyValidActionsOnIos() = runComposeUiTest {
        val state = mutableStateOf(RecurrenceState("account", "group", loading = false,
            recurrence = recurrence.copy(status = "STOP_PENDING"), pending = true))
        setContent { SaqzTheme { RecurrenceScreen(state.value, {}, {}) } }
        onNodeWithText("Cancelamento em confirmação").performScrollTo().assertIsDisplayed()
        onNodeWithTag(RecurrenceTags.Cancel).assertDoesNotExist()
        runOnIdle { state.value = state.value.copy(pending = false, recurrence = recurrence.copy(status = "STOPPED")) }
        onNodeWithText("A cobrança mensal anterior foi encerrada.", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag(RecurrenceTags.Submit).assertDoesNotExist()
        runOnIdle { state.value = state.value.copy(recurrence = recurrence.copy(status = "AUTHORIZING",
            method = ReceiptMethod.CARD, hostedCheckoutUrl = "https://asaas.com/c/checkout")) }
        onNodeWithTag(RecurrenceTags.Checkout).performScrollTo().assertIsDisplayed()
    }
}

private val review = RecurrenceReview("account", "group", "payer", ReceiptMethod.PIX, 10_000, 490, 10_490, 300, 190,
    10_000, "schedule", "terms", "2026-10-10", "MONTHLY", "a".repeat(64))
private val recurrence = PaymentRecurrence("recurrence", "account", "group", "payer", ReceiptMethod.PIX, 10_000, 490,
    10_490, "2026-10-10", "ACTIVE", "subscription", null, null)
