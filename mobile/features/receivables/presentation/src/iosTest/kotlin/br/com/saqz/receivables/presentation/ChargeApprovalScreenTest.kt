package br.com.saqz.receivables.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ChargeApprovalScreenTest {
    @Test fun releaseNeedsAllTermsAndCancellationNeedsConfirmation() = runComposeUiTest {
        val state = mutableStateOf(ChargeApprovalState(loading = false, accountId = "account", review = approvalReview,
            terms = listOf(ReceiptTerms("v1", "Termos Pix"))))
        val intents = mutableListOf<ChargeApprovalIntent>()
        setContent { SaqzTheme { ChargeApprovalScreen(state.value, intents::add, {}) } }
        onNodeWithTag(ChargeApprovalTags.Submit).performScrollTo().assertIsNotEnabled()
        runOnIdle { state.value = state.value.copy(accepted = true) }
        onNodeWithTag(ChargeApprovalTags.Submit).assertIsNotEnabled()
        onNodeWithTag(ChargeApprovalTags.Accept).assertIsNotEnabled()
        runOnIdle { state.value = state.value.copy(terms = state.value.terms + ReceiptTerms("v2", "Termos cartão"), accepted = true) }
        onNodeWithTag(ChargeApprovalTags.Submit).assertIsEnabled().performClick()
        assertEquals(listOf<ChargeApprovalIntent>(ChargeApprovalIntent.Approve), intents)
        runOnIdle { state.value = ChargeApprovalState(loading = false, accountId = "account", detail = MemberPaymentDetail(approvalOrder, emptyList())) }
        onNodeWithTag(ChargeApprovalTags.Confirm).assertDoesNotExist()
        onNodeWithTag(ChargeApprovalTags.Cancel).performScrollTo().performClick()
        assertEquals(ChargeApprovalIntent.RequestCancel, intents.last())
        runOnIdle { state.value = state.value.copy(confirmCancel = true) }
        onNodeWithTag(ChargeApprovalTags.Confirm).performScrollTo().performClick()
        assertEquals(ChargeApprovalIntent.ConfirmCancel, intents.last())
        runOnIdle { state.value = state.value.copy(confirmCancel = false, detail = MemberPaymentDetail(approvalOrder.copy(status = "CANCEL_PENDING"), emptyList())) }
        onNodeWithText("Cancelamento em andamento").assertExists()
        onNodeWithTag(ChargeApprovalTags.Cancel).assertDoesNotExist()
    }
}
