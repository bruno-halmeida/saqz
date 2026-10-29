package br.com.saqz.groups.presentation.ui.finance.settlement

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.presentation.ui.finance.sheets.FinanceSheetsTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GameSettlementScreenTest {
    @Test
    fun `load failure replaces the settlement with retry`() = runComposeUiTest {
        setScreen(summaryState.copy(loadFailed = true), {})
        onNodeWithTag(GameSettlementTags.Failure).assertExists()
        onNodeWithTag(GameSettlementTags.Diarists).assertDoesNotExist()
    }

    @Test
    fun `blue header with played paid and due then diaries monthly line exits and charge footer`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(progressState, intents::add)

        onNodeWithText("JOGO ENCERRADO").assertExists()
        onNodeWithText("Terça, 04/08").assertExists()
        onNodeWithText("19h30 · CERET — Quadra 2").assertExists()
        onNodeWithText("Jogaram").assertExists()
        onNodeWithText("Pagaram").assertExists()
        onNodeWithText("/2").assertExists()
        onNodeWithText("Diárias · R$\u00A070").assertExists()
        onNodeWithText("1 de 2 pagaram").assertExists()
        onNodeWithText("3 · cobertos pela mensalidade").performScrollTo().assertExists()
        onNodeWithText("Lançar").performScrollTo().performClick()
        onNodeWithTag(GameSettlementTags.ChargeMissing).performScrollTo().performClick()
        onNodeWithTag(GameSettlementTags.receipt("pending-1")).performScrollTo().performClick()
        onNodeWithTag(GameSettlementTags.more("pending-1")).performClick()

        assertEquals(
            listOf(
                GameSettlementIntent.OpenCourtExpense,
                GameSettlementIntent.ChargeMissing,
                GameSettlementIntent.OpenReceipt("pending-1"),
                GameSettlementIntent.OpenMenu("pending-1"),
            ),
            intents,
        )
    }

    @Test
    fun `uncharged section shows the strip the tags and opens the review`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(
            progressState.copy(
                gameHadNoFee = true,
                uncharged = listOf(
                    UnchargedRowUi("caio/0", "Caio Lima", "Avulso", noShow = false, skipped = false),
                    UnchargedRowUi("marcos/0", "Marcos Lima", "Avulso", noShow = true, skipped = false),
                    UnchargedRowUi("rafa/0", "Rafa Nunes", "Avulso · saiu do grupo", noShow = false, skipped = true),
                ),
            ),
            intents::add,
        )

        onNodeWithText("2 jogaram sem cobrança").assertExists()
        onNodeWithText("O jogo não tinha valor por jogo").assertExists()
        onNodeWithText("Sem diária").performScrollTo().assertExists()
        onNodeWithText("Faltou").assertExists()
        onNodeWithText("Não cobrar").assertExists()
        onNodeWithTag(GameSettlementTags.UnchargedReview).performClick()

        assertEquals(listOf<GameSettlementIntent>(GameSettlementIntent.ReviewUncharged), intents)
    }

    @Test
    fun `not played section says waiting not released and withdrew with paid and due lines`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(
            progressState.copy(
                diarists = progressState.diarists + listOf(
                    diarist("rafa", "Rafa Nunes", ChargeStatus.Paid).copy(
                        notPlayed = NotPlayedReason.NotReleased, canUndo = true, cashMeta = "Avulso · Pix",
                    ),
                    diarist("marcos", "Marcos Lima", ChargeStatus.Pending).copy(notPlayed = NotPlayedReason.Withdrew),
                ),
                debtors = progressState.debtors + debtor("marcos", "Marcos Lima"),
            ),
            intents::add,
        )

        onNodeWithTag(GameSettlementTags.NotPlayed).performScrollTo().assertExists()
        onNodeWithText("Não liberado").assertExists()
        onNodeWithText("Desistiu").assertExists()
        onNodeWithText("Pagou R$\u00A070 · Pix").assertExists()
        onNodeWithText("Falta pagar R$\u00A070").assertExists()
        onNodeWithText("Cobrar os 1 que faltam").performScrollTo().assertExists()
        onNodeWithTag(GameSettlementTags.waive("marcos")).performScrollTo().performClick()
        onNodeWithTag(GameSettlementTags.undo("rafa")).performScrollTo().performClick()

        assertEquals(
            listOf<GameSettlementIntent>(GameSettlementIntent.OpenWaive("marcos"), GameSettlementIntent.OpenUndo("rafa")),
            intents,
        )
    }

    @Test
    fun `no-show diarist shows Faltou and the menu offers waive PRES-016`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        val noShowState = progressState.copy(
            debtors = listOf(debtor("pending-1", "Ana").copy(noShow = true)),
            diarists = progressState.diarists.map { if (it.chargeId == "pending-1") it.copy(noShow = true) else it },
            menuChargeId = "pending-1",
        )
        setScreen(noShowState, intents::add)

        onNodeWithTag(GameSettlementTags.noShow("pending-1")).assertExists()
        onNodeWithTag(GameSettlementTags.waive("pending-1")).assertIsEnabled().performClick()

        assertEquals(
            listOf(GameSettlementIntent.DismissMenu, GameSettlementIntent.OpenWaive("pending-1")),
            intents,
        )
    }

    @Test
    fun `menu of a diarist who did not withdraw has charge and cancel but no waive`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(progressState.copy(menuChargeId = "pending-1"), intents::add)

        onNodeWithTag(GameSettlementTags.waive("pending-1")).assertDoesNotExist()
        onNodeWithTag(GameSettlementTags.charge("pending-1")).assertExists()
        onNodeWithTag(GameSettlementTags.cancel("pending-1")).performClick()

        assertEquals(listOf(GameSettlementIntent.DismissMenu, GameSettlementIntent.OpenCancel("pending-1")), intents)
    }

    @Test
    fun `waive confirmation says nobody is notified and confirms the waive`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(withdrewState.copy(waiveSheetChargeId = "pending-1"), intents::add)

        onNodeWithTag(FinanceSheetsTags.Waive).assertExists()
        onNodeWithText("Quem desistiu não paga esta diária. A pessoa não recebe aviso. Dá para desfazer depois.").assertExists()
        onNodeWithTag(FinanceSheetsTags.WaiveConfirm).performClick()

        assertEquals(listOf<GameSettlementIntent>(GameSettlementIntent.Waive("pending-1")), intents)
    }

    @Test
    fun `waive failure shows its own message`() = runComposeUiTest {
        setScreen(withdrewState.copy(waiveFailed = true), {})

        onNodeWithText("Não foi possível dispensar a cobrança. Tente novamente.").performScrollTo().assertExists()
        onNodeWithText("Não foi possível registrar o recebimento. Tente novamente.").assertDoesNotExist()
    }

    @Test
    fun `cancel confirmation says the effect and confirms the cancel`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(progressState.copy(cancelSheetChargeId = "pending-1"), intents::add)

        onNodeWithTag(FinanceSheetsTags.CancelCharge).assertExists()
        onNodeWithText("A cobrança sai do que falta receber e não entra no caixa. A pessoa não recebe aviso. Não dá para desfazer.").assertExists()
        onNodeWithTag(FinanceSheetsTags.CancelChargeConfirm).performClick()

        assertEquals(listOf<GameSettlementIntent>(GameSettlementIntent.Cancel("pending-1")), intents)
    }

    @Test
    fun `cancel failure shows its own message`() = runComposeUiTest {
        setScreen(progressState.copy(cancelFailed = true), {})

        onNodeWithTag(GameSettlementTags.CancelFailure).performScrollTo().assertExists()
        onNodeWithText("Não foi possível cancelar a cobrança. Tente novamente.").assertExists()
    }

    @Test
    fun `a charge marked for review shows the tag and a review action`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(reviewState, intents::add)

        onNodeWithTag(GameSettlementTags.review("paid-1")).performScrollTo().assertExists()
        onNodeWithText("Revisar").assertExists()
        onNodeWithTag(GameSettlementTags.review("paid-2")).assertDoesNotExist()
        onNodeWithTag(GameSettlementTags.reviewAction("paid-2")).assertDoesNotExist()
        onNodeWithTag(GameSettlementTags.reviewAction("paid-1")).performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf<GameSettlementIntent>(GameSettlementIntent.OpenReview("paid-1")), intents)
    }

    @Test
    fun `review confirmation reminds about the refund and resolves on confirm`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(reviewState.copy(reviewSheetChargeId = "paid-1"), intents::add)

        onNodeWithTag(FinanceSheetsTags.Review).assertExists()
        onNodeWithText("Para registrar a devolução no caixa, lance uma Saída.").assertExists()
        onNodeWithText("Marcar como resolvida").assertExists()
        onNodeWithTag(FinanceSheetsTags.ReviewConfirm).performClick()

        assertEquals(listOf<GameSettlementIntent>(GameSettlementIntent.ResolveReview("paid-1")), intents)
    }

    @Test
    fun `review confirmation is locked while the write is in flight`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(reviewState.copy(reviewSheetChargeId = "paid-1", updatingChargeId = "paid-1"), intents::add)

        onNodeWithTag(FinanceSheetsTags.ReviewConfirm).assertIsNotEnabled()
    }

    @Test
    fun `received and waived charges offer undo in the menu and electronic ones have no menu`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(undoState.copy(menuChargeId = "paid-1"), intents::add)

        onNodeWithTag(GameSettlementTags.more("waived-1")).performScrollTo().assertExists()
        onNodeWithTag(GameSettlementTags.more("paid-electronic")).assertDoesNotExist()
        onNodeWithTag(GameSettlementTags.undo("paid-1")).performClick()

        assertEquals(listOf(GameSettlementIntent.DismissMenu, GameSettlementIntent.OpenUndo("paid-1")), intents)
    }

    @Test
    fun `undo confirmation of a received charge says the money leaves the cashbox and sends the note`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(undoState.copy(undoSheetChargeId = "paid-1"), intents::add)

        onNodeWithTag(FinanceSheetsTags.Undo).assertExists()
        onNodeWithText("A cobrança volta a pendente e o valor sai do caixa. A pessoa não recebe aviso.").assertExists()
        onNode(hasSetTextAction(), useUnmergedTree = true).performTextInput("Lancei errado")
        onNodeWithTag(FinanceSheetsTags.UndoConfirm).performClick()

        assertEquals(listOf<GameSettlementIntent>(GameSettlementIntent.Undo("paid-1", "Lancei errado")), intents)
    }

    @Test
    fun `undo confirmation of a waived charge does not talk about the cashbox`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(undoState.copy(undoSheetChargeId = "waived-1"), intents::add)

        onNodeWithText("A cobrança volta a pendente. A pessoa não recebe aviso.").assertExists()
        onNodeWithTag(FinanceSheetsTags.UndoConfirm).performClick()

        assertEquals(listOf<GameSettlementIntent>(GameSettlementIntent.Undo("waived-1", "")), intents)
    }

    @Test
    fun `undo confirmation is locked while writing and with a one letter reason`() = runComposeUiTest {
        setScreen(undoState.copy(undoSheetChargeId = "paid-1", updatingChargeId = "paid-1"), {})
        onNodeWithTag(FinanceSheetsTags.UndoConfirm).assertIsNotEnabled()
    }

    @Test
    fun `undo confirmation refuses a one letter reason`() = runComposeUiTest {
        setScreen(undoState.copy(undoSheetChargeId = "paid-1"), {})

        onNode(hasSetTextAction(), useUnmergedTree = true).performTextInput("x")

        onNodeWithText("Use pelo menos 2 letras.").assertExists()
        onNodeWithTag(FinanceSheetsTags.UndoConfirm).assertIsNotEnabled()
    }

    @Test
    fun `undo failure shows its own message`() = runComposeUiTest {
        setScreen(undoState.copy(undoFailed = true), {})

        onNodeWithText("Não foi possível desfazer. Tente novamente.").performScrollTo().assertExists()
    }

    @Test
    fun `review failure shows its own message`() = runComposeUiTest {
        setScreen(reviewState.copy(reviewFailed = true), {})

        onNodeWithText("Não foi possível marcar como resolvida. Tente novamente.").performScrollTo().assertExists()
    }

    private fun ComposeUiTest.setScreen(
        state: GameSettlementState,
        onIntent: (GameSettlementIntent) -> Unit,
    ) = setContent {
        SaqzTheme {
            GameSettlementScreen(state = state, onBack = {}, onIntent = onIntent)
        }
    }

    private companion object {
        val header = GameSettlementHeaderUi(
            "04/08/2026 · 19:30", "CERET — Quadra 2", 8,
            title = "Terça, 04/08", meta = "19h30 · CERET — Quadra 2", completed = true,
        )
        val progressState = GameSettlementState(
            isLoading = false,
            groupName = "Vôlei do CERET",
            header = header,
            monthlyMemberCount = 3,
            paidDiaristCount = 2,
            totalDiaristCount = 6,
            pendingDiaristCount = 4,
            progress = 2f / 6f,
            totalDiaristCents = 120_000L,
            unitDiaristCents = 20_000L,
            receivedDiaristCents = 40_000L,
            pendingDiaristCents = 8_000L,
            debtors = listOf(debtor("pending-1", "Ana")),
            diarists = listOf(
                diarist("paid-1", "Bia", ChargeStatus.Paid),
                diarist("pending-1", "Ana", ChargeStatus.Pending),
            ),
            pix = br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi("pix@saqz.com", "Vôlei do CERET"),
        )
        val withdrewState = progressState.copy(
            debtors = listOf(debtor("pending-1", "Ana").copy(withdrew = true)),
            diarists = listOf(
                diarist("paid-1", "Bia", ChargeStatus.Paid),
                diarist("pending-1", "Ana", ChargeStatus.Pending).copy(withdrew = true),
            ),
        )
        val summaryState = GameSettlementState(
            isLoading = false,
            header = header,
            monthlyMemberCount = 3,
            paidDiaristCount = 2,
            totalDiaristCount = 2,
            progress = 1f,
            totalDiaristCents = 14_000L,
            unitDiaristCents = 7_000L,
            receivedDiaristCents = 14_000L,
            diarists = listOf(
                diarist("paid-1", "Bia", ChargeStatus.Paid),
                diarist("paid-2", "Caio", ChargeStatus.Paid),
            ),
        )

        val undoState = summaryState.copy(
            diarists = listOf(
                diarist("paid-1", "Bia", ChargeStatus.Paid).copy(canUndo = true),
                diarist("waived-1", "Caio", ChargeStatus.Waived).copy(canUndo = true),
                diarist("paid-electronic", "Dani", ChargeStatus.Paid),
                diarist("pending-1", "Edu", ChargeStatus.Pending),
            ),
        )

        val reviewState = summaryState.copy(
            diarists = listOf(
                diarist("paid-1", "Bia", ChargeStatus.Paid).copy(reviewRequired = true),
                diarist("paid-2", "Caio", ChargeStatus.Paid),
            ),
        )

        fun diarist(id: String, name: String, status: ChargeStatus) = GameSettlementDiaristUi(
            chargeId = id,
            memberId = id,
            name = name,
            meta = "Diarista",
            amountLabel = "R$\u00A07.000,00",
            amountCents = 7_000L,
            dueDate = "2026-08-04",
            chargeVersion = 1L,
            status = status,
            referenceLabel = "Diarista · jogo de 04/08",
        )

        fun debtor(id: String, name: String) = br.com.saqz.groups.presentation.ui.finance.groupcash.DebtorUi(
            chargeId = id,
            memberId = id,
            name = name,
            dueLabel = "Vence em 04/08",
            amountLabel = "R$\u00A08,00",
            amountCents = 800L,
            chargeVersion = 1L,
            month = null,
            referenceLabel = "Diarista · jogo de 04/08",
        )
    }
}
