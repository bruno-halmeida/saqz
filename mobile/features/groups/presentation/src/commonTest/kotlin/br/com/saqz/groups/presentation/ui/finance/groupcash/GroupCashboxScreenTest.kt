package br.com.saqz.groups.presentation.ui.finance.groupcash

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.finance.sheets.FinanceSheetsTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupCashboxScreenTest {
    @Test
    fun `loaded state shows the blue balance the score and the due list without the pix card`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState, intents::add)

        onNodeWithText("SALDO DO CAIXA").assertExists()
        onNodeWithText("Inclui o saldo inicial de R$\u00A0250").assertExists()
        onNodeWithTag(GroupCashboxTags.TabIn).assertExists()
        onNodeWithTag(GroupCashboxTags.TabOut).assertExists()
        onNodeWithTag(GroupCashboxTags.TabDue).assertExists()
        onNodeWithTag(GroupCashboxTags.Monthly).assertExists()
        onNodeWithText("Mensalidades de agosto").assertExists()
        onNodeWithText("Camila").assertExists()
        onNodeWithText("pix@saqz.com").assertDoesNotExist()

        onNodeWithTag(GroupCashboxTags.Income).performClick()
        onNodeWithTag(GroupCashboxTags.Expense).performClick()
        onNodeWithTag(GroupCashboxTags.received("charge-1")).performClick()
        onNodeWithTag(GroupCashboxTags.debtorMore("charge-1")).performClick()
        onNodeWithTag(GroupCashboxTags.TabOut).performClick()
        assertEquals(
            listOf(
                GroupCashboxIntent.NewIncome,
                GroupCashboxIntent.NewExpense,
                GroupCashboxIntent.OpenReceipt("charge-1"),
                GroupCashboxIntent.OpenDebtorMenu("charge-1"),
                GroupCashboxIntent.SelectTab(CashboxTab.Out),
            ),
            intents,
        )
    }

    @Test
    fun `copied group Pix shows the notice DEB-151`() = runComposeUiTest {
        setScreen(loadedState.copy(pixCopied = true), {})
        onNodeWithText("Pix do grupo copiado.").assertExists()
    }

    @Test
    fun `debtor menu has charge waive only for who withdrew and cancel`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(debtorMenuChargeId = "charge-1"), intents::add)

        onNodeWithTag(GroupCashboxTags.chargeWaive("charge-1")).assertDoesNotExist()
        onNodeWithTag(GroupCashboxTags.chargeIndividual("charge-1")).performClick()
        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.ChargeIndividual("charge-1")), intents)
    }

    @Test
    fun `out tab lists the month exits with the red amount`() = runComposeUiTest {
        setScreen(
            loadedState.copy(
                tab = CashboxTab.Out,
                monthOut = listOf(LedgerUi("e1", "Quadra", "Terça, 27/10", "\u2212R$\u00A0160", out = true)),
            ),
            {},
        )

        onNodeWithText("Saídas de agosto").assertExists()
        onNodeWithText("Quadra").assertExists()
        onNodeWithText("\u2212R$\u00A0160").assertExists()
        onNodeWithTag(GroupCashboxTags.Monthly).assertDoesNotExist()
    }

    @Test
    fun `cashbox menu has opening balance and monthly charges and says where the pix went`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(menuOpen = true), intents::add)

        onNodeWithText("O Pix do grupo fica na página do grupo.").assertExists()
        onNodeWithText("R$\u00A0250 · antes do Saqz").assertExists()
        onNodeWithTag(GroupCashboxTags.MenuOpening).performClick()
        onNodeWithTag(GroupCashboxTags.GenerateMonthly).performClick()
        assertEquals(
            listOf<GroupCashboxIntent>(GroupCashboxIntent.OpenOpeningBalance, GroupCashboxIntent.GenerateMonthly),
            intents,
        )
    }

    @Test
    fun `uncharged strip shows the count and opens the review`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(uncharged = UnchargedPendingUi(6, 2, "outubro", null, null)), intents::add)

        onNodeWithText("6 jogaram sem cobrança").assertExists()
        onNodeWithText("Em 2 jogos de outubro").assertExists()
        onNodeWithTag(GroupCashboxTags.UnchargedReview).performClick()
        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.ReviewUncharged), intents)
    }

    @Test
    fun `opening sheet warns about a version conflict`() = runComposeUiTest {
        setScreen(loadedState.copy(openingSheetOpen = true, openingConflict = true, openingInput = "300,00"), {})

        onNodeWithText("Alguém mudou o saldo inicial. O valor novo já está aqui.").assertExists()
    }

    @Test
    fun `zeroed cashbox asks how much the group has with save and start from zero`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(
            loadedState.copy(
                cashboxEmpty = true,
                debtors = emptyList(),
                openingLoaded = true,
                openingBalanceCents = null,
                openingVersion = "\"0\"",
            ),
            intents::add,
        )

        onNodeWithTag(GroupCashboxTags.OpeningQuestion).assertExists()
        onNodeWithText("Quanto o grupo já tem em caixa?").assertExists()
        onNodeWithText("Nada lançado ainda").assertExists()
        onNodeWithTag(GroupCashboxTags.TabDue).assertDoesNotExist()
        onNodeWithTag(GroupCashboxTags.OpeningSave).performClick()
        onNodeWithTag(GroupCashboxTags.OpeningZero).performClick()
        assertEquals(
            listOf<GroupCashboxIntent>(GroupCashboxIntent.SaveOpeningBalance, GroupCashboxIntent.StartFromZero),
            intents,
        )
    }

    @Test
    fun `pending charges without a Pix key show the notice with a shortcut to the group settings`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(pix = null), intents::add)

        onNodeWithTag(GroupCashboxTags.PixKeyNotice).assertExists()
        onNodeWithText("Cadastre a chave Pix para seus atletas pagarem.").assertExists()
        onNodeWithTag(br.com.saqz.groups.presentation.ui.finance.PixKeyNoticeTags.Action).performClick()

        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.OpenPixSetup), intents)
    }

    @Test
    fun `the notice is gone with a Pix key or without pending charges`() = runComposeUiTest {
        setScreen(loadedState, {})
        onNodeWithTag(GroupCashboxTags.PixKeyNotice).assertDoesNotExist()
    }

    @Test
    fun `no pending charges hide the notice even without a key`() = runComposeUiTest {
        setScreen(loadedState.copy(pix = null, debtors = emptyList()), {})
        onNodeWithTag(GroupCashboxTags.PixKeyNotice).assertDoesNotExist()
    }

    @Test
    fun `notifications remain available without Pix`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(pix = null), intents::add)

        onNodeWithText("Cadastre o Pix do grupo em Editar grupo").assertDoesNotExist()
        onNodeWithTag(GroupCashboxTags.ChargeMissing).performScrollTo().performClick()
        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.ChargeMissing), intents)
    }

    @Test
    fun `no pending debtors hide the charge button and say everything was received`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(debtors = emptyList()), intents::add)

        onNodeWithTag(GroupCashboxTags.ChargeMissing).assertDoesNotExist()
        onNodeWithText("Ninguém devendo. Tudo recebido.").assertExists()
        assertEquals(emptyList(), intents)
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
    fun `withdrawn debtor shows the withdrew tag and the waive action in the menu`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(withdrewState.copy(debtorMenuChargeId = "charge-1"), intents::add)

        onNodeWithTag(GroupCashboxTags.chargeWithdrew("charge-1")).assertExists()
        onNodeWithText("Desistiu").assertExists()
        onNodeWithText("Recebi").assertExists()
        onNodeWithTag(GroupCashboxTags.chargeWaive("charge-1")).assertIsEnabled().performClick()

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
        onNodeWithText("Quem desistiu não paga esta diária. A pessoa não recebe aviso. Dá para desfazer depois.").assertExists()
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
    fun `every pending debtor has a cancel action even without having withdrawn`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(debtorMenuChargeId = "charge-1"), intents::add)

        onNodeWithTag(GroupCashboxTags.chargeWithdrew("charge-1")).assertDoesNotExist()
        onNodeWithTag(GroupCashboxTags.chargeCancel("charge-1")).assertIsEnabled().performClick()

        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.OpenCancel("charge-1")), intents)
    }

    @Test
    fun `cancel confirmation says the effect and confirms the cancel`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(cancelSheetChargeId = "charge-1"), intents::add)

        onNodeWithTag(FinanceSheetsTags.CancelCharge).assertExists()
        onNodeWithText("A cobrança sai do que falta receber e não entra no caixa. A pessoa não recebe aviso. Não dá para desfazer.").assertExists()
        onNodeWithTag(FinanceSheetsTags.CancelChargeConfirm).performClick()

        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.Cancel("charge-1")), intents)
    }

    @Test
    fun `cancel confirmation does not confirm while a write is in flight`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(cancelSheetChargeId = "charge-1", updatingChargeId = "charge-1"), intents::add)

        onNodeWithTag(FinanceSheetsTags.CancelChargeConfirm).assertIsNotEnabled()
    }

    @Test
    fun `cancel failure shows its own message`() = runComposeUiTest {
        setScreen(loadedState.copy(cancelFailed = true), {})

        onNodeWithTag(GroupCashboxTags.CancelFailure).assertExists()
        onNodeWithText("Não foi possível cancelar a cobrança. Tente novamente.").assertExists()
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
    fun `settled monthlies are listed with an undo action`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(settled = listOf(settled, settled.copy(chargeId = "settled-2", received = false))), intents::add)

        onNodeWithTag(GroupCashboxTags.Settled).performScrollTo().assertExists()
        onNodeWithText("Mensalidades baixadas").assertExists()
        onNodeWithText("Recebida").assertExists()
        onNodeWithText("Dispensada").assertExists()
        onNodeWithTag(GroupCashboxTags.undo("settled-1")).performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.OpenUndo("settled-1")), intents)
    }

    @Test
    fun `no settled section without settled monthlies`() = runComposeUiTest {
        setScreen(loadedState, {})

        onNodeWithTag(GroupCashboxTags.Settled).assertDoesNotExist()
    }

    @Test
    fun `undo confirmation says the money leaves the cashbox and sends the note`() = runComposeUiTest {
        val intents = mutableListOf<GroupCashboxIntent>()
        setScreen(loadedState.copy(settled = listOf(settled), undoSheetChargeId = "settled-1"), intents::add)

        onNodeWithTag(FinanceSheetsTags.Undo).assertExists()
        onNodeWithText("A cobrança volta a pendente e o valor sai do caixa. A pessoa não recebe aviso.").assertExists()
        onNode(hasSetTextAction(), useUnmergedTree = true).performTextInput("Lancei errado")
        onNodeWithTag(FinanceSheetsTags.UndoConfirm).performClick()

        assertEquals(listOf<GroupCashboxIntent>(GroupCashboxIntent.Undo("settled-1", "Lancei errado")), intents)
    }

    @Test
    fun `undo confirmation of a waived monthly does not talk about the cashbox`() = runComposeUiTest {
        setScreen(loadedState.copy(settled = listOf(settled.copy(received = false)), undoSheetChargeId = "settled-1"), {})

        onNodeWithText("A cobrança volta a pendente. A pessoa não recebe aviso.").assertExists()
    }

    @Test
    fun `undo confirmation cannot be confirmed again while writing`() = runComposeUiTest {
        setScreen(
            loadedState.copy(settled = listOf(settled), undoSheetChargeId = "settled-1", updatingChargeId = "settled-1"),
            {},
        )

        onNodeWithTag(FinanceSheetsTags.UndoConfirm).assertIsNotEnabled()
    }

    @Test
    fun `undo failure shows its own message`() = runComposeUiTest {
        setScreen(loadedState.copy(undoFailed = true), {})

        onNodeWithText("Não foi possível desfazer. Tente novamente.").performScrollTo().assertExists()
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
        val settled = SettledUi(
            chargeId = "settled-1",
            name = "Bia",
            referenceLabel = "Mensalista · agosto",
            amountLabel = "R$\u00A070,00",
            chargeVersion = 3L,
            received = true,
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
            openingBalanceCents = 25_000L,
            openingVersion = "\"2\"",
            openingLoaded = true,
            monthInLabel = "R$\u00A0880",
            monthOutLabel = "R$\u00A0640",
            dueLabel = "R$\u00A070",
        ).let { it.copy(debtors = it.debtors.map { d -> d.copy(month = "2026-08") }) }
    }
}
