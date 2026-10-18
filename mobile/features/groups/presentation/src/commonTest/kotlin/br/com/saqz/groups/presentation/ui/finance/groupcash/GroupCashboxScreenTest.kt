package br.com.saqz.groups.presentation.ui.finance.groupcash

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.finance.sheets.FinanceSheetsTags
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
    fun `notifications remain available without Pix`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(pix = null), intents::add)

        onNodeWithText("Cadastre o Pix do grupo em Editar grupo").assertDoesNotExist()
        onNodeWithTag(GroupCashboxTags.ChargeMissing).performClick()
        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.ChargeMissing), intents)
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

    @Test
    fun `withdrawn debtor shows the withdrew tag and a waive action beside received`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(withdrewState, intents::add)

        onNodeWithTag(GroupCashboxTags.chargeWithdrew("charge-1")).assertExists()
        onNodeWithText("Desistiu").assertExists()
        onNodeWithText("Recebi").assertExists()
        onNodeWithTag(GroupCashboxTags.chargeWaive("charge-1")).performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.OpenWaive("charge-1")), intents)
    }

    @Test
    fun `debtor who did not withdraw has neither the tag nor the waive action`() = runComposeUiTest {
        setScreen(loadedState, {})

        onNodeWithTag(GroupCashboxTags.chargeWithdrew("charge-1")).assertDoesNotExist()
        onNodeWithTag(GroupCashboxTags.chargeWaive("charge-1")).assertDoesNotExist()
        onNodeWithText("Desistiu").assertDoesNotExist()
    }

    @Test
    fun `waive confirmation says nobody is notified and confirms the waive`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(withdrewState.copy(waiveSheetChargeId = "charge-1"), intents::add)

        onNodeWithTag(FinanceSheetsTags.Waive).assertExists()
        onNodeWithText("Quem desistiu não paga esta diária. A pessoa não recebe aviso e não dá para desfazer.").assertExists()
        onNodeWithTag(FinanceSheetsTags.WaiveConfirm).performClick()

        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.Waive("charge-1")), intents)
    }

    @Test
    fun `waive failure shows its own message`() = runComposeUiTest {
        setScreen(withdrewState.copy(waiveFailed = true), {})

        onNodeWithTag(GroupCashboxTags.WaiveFailure).assertExists()
        onNodeWithText("Não foi possível dispensar a cobrança. Tente novamente.").assertExists()
    }

    @Test
    fun `charges for review are listed with the tag and a review action`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(reviews = listOf(review)), intents::add)

        onNodeWithTag(GroupCashboxTags.Reviews).performScrollTo().assertExists()
        onNodeWithText("Cobranças para revisar").assertExists()
        onNodeWithTag(GroupCashboxTags.review("review-1")).assertExists()
        onNodeWithTag(GroupCashboxTags.reviewAction("review-1")).performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.OpenReview("review-1")), intents)
    }

    @Test
    fun `no review section without marked charges`() = runComposeUiTest {
        setScreen(loadedState, {})

        onNodeWithTag(GroupCashboxTags.Reviews).assertDoesNotExist()
    }

    @Test
    fun `review confirmation resolves on confirm and is locked while writing`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(reviews = listOf(review), reviewSheetChargeId = "review-1"), intents::add)

        onNodeWithTag(FinanceSheetsTags.Review).assertExists()
        onNodeWithText("Marcar como resolvida").assertExists()
        onNodeWithTag(FinanceSheetsTags.ReviewConfirm).performClick()
        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.ResolveReview("review-1")), intents)
    }

    @Test
    fun `review confirmation cannot be confirmed again while writing`() = runComposeUiTest {
        setScreen(
            loadedState.copy(reviews = listOf(review), reviewSheetChargeId = "review-1", updatingChargeId = "review-1"),
            {},
        )

        onNodeWithTag(FinanceSheetsTags.ReviewConfirm).assertIsNotEnabled()
    }

    @Test
    fun `review failure shows its own message`() = runComposeUiTest {
        setScreen(loadedState.copy(reviewFailed = true), {})

        onNodeWithText("Não foi possível marcar como resolvida. Tente novamente.").performScrollTo().assertExists()
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
        val withdrewState get() = loadedState.copy(
            debtors = loadedState.debtors.map { it.copy(withdrew = true) },
        )
        val review = ReviewUi(
            chargeId = "review-1",
            name = "Camila",
            referenceLabel = "Diarista · jogo de 08/08 · paga",
            amountLabel = "R$\u00A030,00",
            chargeVersion = 5L,
        )
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
