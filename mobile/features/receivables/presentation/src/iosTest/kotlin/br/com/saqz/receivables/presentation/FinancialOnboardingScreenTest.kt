package br.com.saqz.receivables.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FinancialOnboardingScreenTest {
    @Test fun explicitConsentExactMoneyAndUploadConfirmationAreVisibleOnIos() = runComposeUiTest {
        val state = mutableStateOf(FinancialOnboardingState(loading = false, discovered = true, available = true,
            form = onboardingForm, terms = ReceiptTerms("v1", "Termos de cadastro de teste")))
        val intents = mutableListOf<FinancialOnboardingIntent>()
        setContent { SaqzTheme { FinancialOnboardingScreen(state.value, intents::add, {}) } }
        onNodeWithText("Renda ou faturamento informado: R$\u00a02.500,01").performScrollTo().assertIsDisplayed()
        onNodeWithText("Termos de cadastro de teste").performScrollTo().assertIsDisplayed()
        onNodeWithTag(FinancialOnboardingTags.Create).performScrollTo().assertIsNotEnabled()
        runOnIdle { state.value = state.value.copy(accepted = true) }
        onNodeWithTag(FinancialOnboardingTags.Create).performClick()
        assertEquals(listOf<FinancialOnboardingIntent>(FinancialOnboardingIntent.Create), intents)
        runOnIdle { state.value = state.value.copy(terms = ReceiptTerms("v1", " ")) }
        onNodeWithTag(FinancialOnboardingTags.Create).assertIsNotEnabled()
        runOnIdle { state.value = FinancialOnboardingState(loading = false, account = onboardingAccount,
            documents = listOf(onboardingDocument)) }
        onNodeWithTag(FinancialOnboardingTags.Upload).assertDoesNotExist()
        onNodeWithTag(FinancialOnboardingTags.document("doc")).performScrollTo().performClick()
        assertEquals(FinancialOnboardingIntent.ChooseFile("doc"), intents.last())
        runOnIdle { state.value = state.value.copy(selectedDocument = "doc", selectedFile = onboardingFile) }
        onNodeWithTag(FinancialOnboardingTags.Upload).performScrollTo().performClick()
        assertEquals(FinancialOnboardingIntent.Upload, intents.last())
        runOnIdle { state.value = state.value.copy(selectedFile = null, uploaded = true) }
        onNodeWithText("Cadastro em análise").assertExists()
        onNodeWithTag(FinancialOnboardingTags.Upload).assertDoesNotExist()
    }
}
