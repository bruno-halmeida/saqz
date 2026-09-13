package br.com.saqz.receivables.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MemberPaymentScreenTest {
    @Test fun reviewAndExpiryControlsFollowStateOnIos() = runComposeUiTest {
        val state = mutableStateOf(MemberPaymentState(loading = false, detail = MemberPaymentDetail(paymentOrder, emptyList()),
            method = ReceiptMethod.PIX, name = "Pessoa Teste", document = "12345678909"))
        val intents = mutableListOf<MemberPaymentIntent>()
        setContent { SaqzTheme { MemberPaymentScreen(state.value, intents::add, {}) } }
        onNodeWithTag(MemberPaymentTags.Pay).performScrollTo().assertIsNotEnabled()
        runOnIdle { state.value = state.value.copy(terms = ReceiptTerms("v1", "Termos publicados"), accepted = true) }
        onNodeWithTag(MemberPaymentTags.Pay).performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf<MemberPaymentIntent>(MemberPaymentIntent.Pay), intents)
        runOnIdle { state.value = MemberPaymentState(loading = false,
            detail = MemberPaymentDetail(paymentOrder, listOf(paymentInstrument())), pixExpired = true) }
        onNodeWithTag(MemberPaymentTags.Status).performScrollTo().assertTextEquals("Pix expirado")
        onNodeWithTag(MemberPaymentTags.Copy).assertDoesNotExist()
        runOnIdle { state.value = state.value.copy(pixExpired = false, detail = MemberPaymentDetail(paymentOrder,
            listOf(paymentInstrument().copy(status = "REFUNDED", confirmed = true, available = true)))) }
        onNodeWithTag(MemberPaymentTags.Status).performScrollTo().assertTextEquals("Pagamento reembolsado")
        onNodeWithTag(MemberPaymentTags.Copy).assertDoesNotExist()
    }

    @Test fun expiredPixRenewalReceiptAndRecurrenceEntryExposeExactIntentsOnIos() = runComposeUiTest {
        val expired = paymentInstrument().copy(status = "EXPIRED", expiresAt = "2026-09-12T12:00:00Z")
        val state = mutableStateOf(MemberPaymentState(loading = false,
            detail = MemberPaymentDetail(paymentOrder, listOf(expired)), pixExpired = true, renewalDueDate = "2026-09-20"))
        val intents = mutableListOf<MemberPaymentIntent>()
        val recurrences = mutableListOf<Pair<String, String>>()
        setContent { SaqzTheme { MemberPaymentScreen(state.value, intents::add, {},
            onOpenRecurrence = { account, group -> recurrences += account to group }) } }
        onNodeWithTag(MemberPaymentTags.Renew).performScrollTo().assertIsEnabled().performClick()
        assertEquals(MemberPaymentIntent.RenewPix, intents.last())
        onNodeWithTag(MemberPaymentTags.Recurrence).performScrollTo().performClick()
        assertEquals(listOf("account" to "group"), recurrences)
        runOnIdle { state.value = state.value.copy(pixExpired = false,
            detail = MemberPaymentDetail(paymentOrder, listOf(expired.copy(status = "CONFIRMED")))) }
        onNodeWithTag(MemberPaymentTags.Export).performScrollTo().assertIsEnabled().performClick()
        assertEquals(MemberPaymentIntent.ExportReceipt, intents.last())
        runOnIdle { state.value = state.value.copy(receiptShared = true) }
        onNodeWithText("Compartilhamento aberto.").performScrollTo().assertIsDisplayed()
    }
}
