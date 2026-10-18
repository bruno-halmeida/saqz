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
    fun `load failure replaces settlement actions with retry`() = runComposeUiTest {
        setScreen(summaryState.copy(loadFailed = true), {})
        onNodeWithTag(GameSettlementTags.Failure).assertExists()
        onNodeWithTag(GameSettlementTags.End).assertDoesNotExist()
        onNodeWithTag(GameSettlementTags.Summary).assertDoesNotExist()
        onNodeWithText("ENCERRADO").assertDoesNotExist()
    }

    @Test
    fun `confirmed summary exposes an enabled end action`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(summaryState, intents::add)

        onNodeWithTag(GameSettlementTags.End).performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf<GameSettlementIntent>(GameSettlementIntent.EndSettlement), intents)
    }

    @Test
    fun `last optimistic receipt does not announce a closed settlement`() = runComposeUiTest {
        setScreen(summaryState.copy(updatingChargeId = "paid-2"), {})

        onNodeWithText("ENCERRADO").assertDoesNotExist()
        onNodeWithTag(GameSettlementTags.Summary).assertDoesNotExist()
        onNodeWithTag(GameSettlementTags.End).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `5g shows progress monthly coverage manual copy and enabled charge`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(progressState, intents::add)

        onNodeWithText("JOGO ENCERRADO").assertExists()
        onNodeWithText("2 de 6 avulsos acertaram · faltam R$\u00A080,00").assertExists()
        onNodeWithText("3 mensalistas").assertExists()
        onNodeWithText("Já estão cobertos pela mensalidade.").assertExists()
        onNodeWithText("Toque em 'Recebi' quando o Pix ou o dinheiro chegar. Nada é cobrado automaticamente.").assertExists()
        onNodeWithTag(GameSettlementTags.ChargeMissing).assertIsEnabled()
        onNodeWithTag(GameSettlementTags.End).assertIsNotEnabled()

        onNodeWithTag(GameSettlementTags.ChargeMissing).performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf<GameSettlementIntent>(GameSettlementIntent.ChargeMissing), intents)
    }

    @Test
    fun `5i shows derived summary negative result and group cashbox action`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(summaryState, intents::add)

        onNodeWithText("ENCERRADO").assertExists()
        onNodeWithTag(GameSettlementTags.Summary).assertExists()
        onNodeWithText("2 avulsos", substring = true).assertExists()
        onAllNodesWithText("R$\u00A07.000,00", substring = true).assertCountEquals(2)
        onNodeWithText("-R$\u00A020,00").assertExists()
        onNodeWithText("A diferença saiu do saldo. Considere ajustar o valor do avulso.").assertExists()
        onAllNodesWithText("Recebido").assertCountEquals(2)
        onNodeWithTag(GameSettlementTags.Cashbox).performScrollTo().performClick()

        assertEquals(listOf<GameSettlementIntent>(GameSettlementIntent.OpenCashbox), intents)
    }

    @Test
    fun `withdrawn diarist shows the withdrew tag and a waive action beside received`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(withdrewState, intents::add)

        onNodeWithTag(GameSettlementTags.withdrew("pending-1")).performScrollTo().assertExists()
        onNodeWithText("Desistiu").assertExists()
        onNodeWithTag(GameSettlementTags.receipt("pending-1")).assertExists()
        onNodeWithTag(GameSettlementTags.waive("pending-1")).performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf<GameSettlementIntent>(GameSettlementIntent.OpenWaive("pending-1")), intents)
    }

    @Test
    fun `diarist who did not withdraw has neither the tag nor the waive action`() = runComposeUiTest {
        setScreen(progressState, {})

        onNodeWithTag(GameSettlementTags.receipt("pending-1")).performScrollTo().assertExists()
        onNodeWithTag(GameSettlementTags.waive("pending-1")).assertDoesNotExist()
        onNodeWithTag(GameSettlementTags.withdrew("pending-1")).assertDoesNotExist()
        onNodeWithText("Desistiu").assertDoesNotExist()
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
    fun `received and waived charges offer undo and pending or locked ones do not`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(undoState, intents::add)

        onNodeWithTag(GameSettlementTags.undo("paid-1")).performScrollTo().assertIsEnabled().performClick()
        onNodeWithTag(GameSettlementTags.undo("waived-1")).assertExists()
        onNodeWithTag(GameSettlementTags.undo("paid-electronic")).assertDoesNotExist()
        onNodeWithTag(GameSettlementTags.undo("pending-1")).assertDoesNotExist()

        assertEquals(listOf<GameSettlementIntent>(GameSettlementIntent.OpenUndo("paid-1")), intents)
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
        val header = GameSettlementHeaderUi("04/08/2026 · 19:30", "CERET — Quadra 2", 8)
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
            costCents = 16_000L,
            resultCents = -2_000L,
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
