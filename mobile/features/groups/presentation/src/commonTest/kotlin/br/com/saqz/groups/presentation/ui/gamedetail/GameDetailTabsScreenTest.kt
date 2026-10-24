package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameReminderUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailResponseUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import kotlin.test.Test
import kotlin.test.assertEquals

/** DEB-116: placar-abas, listas por aba, menus e ações só do gestor na tela do jogo em tela cheia. */
@OptIn(ExperimentalTestApi::class)
class GameDetailTabsScreenTest {
    private val athlete = GameDetailPreviewData.athlete
    private val admin = GameDetailPreviewData.admin

    @Test
    fun trocarDeAbaMostraAListaCerta() = runComposeUiTest {
        setScreen(athlete)

        onNodeWithTag(GameDetailTags.TabGoing).assertIsSelected()
        onNodeWithText("Lucas Prado").assertExists()
        onAllNodesWithText("Caio Lima").assertCountEquals(0)

        onNodeWithTag(GameDetailTags.TabWaiting).performClick()
        onNodeWithTag(GameDetailTags.TabWaiting).assertIsSelected()
        onNodeWithText("Caio Lima").assertExists()
        onNodeWithText("2º").assertExists()
        onAllNodesWithText("Lucas Prado").assertCountEquals(0)

        onNodeWithTag(GameDetailTags.TabDeclined).performClick()
        onNodeWithText("Rafa Dias").assertExists()
        onAllNodesWithText("Caio Lima").assertCountEquals(0)
    }

    @Test
    fun atletaVeAAbaNaoVaoENumeros() = runComposeUiTest {
        setScreen(athlete)

        // 9/12 em Vão, 2 na espera, 3 que não vão: o atleta vê a aba Não vão como todo mundo.
        onNodeWithText("/12").assertExists()
        onNodeWithTag(GameDetailTags.TabDeclined).performClick()
        listOf("Rafa Dias", "Marina Costa", "Pedro Henrique").forEach { onNodeWithText(it).assertExists() }
    }

    @Test
    fun quemEstaNaEsperaAbreNaAbaNaEspera() = runComposeUiTest {
        setScreen(
            athlete.copy(
                response = GameDetailResponseUi(
                    visible = true,
                    status = AttendanceStatus.Waitlisted,
                    waitlistKind = HomeWaitlistKind.Reserva,
                    waitlistPosition = 2,
                ),
                waitlist = athlete.waitlist.mapIndexed { index, row -> row.copy(isYou = index == 1) },
            ),
        )

        onNodeWithTag(GameDetailTags.TabWaiting).assertIsSelected()
        onNodeWithText("Você").assertExists()
    }

    @Test
    fun servidorAntigoMostraSoAContagemDeQuemNaoVai() = runComposeUiTest {
        setScreen(athlete.copy(declinedRoster = null))
        onNodeWithTag(GameDetailTags.TabDeclined).performClick()

        onNodeWithText("3 pessoas avisaram que não vão.").assertExists()
    }

    @Test
    fun menuEAvisarSoParaOGestor() {
        runComposeUiTest {
            setScreen(athlete)
            onAllNodesWithTag(GameDetailTags.Menu).assertCountEquals(0)
            onAllNodesWithTag(GameDetailTags.NotifyPanel).assertCountEquals(0)
        }
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            setScreen(admin, intents::add)
            onNodeWithText("4 pessoas sem resposta").assertExists()
            onNodeWithTag(GameDetailTags.NotifyPending).performClick()
            onNodeWithTag(GameDetailTags.Menu).performClick()
            assertEquals<List<GameDetailIntent>>(listOf(GameDetailIntent.NotifyPending, GameDetailIntent.OpenGameMenu), intents)
        }
    }

    @Test
    fun avisarFicaDepoisDoEnvioParaMandarDeNovo() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(admin.copy(reminder = GameReminderUi(notifiedCount = 3)), intents::add)

        onNodeWithText("Lembrete enviado no Saqz para 3 pessoas.").assertExists()
        onNodeWithTag(GameDetailTags.NotifyPending).assertTextContains("Avisar de novo").performClick()
        assertEquals<List<GameDetailIntent>>(listOf(GameDetailIntent.NotifyPending), intents)
    }

    @Test
    fun comGrupoDoWhatsAppOAvisarFicaComTodoMundoRespondido() {
        val everyoneAnswered = admin.copy(attendance = admin.attendance?.copy(pending = 0), whatsAppGroupActive = true)
        runComposeUiTest {
            setScreen(everyoneAnswered)
            onNodeWithText("Todo mundo respondeu").assertExists()
            onNodeWithTag(GameDetailTags.NotifyPending).assertTextContains("Avisar")
        }
        runComposeUiTest {
            setScreen(everyoneAnswered.copy(reminder = GameReminderUi(notifiedCount = 0)))
            onNodeWithText("Lista enviada no grupo do WhatsApp.").assertExists()
        }
        runComposeUiTest {
            setScreen(everyoneAnswered.copy(whatsAppGroupActive = false))
            onAllNodesWithTag(GameDetailTags.NotifyPanel).assertCountEquals(0)
        }
    }

    @Test
    fun menuDoGestorLevaAsFolhasDeHoje() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(admin.copy(gameMenuOpen = true), intents::add)

        onNodeWithText("Jogo de terça, 28/07").assertExists()
        onNodeWithText("Hoje são 12 vagas").assertExists()
        onNodeWithTag(GameDetailTags.MenuEdit).performClick()
        onNodeWithTag(GameDetailTags.MenuCapacity).performClick()
        onNodeWithText("Quem confirmou, desistiu e quando").assertExists()
        onNodeWithTag(GameDetailTags.MenuHistory).performClick()
        onNodeWithTag(GameDetailTags.MenuCancel).performClick()

        assertEquals<List<GameDetailIntent>>(
            listOf(
                GameDetailIntent.Edit, GameDetailIntent.OpenCapacitySheet, GameDetailIntent.OpenHistory,
                GameDetailIntent.RequestCancel,
            ),
            intents,
        )
    }

    /** JOGO-012: o histórico também fica no ⋯ do jogo realizado e do cancelado. */
    @Test
    fun historicoNoMenuDoRealizadoEDoCancelado() {
        listOf(GameDetailStatusTone.Completed, GameDetailStatusTone.Cancelled).forEach { tone ->
            runComposeUiTest {
                val intents = mutableListOf<GameDetailIntent>()
                setScreen(admin.copy(gameMenuOpen = true, header = GameDetailPreviewData.header.copy(statusTone = tone)), intents::add)
                onNodeWithTag(GameDetailTags.MenuHistory).performClick()
                onAllNodesWithTag(GameDetailTags.MenuCancel).assertCountEquals(0)
                assertEquals<List<GameDetailIntent>>(listOf(GameDetailIntent.OpenHistory), intents)
            }
        }
    }

    @Test
    fun vagaAbertaSoETocavelParaOGestor() {
        runComposeUiTest {
            setScreen(athlete)
            onNodeWithTag(GameDetailTags.openSlot(9)).performScrollTo().assertHasNoClickAction()
            onNodeWithText("Pode ser a sua").assertExists()
        }
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            setScreen(admin, intents::add)
            onNodeWithTag(GameDetailTags.openSlot(9)).performScrollTo().assertHasClickAction().performClick()
            onAllNodesWithText("Pode ser a sua").assertCountEquals(0)
            assertEquals<List<GameDetailIntent>>(listOf(GameDetailIntent.OpenPlaceSheet), intents)
        }
    }

    @Test
    fun cincoUmVemEmSecoesComAVagaDaPosicao() = runComposeUiTest {
        setScreen(GameDetailPreviewData.positionsGame)

        onNodeWithText("Centrais").assertExists()
        onNodeWithText("Vaga de central").assertExists()
        onNodeWithText("Falta 1 central").assertExists()
        onNodeWithTag(GameDetailTags.TabWaiting).performClick()
        onNodeWithText("Ponteiros(as)").assertExists()
        onAllNodesWithText("1º").assertCountEquals(2)
    }

    @Test
    fun colocarNaEsperaSoNaEscolhaManual() {
        runComposeUiTest {
            setScreen(admin.copy(promotionMode = br.com.saqz.groups.domain.group.PromotionMode.FIFO))
            onNodeWithTag(GameDetailTags.TabWaiting).performClick()
            onAllNodesWithText("Colocar").assertCountEquals(0)
        }
        runComposeUiTest {
            setScreen(admin)
            onNodeWithTag(GameDetailTags.TabWaiting).performClick()
            onAllNodesWithText("Colocar").assertCountEquals(2)
        }
    }

    @Test
    fun rascunhoTemPublicarSemPlacarERealizadoTemEncerrar() {
        runComposeUiTest {
            setScreen(admin.copy(header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Draft)))
            onNodeWithText("Publicar jogo").assertExists()
            onNodeWithText("RASCUNHO").assertExists()
            onAllNodesWithTag(GameDetailTags.TabGoing).assertCountEquals(0)
        }
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            setScreen(admin.copy(header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Completed)), intents::add)
            onNodeWithText("JOGO REALIZADO").assertExists()
            onNodeWithText("Jogaram").assertExists()
            onNodeWithText("Não foram").assertExists()
            onAllNodesWithTag(GameDetailTags.TabWaiting).assertCountEquals(0)
            onNodeWithText("Encerrar jogo e acertar").performClick()
            assertEquals<List<GameDetailIntent>>(listOf(GameDetailIntent.OpenSettlement), intents)
        }
    }

    @Test
    fun tituloEQuadraFicamVisiveis() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(athlete, intents::add)

        onNodeWithText("Vôlei do CERET").assertExists()
        onNodeWithText("CERET — Quadra 2").assertExists()
        onNodeWithText("Restam 3 vagas").assertExists()
        onNodeWithTag(GameDetailTags.Map).performClick()
        assertEquals<List<GameDetailIntent>>(listOf(GameDetailIntent.OpenVenueMap), intents)
    }

    @Test
    fun semEnderecoNaoTemVerNoMapa() = runComposeUiTest {
        setScreen(athlete.copy(hero = GameDetailPreviewData.hero.copy(address = "")))
        onAllNodesWithTag(GameDetailTags.Map).assertCountEquals(0)
    }

    private fun ComposeUiTest.setScreen(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit = {}) = setContent {
        SaqzTheme { GameDetailScreen(state, {}, onIntent) }
    }
}
