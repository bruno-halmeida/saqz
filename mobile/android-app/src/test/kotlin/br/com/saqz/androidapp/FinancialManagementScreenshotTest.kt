package br.com.saqz.androidapp

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.presentation.*
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class FinancialManagementScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ownerSelectsNamedAdministratorWithoutTechnicalIdentifierInput() {
        val intents = mutableListOf<FinancialManagementIntent>()
        compose.setContent { SaqzTheme { FinancialManagementScreen(FinancialManagementState(
            loading = false, role = ReceiptManagementRole.OWNER,
            candidates = listOf(ReceiptAdministrator("admin-id", "Ana Almeida", listOf("Futebol de sábado"))),
            terms = ReceiptTerms("2026-09", "Texto de exemplo para verificação visual."),
        ), intents::add, {}) } }
        compose.onNodeWithText("Ana Almeida · Futebol de sábado").performScrollTo().performClick()
        assertEquals(listOf(FinancialManagementIntent.DelegateUser("admin-id")), intents)
        compose.onNodeWithText("admin-id", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(FinancialManagementTags.Grant).performScrollTo().assertIsNotEnabled()
        capture("administradores")
    }

    @Test fun financialHomeOpensWalletAndRegistrationAndDisplaysPlanNotice() {
        var wallet = 0
        var registration = 0
        compose.setContent { SaqzTheme { ReceiptFinanceHomeScreen(ReceiptFinanceHomeState(
            notices = listOf(ReceiptNotice("plan", "Seu plano", "Consulte os recebimentos e as pendências da sua conta.")),
        ), {}, { wallet++ }, { registration++ }, {}) } }
        compose.onNodeWithTag("finance-home-wallet").performClick()
        compose.onNodeWithTag("finance-home-registration").performClick()
        assertEquals(1, wallet)
        assertEquals(1, registration)
        compose.onNodeWithText("Consulte os recebimentos e as pendências da sua conta.").assertIsDisplayed()
        capture("inicio")
    }

    private fun capture(name: String) {
        val directory = File("/tmp/saqz-management-visual").apply { mkdirs() }
        compose.onRoot().captureRoboImage(File(directory, "$name.png").absolutePath)
    }
}
