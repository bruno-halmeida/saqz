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
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.ChargeStatus
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GameSettlementScreenTest {
    @Test
    fun `5g shows progress monthly coverage manual copy and enabled charge`() = runComposeUiTest {
        val intents = mutableListOf<GameSettlementIntent>()
        setScreen(progressState, intents::add)

        onNodeWithText("JOGO ENCERRADO").assertExists()
        onNodeWithText("2 de 6 diaristas acertaram · faltam R$\u00A080,00").assertExists()
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
        onNodeWithText("2 diaristas", substring = true).assertExists()
        onAllNodesWithText("R$\u00A07.000,00", substring = true).assertCountEquals(2)
        onNodeWithText("-R$\u00A020,00").assertExists()
        onNodeWithText("A diferença saiu do saldo. Considere ajustar o valor do diarista.").assertExists()
        onAllNodesWithText("Recebido").assertCountEquals(2)
        onNodeWithTag(GameSettlementTags.Cashbox).performScrollTo().performClick()

        assertEquals(listOf<GameSettlementIntent>(GameSettlementIntent.OpenCashbox), intents)
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
