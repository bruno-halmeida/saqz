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
class ReceiptWalletScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun exactBalanceAndReceivablesAndSeparateConsentAreVisible() {
        val state = mutableStateOf(ready)
        val intents = mutableListOf<ReceiptWalletIntent>()
        compose.setContent { SaqzTheme { ReceiptWalletScreen(state.value, intents::add, {}) } }
        compose.onNodeWithTag(ReceiptWalletTags.Balance).assertTextEquals("Disponível para saque: R$\u00a0123,45")
        compose.onNodeWithTag(ReceiptWalletTags.Pending).assertTextEquals("Recebíveis pendentes: R$\u00a067,89")
        capture("resumo")
        compose.onNodeWithTag(ReceiptWalletTags.Withdraw).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Valor solicitado: R$\u00a0123,45").assertIsDisplayed()
        compose.onNodeWithTag(ReceiptWalletTags.Accept).performClick()
        assertEquals(listOf(ReceiptWalletIntent.Accept(true)), intents)
        compose.runOnIdle { state.value = ready.copy(accepted = true) }
        compose.onNodeWithTag(ReceiptWalletTags.Withdraw).performClick()
        assertEquals(ReceiptWalletIntent.Withdraw, intents.last()); capture("confirmacao")
    }
    @Test fun unknownWithdrawalOnlyAllowsRecoveryAndShowsActualCompletionFee() {
        val state = mutableStateOf(ready.copy(attempt = WalletAttempt("actor", "account", "request", "WITHDRAW", 12345, "bank")))
        compose.setContent { SaqzTheme { ReceiptWalletScreen(state.value, {}, {}) } }
        compose.onNodeWithTag(ReceiptWalletTags.Withdraw).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Valor do saque em reais", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag(ReceiptWalletTags.Recover).performScrollTo().assertIsEnabled(); capture("recuperacao")
        compose.runOnIdle {
            state.value = ready.copy(withdrawal = ReceiptWithdrawal("w", "request", "bank", 12345, 173, "COMPLETED", null))
        }
        compose.onNodeWithText("Saque concluído: R$\u00a0123,45").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tarifa informada pelo provedor: R$\u00a01,73").assertIsDisplayed(); capture("concluido")
    }
    private fun capture(name: String) {
        val directory = File("/tmp/saqz-wallet-visual").apply { mkdirs() }
        compose.onRoot().captureRoboImage(File(directory, "$name.png").absolutePath)
    }
    private val ready = ReceiptWalletState(loading = false,
        accounts = listOf(ReceiptAccount("account", AccountRegistration.APPROVED, false)), accountId = "account",
        balance = ReceiptWalletBalance("account", 12345, 6789, "2026-09-13"),
        destinations = listOf(ReceiptBankDestination("bank", "001", "CHECKING", "T***", "1234", "1234", "1234", false)),
        destinationId = "bank", amount = "123,45")
}
