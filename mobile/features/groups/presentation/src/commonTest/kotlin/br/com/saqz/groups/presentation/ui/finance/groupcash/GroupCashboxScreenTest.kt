package br.com.saqz.groups.presentation.ui.finance.groupcash

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupCashboxScreenTest {
    @Test
    fun `loaded state exposes cashbox sections and actions`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState, intents::add)

        onNodeWithText("SALDO DO GRUPO").assertExists()
        onNodeWithTag(GroupCashboxTags.Monthly).assertExists()
        onNodeWithTag(GroupCashboxTags.Debtors).assertExists()
        onNodeWithTag(GroupCashboxTags.Pix).assertExists()
        onNodeWithText("Camila").assertExists()
        onNodeWithText("Recebi").assertExists()
        onNodeWithTag(GroupCashboxTags.ChargeMissing).assertIsEnabled()

        onNodeWithTag(GroupCashboxTags.Register).performClick()
        onNodeWithText("Cobrar").performClick()
        assertEquals(
            listOf<GroupCashboxIntent>(GroupCashboxIntent.Register, GroupCashboxIntent.ChargeIndividual("charge-1")),
            intents,
        )
    }

    @Test
    fun `empty state shows the requested first launch copy and CTA`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(cashboxEmpty = true, debtors = emptyList()), intents::add)

        onNodeWithTag(GroupCashboxTags.Empty).assertExists()
        onNodeWithText("Comece definindo a mensalidade ou registrando o aluguel da quadra").assertExists()
        onNodeWithText("Registrar primeiro lançamento").performClick()
        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.Register), intents)
    }

    @Test
    fun `missing Pix disables charge CTAs and explains where to configure it`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(pix = null), intents::add)

        onNodeWithText("Cadastre o Pix do grupo em Editar grupo").assertExists()
        onNodeWithTag(GroupCashboxTags.ChargeMissing).assertIsNotEnabled()
        onNodeWithTag(GroupCashboxTags.OverdueCharge).assertIsNotEnabled()
        onNodeWithTag(GroupCashboxTags.chargeIndividual("charge-1")).assertIsNotEnabled()
        assertEquals(emptyList(), intents)
    }

    @Test
    fun `no pending debtors disables charge missing CTA even with Pix`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(debtors = emptyList()), intents::add)

        onNodeWithTag(GroupCashboxTags.ChargeMissing).assertIsNotEnabled()
        assertEquals(emptyList(), intents)
    }

    @Test
    fun `overdue state shows named members and active charge CTA`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(
            loadedState.copy(
                overdueBanner = OverdueBannerUi(
                    message = "Camila, Pedro e Thiago estão com julho em aberto",
                    monthLabel = "julho de 2026",
                ),
            ),
            intents::add,
        )

        onNodeWithTag(GroupCashboxTags.Overdue).assertExists()
        onNodeWithText("Camila, Pedro e Thiago estão com julho em aberto").assertExists()
        onNodeWithTag(GroupCashboxTags.OverdueCharge).assertIsEnabled()
        onNodeWithTag(GroupCashboxTags.OverdueCharge).performClick()
        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.ChargeMissing), intents)
    }

    @Test
    fun `load failure shows retry state`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(GroupCashboxState(isLoading = false, loadFailed = true), intents::add)

        onNodeWithTag(GroupCashboxTags.Failure).assertExists()
        onNodeWithText("Verifique sua conexão e tente de novo.").assertExists()
        onNodeWithText("Tentar novamente").performClick()
        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.Retry), intents)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        state: GroupCashboxState,
        onIntent: (GroupCashboxIntent) -> Unit,
    ) = setContent {
        SaqzTheme {
            GroupCashboxScreen(state = state, onBack = {}, onIntent = onIntent)
        }
    }

    private companion object {
        val loadedState = GroupCashboxState(
            isLoading = false,
            groupName = "Vôlei do CERET",
            monthKey = "2026-08",
            monthLabel = "agosto de 2026",
            monthlyMembersLabel = "18 mensalistas",
            balanceLabel = "R$\u00A0380,00",
            receivedLabel = "R$\u00A070.000,00",
            openLabel = "R$\u00A056.000,00",
            expensesLabel = "R$\u00A012.000,00",
            monthlyProgressLabel = "10/18",
            monthlyProgress = 10f / 18f,
            paidMonthlyCount = 10,
            openMonthlyCount = 8,
            monthlyTotalCount = 18,
            debtors = listOf(
                DebtorUi("charge-1", "member-1", "Camila", "Venceu em 10/07", "R$\u00A07.000,00", 7000L, 3L, "2026-07"),
            ),
            overdueBanner = OverdueBannerUi("Camila está com julho em aberto", "julho de 2026"),
            pix = PixUi("pix@saqz.com", "Vôlei do CERET"),
        )
    }
}
