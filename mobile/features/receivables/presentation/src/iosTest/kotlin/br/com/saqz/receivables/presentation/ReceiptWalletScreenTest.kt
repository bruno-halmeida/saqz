package br.com.saqz.receivables.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReceiptWalletScreenTest {
    @Test fun exactAmountsAndConsentRemainSeparateOnIos() = runComposeUiTest {
        val state = mutableStateOf(ReceiptWalletState(loading = false, accountId = "account",
            accounts = listOf(ReceiptAccount("account", AccountRegistration.APPROVED, false)),
            balance = ReceiptWalletBalance("account", 12345, 6789, "2026-09-13"), destinationId = "bank", amount = "123,45"))
        val intents = mutableListOf<ReceiptWalletIntent>()
        setContent { SaqzTheme { ReceiptWalletScreen(state.value, intents::add, {}) } }
        onNodeWithTag(ReceiptWalletTags.Balance).assertTextEquals("Disponível para saque: R$\u00a0123,45")
        onNodeWithTag(ReceiptWalletTags.Pending).assertTextEquals("Valores a receber: R$\u00a067,89")
        onNodeWithTag(ReceiptWalletTags.Withdraw).performScrollTo().assertIsNotEnabled()
        runOnIdle { state.value = state.value.copy(accepted = true) }
        onNodeWithTag(ReceiptWalletTags.Withdraw).performClick()
        assertEquals(listOf<ReceiptWalletIntent>(ReceiptWalletIntent.Withdraw), intents)
        runOnIdle { state.value = state.value.copy(attempt = WalletAttempt("actor", "account", "request", "WITHDRAW", 12345, "bank")) }
        onNodeWithTag(ReceiptWalletTags.Withdraw).assertIsNotEnabled()
        onNodeWithTag(ReceiptWalletTags.Recover).performScrollTo().assertIsEnabled()
    }
}
