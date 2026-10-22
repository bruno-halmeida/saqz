package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
import br.com.saqz.groups.presentation.gamedetail.GameNoShowUi
import kotlin.test.Test
import kotlin.test.assertEquals

/** DEB-128 (Ver acerto, abas do cancelado) e DEB-129 (Faltou) na tela do jogo. */
@OptIn(ExperimentalTestApi::class)
class GameDetailPastScreenTest {
    private val completedAdmin = GameDetailPreviewData.admin.copy(
        header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Completed, responseOpen = false),
        confirmedRoster = GameDetailPreviewData.confirmed,
        waitlist = emptyList(),
        response = GameDetailPreviewData.athlete.response.copy(visible = false),
        noShow = GameNoShowUi(until = "4 de agosto", gameDay = "terça, 28 de julho", feeLabel = "R$ 25,00"),
    )
    private val completedAthlete = completedAdmin.copy(isAdmin = false, noShow = GameNoShowUi())

    @Test
    fun realizadoAcertadoMostraVerAcerto() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(completedAdmin.copy(settlementPending = false)) { intents += it }

        onNodeWithTag(GameDetailTags.SettleView).assertTextContains("Ver acerto").performClick()
        onAllNodesWithTag(GameDetailTags.Settle).assertCountEquals(0)
        assertEquals(GameDetailIntent.OpenSettlement, intents.last())
    }

    @Test
    fun realizadoComPendenciaContinuaEncerrarEAcertar() = runComposeUiTest {
        setScreen(completedAdmin.copy(settlementPending = true))

        onNodeWithTag(GameDetailTags.Settle).assertTextContains("Encerrar jogo e acertar")
        onAllNodesWithTag(GameDetailTags.SettleView).assertCountEquals(0)
    }

    @Test
    fun canceladoTemAbasConfirmadosENaoIam() = runComposeUiTest {
        setScreen(
            GameDetailPreviewData.athlete.copy(
                header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Cancelled, responseOpen = false),
                waitlist = emptyList(),
            ),
        )

        onNodeWithText("Confirmados").assertExists()
        onNodeWithText("Não iam").assertExists()
        onAllNodesWithText("Vão").assertCountEquals(0)
        onAllNodesWithText("Não vão").assertCountEquals(0)
    }

    @Test
    fun gestorSemFaltaNaoTemAbaFaltaram() = runComposeUiTest {
        setScreen(completedAdmin)

        onAllNodesWithTag(GameDetailTags.TabNoShow).assertCountEquals(0)
        onNodeWithText("Jogaram").assertExists()
        onNodeWithText("Não foram").assertExists()
    }

    @Test
    fun gestorComFaltaVeJogaramFaltaramNaoForam() = runComposeUiTest {
        val marked = completedAdmin.copy(
            confirmedRoster = completedAdmin.confirmedRoster.map { if (it.id == "5") it.copy(noShow = true) else it },
        )
        setScreen(marked)

        // Quem faltou sai de Jogaram: 9 confirmados, 8 jogaram.
        onNodeWithTag(GameDetailTags.TabGoing).assertTextContains("8")
        onAllNodesWithText("Felipe Nunes").assertCountEquals(0)

        onNodeWithTag(GameDetailTags.TabNoShow).performClick()
        onNodeWithText("Felipe Nunes").assertExists()
        onNodeWithTag(GameDetailTags.noShowTag("5")).assertExists()
        onNodeWithText("Faltou").assertExists()
        onNodeWithTag(GameDetailTags.NoShowFooter).performScrollTo()
            .assertTextContains("Só você e os admins veem quem faltou. Dá para marcar até 4 de agosto.")
        onNodeWithTag(GameDetailTags.more("5")).assertExists()
    }

    @Test
    fun prazoVencidoTiraOMenuEOPrazoDoRodape() = runComposeUiTest {
        val marked = completedAdmin.copy(
            confirmedRoster = completedAdmin.confirmedRoster.map { if (it.id == "5") it.copy(noShow = true) else it },
            noShow = GameNoShowUi(until = null),
        )
        setScreen(marked)

        onNodeWithTag(GameDetailTags.TabNoShow).performClick()
        onAllNodesWithTag(GameDetailTags.more("5")).assertCountEquals(0)
        onNodeWithTag(GameDetailTags.NoShowFooter).assertTextContains("Só você e os admins veem quem faltou.")
    }

    @Test
    fun atletaNaoVeFaltaNemMenu() = runComposeUiTest {
        setScreen(completedAthlete)

        onAllNodesWithTag(GameDetailTags.TabNoShow).assertCountEquals(0)
        onNodeWithText("Felipe Nunes").assertExists()
        onAllNodesWithTag(GameDetailTags.more("5")).assertCountEquals(0)
    }

    @Test
    fun folhaDeMarcarFaltaComDiariaEOToqueMarca() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(completedAdmin.copy(personActionsId = "5")) { intents += it }

        onNodeWithText("Avulso · confirmou para terça, 28 de julho").assertExists()
        onNodeWithText(
            "Só você e os admins veem a falta, e a pessoa não recebe aviso. A diária de R$ 25,00 continua; no acerto você decide se cobra.",
        ).assertExists()
        onNodeWithTag(GameDetailTags.noShowMark("5")).performClick()

        assertEquals(GameDetailIntent.SetNoShow("5", marked = true), intents.last())
    }

    @Test
    fun mensalistaNaoTemAFraseDaDiaria() = runComposeUiTest {
        setScreen(completedAdmin.copy(personActionsId = "1"))

        onNodeWithText("Só você e os admins veem a falta, e a pessoa não recebe aviso.").assertExists()
        onAllNodesWithText("A diária", substring = true).assertCountEquals(0)
    }

    @Test
    fun quemFaltouTemDesfazer() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        val marked = completedAdmin.copy(
            confirmedRoster = completedAdmin.confirmedRoster.map { if (it.id == "5") it.copy(noShow = true) else it },
            personActionsId = "5",
        )
        setScreen(marked) { intents += it }

        onNodeWithTag(GameDetailTags.noShowUndo("5")).performClick()

        assertEquals(GameDetailIntent.SetNoShow("5", marked = false), intents.last())
    }

    private fun ComposeUiTest.setScreen(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit = {}) = setContent {
        SaqzTheme { GameDetailScreen(state, {}, onIntent) }
    }
}
