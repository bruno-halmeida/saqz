package br.com.saqz.receivables.presentation

import androidx.compose.ui.test.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReceiptConfigurationScreenTest {
    /** v1: sem escolha de meio; "Revisar e ativar" pede a prévia com Pix e cartão juntos. */
    @Test fun approvedAccountReviewsBothMethodsAtOnce() = runComposeUiTest {
        val intents = mutableListOf<ReceiptConfigurationIntent>()
        val config = ReceiptConfiguration("account", "group", false, false, false)
        setContent { SaqzTheme { ReceiptConfigurationScreen(ReceiptConfigurationState(loading = false,
            accounts = listOf(ReceiptAccount("account", AccountRegistration.APPROVED, true)), accountId = "account",
            status = ReceiptStatus(config, mapOf("CANCEL" to ReceiptPermission(true))), discoveryAvailable = true), intents::add, {}) } }
        onNodeWithTag(ReceiptConfigurationTags.method(ReceiptMethod.PIX)).assertDoesNotExist()
        onNodeWithTag(ReceiptConfigurationTags.Preview).assertIsEnabled().performClick()
        assertEquals(listOf<ReceiptConfigurationIntent>(ReceiptConfigurationIntent.PreviewAllMethods), intents)
        onNodeWithTag(ReceiptConfigurationTags.Activate).assertDoesNotExist()
    }

    @Test fun withoutAccountTheOnlyActionIsStartingTheRegistration() = runComposeUiTest {
        var opened = 0
        setContent { SaqzTheme { ReceiptConfigurationScreen(ReceiptConfigurationState(loading = false), {}, {}, onRegistration = { opened++ }) } }
        onNodeWithTag(ReceiptConfigurationTags.Preview).assertDoesNotExist()
        onNodeWithTag(ReceiptConfigurationTags.Registration).performClick()
        assertEquals(1, opened)
    }
}
