package br.com.saqz.receivables.presentation

import androidx.compose.ui.test.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FinancialManagementScreenTest {
    @Test fun delegateCanRecoverWithoutExposingTechnicalIdentifiersOrRedelegation() = runComposeUiTest {
        val intents = mutableListOf<FinancialManagementIntent>()
        val correction = ReceiptRegistrationCorrection("owner@example.test", null, "11999999999", null, 250001,
            "01001000", "Rua", "10", null, "Centro")
        val form = FinancialManagementForm(mapOf(
            ManagementField.EMAIL to correction.email, ManagementField.MOBILE_PHONE to correction.mobilePhone,
            ManagementField.INCOME to "2500,01", ManagementField.POSTAL_CODE to correction.postalCode,
            ManagementField.ADDRESS to correction.address, ManagementField.ADDRESS_NUMBER to correction.addressNumber,
            ManagementField.PROVINCE to correction.province))
        val state = FinancialManagementState(loading = false, actorId = "actor", accounts = listOf(
            ManagedReceiptAccount("account", "owner", AccountRegistration.CORRECTION_REQUIRED, false)),
            selectedAccountId = "account", role = ReceiptManagementRole.DELEGATE, form = form,
            delegations = listOf(ReceiptDelegation("account", "admin", false)),
            attempt = FinancialManagementAttempt("actor", "account", "request-123", "CORRECT"))
        setContent { SaqzTheme { FinancialManagementScreen(state, intents::add, {}) } }
        onNodeWithText("Sessão: actor").assertDoesNotExist()
        onNodeWithText("request-123", substring = true).assertDoesNotExist()
        onNodeWithText("Resultado ainda não confirmado.").assertExists()
        onNodeWithText("CPF", substring = true).assertDoesNotExist()
        onNodeWithText("reembolso", substring = true, ignoreCase = true).assertDoesNotExist()
        onNodeWithTag(FinancialManagementTags.Grant).assertDoesNotExist()
        onNodeWithTag(FinancialManagementTags.Recover).performClick()
        assertEquals(listOf<FinancialManagementIntent>(FinancialManagementIntent.Recover), intents)
    }

    @Test fun ownerChoosesAdministratorByNameAndGroup() = runComposeUiTest {
        val intents = mutableListOf<FinancialManagementIntent>()
        setContent { SaqzTheme { FinancialManagementScreen(FinancialManagementState(
            loading = false, role = ReceiptManagementRole.OWNER,
            candidates = listOf(ReceiptAdministrator("admin-id", "Ana", listOf("Futebol"))),
        ), intents::add, {}) } }
        onNodeWithText("Ana · Futebol").performScrollTo().performClick()
        assertEquals(listOf<FinancialManagementIntent>(FinancialManagementIntent.DelegateUser("admin-id")), intents)
        onNodeWithText("Identificador do administrador atual").assertDoesNotExist()
        onNodeWithText("admin-id", substring = true).assertDoesNotExist()
    }

    @Test fun ownerReviewsLiteralBrlAndExplicitFiscalWarningBeforeCorrection() = runComposeUiTest {
        val correction = ReceiptRegistrationCorrection("owner@example.test", null, "11999999999", null, 250001,
            "01001000", "Rua", "10", null, "Centro")
        val form = FinancialManagementForm(ManagementField.entries.associateWith { field -> when (field) {
            ManagementField.EMAIL -> correction.email; ManagementField.MOBILE_PHONE -> correction.mobilePhone
            ManagementField.INCOME -> "2500,01"; ManagementField.POSTAL_CODE -> correction.postalCode
            ManagementField.ADDRESS -> correction.address; ManagementField.ADDRESS_NUMBER -> correction.addressNumber
            ManagementField.PROVINCE -> correction.province; else -> ""
        } })
        setContent { SaqzTheme { FinancialManagementScreen(FinancialManagementState(loading = false, actorId = "owner",
            accounts = listOf(ManagedReceiptAccount("account", "owner", AccountRegistration.APPROVED, false)),
            selectedAccountId = "account", role = ReceiptManagementRole.OWNER, form = form,
            terms = ReceiptTerms("v1", "Termos integrais")), {}, {}) } }
        onNodeWithText("Valor informado: R$ 2.500,01").performScrollTo().assertIsDisplayed()
        onNodeWithText("Alterar o município pode redefinir configurações fiscais", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag(FinancialManagementTags.Correct).assertIsNotEnabled()
    }
}
