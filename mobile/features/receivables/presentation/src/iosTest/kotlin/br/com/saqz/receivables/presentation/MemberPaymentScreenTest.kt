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
}
