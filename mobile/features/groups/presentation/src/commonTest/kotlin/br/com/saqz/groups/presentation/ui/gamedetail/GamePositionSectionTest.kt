package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameGuestUi
import br.com.saqz.groups.presentation.gamedetail.GamePositionPickerUi
import br.com.saqz.groups.presentation.gamedetail.GamePositionPurpose
import kotlin.test.Test
import kotlin.test.assertEquals

/** DEB-102 (b) a (e): o que a tela mostra de vaga por posição. */
@OptIn(ExperimentalTestApi::class)
class GamePositionSectionTest {
    private val game = GameDetailPreviewData.positionsGame

    @Test
    fun confirmadosVemAgrupadosPorPosicaoComOQueFalta() = runComposeUiTest {
        setScreen(game)

        // 2/2 ponteiros(as) completo, 1/2 centrais falta 1, 1/1 levantador completo.
        onNodeWithTag(GamePositionTags.header(AthletePosition.PONTA)).performScrollTo()
        onNodeWithText("2/2 ponteiros(as)").assertExists()
        onNodeWithText("1/2 centrais").assertExists()
        onNodeWithText("falta 1").assertExists()
        onNodeWithText("1/1 levantadores(as)").assertExists()
        onNodeWithText("Rafa Dias").assertExists()
    }

    @Test
    fun jogoSemPosicoesContinuaComAListaCorrida() = runComposeUiTest {
        setScreen(GameDetailPreviewData.admin)
        onAllNodesWithTag(GamePositionTags.header(AthletePosition.PONTA)).assertCountEquals(0)
    }

    @Test
    fun seletorMostraAsPosicoesComSugestaoEOBotaoSoComEscolha() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(
            game.copy(positionPicker = GamePositionPickerUi(GamePositionPurpose.Self, suggested = AthletePosition.CENTRAL, selected = AthletePosition.CENTRAL)),
            intents::add,
        )

        onNodeWithTag(GamePositionTags.choice("game-position", AthletePosition.CENTRAL)).assertIsSelected()
        onNodeWithText("Do seu perfil").assertDoesNotExist()
        onNodeWithTag(GamePositionTags.choice("game-position", AthletePosition.PONTA)).performClick()
        onNodeWithTag(GamePositionTags.Submit).performClick()

        assertEquals<List<GameDetailIntent>>(
            listOf(GameDetailIntent.SelectPosition(AthletePosition.PONTA), GameDetailIntent.ConfirmPosition),
            intents,
        )
    }

    @Test
    fun seletorSemEscolhaNaoConfirma() = runComposeUiTest {
        setScreen(game.copy(positionPicker = GamePositionPickerUi(GamePositionPurpose.Self, suggested = null, selected = null)))
        onNodeWithTag(GamePositionTags.Submit).assertIsNotEnabled()
    }

    @Test
    fun posicaoCheiaMostraAEsperaNaLinhaDoSeletor() = runComposeUiTest {
        setScreen(game.copy(positionPicker = GamePositionPickerUi(GamePositionPurpose.Self, null, null)))
        onNodeWithText("2/2 · cheia · 1 na espera").assertExists()
        onNodeWithText("1/2 · falta 1").assertExists()
    }

    @Test
    fun folhaDoConvidadoPedeAPosicao() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(
            game.copy(guest = GameGuestUi(visible = true, enabled = true, sheetOpen = true, name = "Rafa", position = AthletePosition.CENTRAL)),
            intents::add,
        )
        onNodeWithTag(GamePositionTags.choice(GameGuestTags.PositionPrefix, AthletePosition.CENTRAL)).assertIsSelected()
        onNodeWithTag(GamePositionTags.choice(GameGuestTags.PositionPrefix, AthletePosition.LEVANTADOR)).performClick()
        assertEquals<List<GameDetailIntent>>(listOf(GameDetailIntent.UpdateGuestPosition(AthletePosition.LEVANTADOR)), intents)
    }

    @Test
    fun forcarEntradaAparecePraGestorSoNaPosicaoCheia() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(game.copy(header = game.header?.copy(responseOpen = true)), intents::add)

        onNodeWithTag(GamePositionTags.force("wait-2")).performScrollTo().performClick()
        assertEquals<List<GameDetailIntent>>(listOf(GameDetailIntent.RequestForceEntry("wait-2")), intents)
    }

    @Test
    fun atletaComumNaoVeForcarEntrada() = runComposeUiTest {
        setScreen(game.copy(isAdmin = false, waitlist = game.waitlist.map { it.copy(canRemove = false) }))
        onAllNodesWithTag(GamePositionTags.force("wait-2")).assertCountEquals(0)
    }

    @Test
    fun folhaDeForcarConfirmaComOMotivoPadrao() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(
            game.copy(
                positionPicker = GamePositionPickerUi(
                    GamePositionPurpose.Force("wait-2", "Duda Alves", AthletePosition.PONTA),
                    suggested = null,
                    selected = AthletePosition.PONTA,
                ),
            ),
            intents::add,
        )
        onNodeWithText("Forçar a entrada de Duda Alves?").assertExists()
        onNodeWithTag(GamePositionTags.ForceConfirm).performClick()
        assertEquals<List<GameDetailIntent>>(listOf(GameDetailIntent.ConfirmForceEntry("Entrada forçada pelo gestor")), intents)
    }

    @Test
    fun ajusteDeTotalSomeEmJogoComPosicoes() = runComposeUiTest {
        setScreen(game)
        onAllNodesWithTag(GameWaitlistTags.CapacityAction).assertCountEquals(0)
    }

    @Test
    fun ajusteDeTotalContinuaEmJogoSemPosicoes() = runComposeUiTest {
        setScreen(GameDetailPreviewData.admin)
        onNodeWithTag(GameWaitlistTags.CapacityAction).performScrollTo().assertExists()
    }

    private fun ComposeUiTest.setScreen(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit = {}) = setContent {
        SaqzTheme { GameDetailScreen(state, {}, onIntent) }
    }
}
