package br.com.saqz.groups.presentation.ui.finance.sheets

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.finance.groupcash.DebtorUi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ChargeSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectingAnotherChargeKeepsPreviousSelectionAndUpdatesTotal() {
        show()
        recipient("pedro").performClick()
        recipient("ana").assertIsSelected()
        recipient("pedro").assertIsSelected()
        recipient("bia").assertIsNotSelected()
        compose.onNodeWithTag(FinanceSheetsTags.ChargeSelection)
            .assertTextEquals("Selecionadas: 2 · R$\u00A0100,00")
        recipient("ana").performClick()
        recipient("ana").assertIsNotSelected()
        recipient("pedro").assertIsSelected()
        compose.onNodeWithTag(FinanceSheetsTags.ChargeSelection)
            .assertTextEquals("Selecionadas: 1 · R$\u00A030,00")
    }

    @Test
    fun chargeAllSelectsEveryChargeAndClearDisablesSending() {
        show()
        compose.onNodeWithTag(FinanceSheetsTags.ChargeAll).performClick()
        listOf("ana", "pedro", "bia").forEach { recipient(it).assertIsSelected() }
        compose.onNodeWithTag(FinanceSheetsTags.ChargeSelection)
            .assertTextEquals("Selecionadas: 3 · R$\u00A0150,00")
        compose.onNodeWithTag(FinanceSheetsTags.ChargeSend).assertIsEnabled()
        compose.onNodeWithTag(FinanceSheetsTags.ChargeClear).performClick()
        listOf("ana", "pedro", "bia").forEach { recipient(it).assertIsNotSelected() }
        compose.onNodeWithTag(FinanceSheetsTags.ChargeSend).assertIsNotEnabled()
        recipient("bia").performClick()
        compose.onNodeWithTag(FinanceSheetsTags.ChargeSend).assertIsEnabled()
    }

    @Test
    fun sendsOnlySelectedIdsToNotificationFlow() {
        show()
        recipient("bia").performClick()
        compose.onNodeWithTag(FinanceSheetsTags.ChargeSend).performClick()
        compose.runOnIdle { assertEquals(listOf("ana", "bia"), sentIds); assertEquals(1, sends) }
    }

    @Test
    fun chargeAllSendsEverySelectedIdOnce() {
        show()
        compose.onNodeWithTag(FinanceSheetsTags.ChargeAll).performClick()
        compose.onNodeWithTag(FinanceSheetsTags.ChargeSend).performClick()
        compose.runOnIdle { assertEquals(listOf("ana", "pedro", "bia"), sentIds); assertEquals(1, sends) }
    }

    private var sentIds = emptyList<String>()
    private var sends = 0

    private fun recipient(id: String) = compose.onNodeWithTag(FinanceSheetsTags.chargeRecipient(id))

    private fun show() {
        compose.setContent {
            SaqzTheme {
                ChargeSheet(true, debtors, {}, { selected ->
                    sentIds = selected
                    sends++
                })
            }
        }
    }

    private val debtors = listOf(
        DebtorUi("ana", "member-ana", "Ana", "", "R$ 70,00", 7_000L, 1L, "2026-09"),
        DebtorUi("pedro", "member-pedro", "Pedro", "", "R$ 30,00", 3_000L, 1L, "2026-09"),
        DebtorUi("bia", "member-bia", "Bia", "", "R$ 50,00", 5_000L, 1L, "2026-09"),
    )
}
