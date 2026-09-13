package br.com.saqz.groups.presentation.ui.finance.groupcash

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.saqz.designsystem.theme.SaqzTheme
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
class ChargeApprovalEntryTest {
    @get:Rule val compose = createComposeRule()
    @Test fun explicitChargeEntryWorksWithoutManualPixKeyAndPreservesManualReceipt() {
        var opened: String? = null
        val state = GroupCashboxState(isLoading = false, groupName = "Grupo de teste", debtors = listOf(
            DebtorUi("charge", "payer", "Pessoa Teste", "20/09/2026", "R$ 100,00", 10000, 1, "2026-09")))
        compose.setContent { SaqzTheme { GroupCashboxScreen(state, {}, {}, onOpenChargePayment = { opened = it }) } }
        compose.onNodeWithTag(GroupCashboxTags.chargePayment("charge")).performScrollTo().assertIsEnabled().performClick()
        assertEquals("charge", opened)
        compose.onNodeWithText("Recebi").assertExists()
        val dir = File("../../../build/reports/charge-approval").apply { mkdirs() }
        compose.onNodeWithTag(GroupCashboxTags.Screen).captureRoboImage(File(dir, "caixa-entrada.png").path)
    }
}
