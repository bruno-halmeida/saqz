package br.com.saqz.receivables.presentation

import androidx.compose.ui.test.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReceiptConfigurationScreenTest {
    @Test fun noDefaultSelectionAndContextualMethodIntent() = runComposeUiTest {
        val intents = mutableListOf<ReceiptConfigurationIntent>()
        val config = ReceiptConfiguration("account", "group", false, false, false)
        setContent { SaqzTheme { ReceiptConfigurationScreen(ReceiptConfigurationState(loading = false,
            accounts = listOf(ReceiptAccount("account", AccountRegistration.APPROVED, true)), accountId = "account",
            status = ReceiptStatus(config, mapOf("CANCEL" to ReceiptPermission(true))), discoveryAvailable = true), intents::add, {}) } }
        onNodeWithTag(ReceiptConfigurationTags.Preview).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(ReceiptConfigurationTags.method(ReceiptMethod.PIX)).performScrollTo().assertIsOff().performClick()
        assertEquals(listOf<ReceiptConfigurationIntent>(ReceiptConfigurationIntent.ToggleMethod(ReceiptMethod.PIX)), intents)
        onNodeWithTag(ReceiptConfigurationTags.Activate).assertDoesNotExist()
    }
}
