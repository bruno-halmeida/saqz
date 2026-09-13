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
class FinancialOnboardingScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun formShowsExactIncomeAndTermsAndRequiresSeparateConsent() {
        val state = mutableStateOf(formState)
        val intents = mutableListOf<FinancialOnboardingIntent>()
        compose.setContent { SaqzTheme { FinancialOnboardingScreen(state.value, intents::add, {}) } }
        compose.onNodeWithTag(FinancialOnboardingTags.field(OnboardingField.NAME)).assertExists(); capture("formulario")
        compose.onNodeWithText("Renda ou faturamento informado: R$\u00a02.500,01").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Termos · versão v1").assertExists()
        compose.onNodeWithText("Termos de teste para cadastro financeiro.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(FinancialOnboardingTags.Create).performScrollTo().assertIsNotEnabled(); capture("termos")
        compose.onNodeWithTag(FinancialOnboardingTags.Accept).performClick()
        assertEquals(listOf(FinancialOnboardingIntent.Accept(true)), intents)
        compose.runOnIdle { state.value = formState.copy(accepted = true) }
        compose.onNodeWithTag(FinancialOnboardingTags.Create).performClick()
        assertEquals(FinancialOnboardingIntent.Create, intents.last())
        for (terms in listOf(null, ReceiptTerms("", "Texto"), ReceiptTerms("v1", " "))) {
            compose.runOnIdle { state.value = formState.copy(accepted = true, terms = terms) }
            compose.onNodeWithTag(FinancialOnboardingTags.Create).assertIsNotEnabled()
            compose.onNodeWithTag(FinancialOnboardingTags.Accept).assertIsNotEnabled()
        }
    }
    @Test fun loadingLookupErrorUnavailableAndPendingHaveNoNewRegistrationAction() {
        val state = mutableStateOf(FinancialOnboardingState()); var exits = 0
        compose.setContent { SaqzTheme { FinancialOnboardingScreen(state.value, {}, { exits++ }) } }
        compose.onNodeWithTag(FinancialOnboardingTags.Create).assertDoesNotExist(); capture("carregando")
        compose.runOnIdle { state.value = FinancialOnboardingState(loading = false, error = ReceiptError.NETWORK) }
        compose.onNodeWithText("Você ainda não tem uma conta financeira cadastrada.").assertDoesNotExist(); capture("erro")
        compose.runOnIdle { state.value = FinancialOnboardingState(loading = false, discovered = true) }
        compose.onNodeWithText("O cadastro de novas contas ainda não está disponível para você.").assertIsDisplayed();
            capture("indisponivel")
        compose.runOnIdle { state.value = formState.copy(attempt = OnboardingAttempt("owner", "request", "CREATE")) }
        compose.onNodeWithTag(FinancialOnboardingTags.Create).assertDoesNotExist()
        compose.onNodeWithTag(FinancialOnboardingTags.Recover).assertIsEnabled(); capture("incerto")
        compose.onNodeWithContentDescription("Voltar").performClick(); assertEquals(0, exits)
        compose.runOnIdle { state.value = FinancialOnboardingState(loading = false, discovered = true) }
        compose.onNodeWithContentDescription("Voltar").performClick(); assertEquals(1, exits)
    }
    @Test fun documentsNeedConfirmationAndAccountStatusNeverComesFromSubmission() {
        val state = mutableStateOf(accountState)
        val intents = mutableListOf<FinancialOnboardingIntent>()
        compose.setContent { SaqzTheme { FinancialOnboardingScreen(state.value, intents::add, {}) } }
        compose.onNodeWithText("Ata de eleição").assertIsDisplayed()
        compose.onNodeWithTag(FinancialOnboardingTags.Upload).assertDoesNotExist()
        compose.onNodeWithTag(FinancialOnboardingTags.document("doc")).performClick()
        assertEquals(listOf(FinancialOnboardingIntent.ChooseFile("doc")), intents); capture("documentos")
        compose.runOnIdle { state.value = accountState.copy(selectedDocument = "doc",
            selectedFile = ReceiptDocumentFile("%PDF-test".encodeToByteArray(), "application/pdf")) }
        compose.onNodeWithText("Arquivo selecionado: application/pdf · 9 bytes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(FinancialOnboardingTags.Upload).performScrollTo().performClick()
        assertEquals(FinancialOnboardingIntent.Upload, intents.last()); capture("confirmar-documento")
        compose.runOnIdle { state.value = accountState.copy(uploaded = true) }
        compose.onNodeWithText("Cadastro em análise").assertExists()
        compose.onNodeWithText("Arquivo enviado para análise. A aprovação será exibida após a confirmação do Asaas.")
            .performScrollTo().assertIsDisplayed(); capture("enviado")
        compose.runOnIdle { state.value = accountState.copy(documents = listOf(document.copy(onboardingUrl =
            "https://asaas.com/onboarding/test"))) }
        compose.onNodeWithText("Continuar no Asaas").performScrollTo().performClick()
        assertEquals(FinancialOnboardingIntent.Open("doc"), intents.last()); capture("link-asaas")
        for ((status, label) in listOf(AccountRegistration.INCOMPLETE to "Cadastro em preparação",
            AccountRegistration.CORRECTION_REQUIRED to "Cadastro com pendências",
                AccountRegistration.APPROVED to "Cadastro aprovado",
            AccountRegistration.REJECTED to "Cadastro não aprovado")) {
            compose.runOnIdle { state.value = accountState.copy(account = accountState.account!!.copy(registration = status)) }
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed(); capture(status.name.lowercase())
        }
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        val dir = File("../build/reports/financial-onboarding").apply { mkdirs() }
        compose.onNodeWithTag(FinancialOnboardingTags.Screen).captureRoboImage(File(dir, "$name.png").path)
    }
    private val document = ReceiptDocument("doc", "CUSTOM", "PENDING", null, "Ata de eleição")
    private val accountState = FinancialOnboardingState(loading = false,
        account = OwnedReceiptAccount("account", "owner", AccountRegistration.UNDER_REVIEW, false), documents = listOf(document))
    private val formState = FinancialOnboardingState(loading = false, discovered = true, available = true,
        terms = ReceiptTerms("v1", "Termos de teste para cadastro financeiro."), form = OnboardingForm(mapOf(
            OnboardingField.NAME to "Titular", OnboardingField.EMAIL to "owner@example.test",
                OnboardingField.DOCUMENT to "12345678901",
            OnboardingField.PHONE to "11999999999", OnboardingField.INCOME to "2500,01", OnboardingField.STREET to "Rua",
            OnboardingField.NUMBER to "10", OnboardingField.PROVINCE to "Centro", OnboardingField.POSTAL_CODE to "01001000",
            OnboardingField.BIRTH_DATE to "01/01/1990")))
}
