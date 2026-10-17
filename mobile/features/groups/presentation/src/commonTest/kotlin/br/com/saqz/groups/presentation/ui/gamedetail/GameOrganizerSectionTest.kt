package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.presentation.gamedetail.GameDetailAttendanceUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameGuestUi
import br.com.saqz.groups.presentation.gamedetail.GameMemberCandidateUi
import br.com.saqz.groups.presentation.gamedetail.GameMemberRemovalUi
import br.com.saqz.groups.presentation.gamedetail.GameOrganizerFailure
import br.com.saqz.groups.presentation.gamedetail.GameOrganizerUi
import kotlin.test.Test
import kotlin.test.assertEquals

private const val DefaultReason = "Escolha do organizador"

@OptIn(ExperimentalTestApi::class)
class GameOrganizerSectionTest {
    private val admin = GameDetailPreviewData.admin
    private val candidates = listOf(
        GameMemberCandidateUi("edu", "Edu Faria", AthletePosition.PONTA),
        GameMemberCandidateUi("fabi", "Fabi Neves", null),
    )

    /** O que o ViewModel entrega a quem não é gestor: nenhuma linha com "Tirar". */
    private val athlete = admin.copy(
        isAdmin = false,
        confirmedRoster = admin.confirmedRoster.map { it.copy(canRemove = false) },
        waitlist = admin.waitlist.map { it.copy(canRemove = false) },
    )

    @Test
    fun tirarAparecePraGestorEForaDaLinhaDeQuemOlha() = runComposeUiTest {
        setScreen(admin)

        // "Lucas Prado" é quem olha (id 1): sem "Tirar". Os outros dois confirmados e os dois da espera têm.
        onAllNodesWithTag(GameOrganizerTags.remove("1")).assertCountEquals(0)
        listOf("2", "3", "wait-1", "wait-2").forEach {
            onNodeWithTag(GameOrganizerTags.remove(it)).performScrollTo().assertExists()
        }
    }

    @Test
    fun tirarNaoAparecePraQuemNaoEGestor() = runComposeUiTest {
        setScreen(athlete)

        listOf("1", "2", "3", "wait-1", "wait-2").forEach {
            onAllNodesWithTag(GameOrganizerTags.remove(it)).assertCountEquals(0)
        }
        onAllNodesWithTag(GameOrganizerTags.PlaceAction).assertCountEquals(0)
    }

    @Test
    fun tirarPedeAFolhaComOIdDaLinhaNosConfirmadosENaEspera() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(admin, intents::add)

        onNodeWithTag(GameOrganizerTags.remove("2")).performScrollTo().performClick()
        onNodeWithTag(GameOrganizerTags.remove("wait-1")).performScrollTo().performClick()

        assertEquals<List<GameDetailIntent>>(
            listOf(GameDetailIntent.RequestRemoveMember("2"), GameDetailIntent.RequestRemoveMember("wait-1")),
            intents,
        )
    }

    @Test
    fun folhaDeTirarTemONomeEALinhaDeAviso() = runComposeUiTest {
        setScreen(removeState(confirmed = true))

        onNodeWithText("Tirar Bia Souza do jogo?").assertExists()
        onNodeWithText("Bia Souza vai receber um aviso, ok?").assertExists()
        onNodeWithText("Sai dos confirmados e a vaga vai para o próximo da lista.").assertExists()
    }

    @Test
    fun folhaDeTirarDizOEfeitoDeCadaCaso() {
        runComposeUiTest {
            setScreen(removeState(confirmed = false))
            onNodeWithText("Sai da lista de espera. Nada é cobrado.").assertExists()
        }
        runComposeUiTest {
            setScreen(removeState(confirmed = true).copy(guest = GameGuestUi(feeLabel = "R$ 25,00")))
            onNodeWithText("Sai dos confirmados, a vaga vai para o próximo da lista e a cobrança de R$ 25,00 é cancelada.")
                .assertExists()
        }
    }

    @Test
    fun campoDeMotivoSoApareceComOutroMotivo() {
        runComposeUiTest {
            val intents = mutableListOf<GameDetailIntent>()
            setScreen(removeState(confirmed = true), intents::add)

            onAllNodesWithTag(GameOrganizerTags.RemoveReason).assertCountEquals(0)
            onNodeWithTag(GameOrganizerTags.RemoveReasonToggle).performClick()

            assertEquals(GameDetailIntent.ToggleRemoveReason, intents.single())
        }
        runComposeUiTest {
            setScreen(removeState(confirmed = true, reasonOpen = true))

            onNodeWithTag(GameOrganizerTags.RemoveReason).assertExists()
            onAllNodesWithTag(GameOrganizerTags.RemoveReasonToggle).assertCountEquals(0)
        }
    }

    @Test
    fun confirmarTirarMandaOMotivoPadraoEManterFecha() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(removeState(confirmed = true), intents::add)

        onNodeWithTag(GameOrganizerTags.RemoveConfirm).performClick()
        onNodeWithTag(GameOrganizerTags.RemoveKeep).performClick()

        assertEquals(
            listOf(GameDetailIntent.ConfirmRemoveMember(DefaultReason), GameDetailIntent.DismissRemoveMember),
            intents,
        )
    }

    @Test
    fun tirarEmVooTravaOsBotoes() = runComposeUiTest {
        setScreen(removeState(confirmed = true).let { it.copy(organizer = it.organizer.copy(removing = true)) })

        onNodeWithTag(GameOrganizerTags.RemoveConfirm).assertIsNotEnabled()
        onNodeWithTag(GameOrganizerTags.RemoveKeep).assertIsNotEnabled()
    }

    @Test
    fun falhaAoTirarMostraAMensagem() {
        runComposeUiTest {
            setScreen(removeState(true).let { it.copy(organizer = it.organizer.copy(removeFailure = GameOrganizerFailure.Generic)) })
            onNodeWithText("Não foi possível. Tente de novo.").assertExists()
        }
        runComposeUiTest {
            setScreen(removeState(true).let { it.copy(organizer = it.organizer.copy(removeFailure = GameOrganizerFailure.Closed)) })
            onNodeWithText("O jogo não aceita mais mudanças.").assertExists()
        }
    }

    @Test
    fun colocarAlguemAbreAFolhaSoParaGestor() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(admin, intents::add)

        onNodeWithTag(GameOrganizerTags.PlaceAction).performScrollTo().performClick()

        assertEquals(GameDetailIntent.OpenPlaceSheet, intents.single())
    }

    @Test
    fun colocarAlguemFicaAbaixoDosConfirmadosMesmoComAListaVazia() = runComposeUiTest {
        setScreen(admin.copy(confirmedRoster = emptyList()))

        onNodeWithTag(GameOrganizerTags.PlaceAction).assertExists()
    }

    @Test
    fun folhaDeColocarListaOsNomesEOBotaoSoHabilitaComEscolha() {
        runComposeUiTest {
            setScreen(placeState())

            onNodeWithText("Edu Faria").assertExists()
            onNodeWithText("Fabi Neves").assertExists()
            onNodeWithText("Escolha quem entra").assertExists()
            onNodeWithTag(GameOrganizerTags.PlaceSubmit).assertIsNotEnabled()
        }
        runComposeUiTest {
            setScreen(placeState(selectedId = "edu"))

            onNodeWithTag(GameOrganizerTags.candidate("edu")).assertIsSelected()
            onNodeWithText("Colocar Edu Faria no jogo").assertExists()
            onNodeWithTag(GameOrganizerTags.PlaceSubmit).assertIsEnabled()
        }
    }

    @Test
    fun tocarNoNomeSelecionaEOBotaoColoca() = runComposeUiTest {
        val intents = mutableListOf<GameDetailIntent>()
        setScreen(placeState(selectedId = "edu"), intents::add)

        onNodeWithTag(GameOrganizerTags.candidate("fabi")).performClick()
        onNodeWithTag(GameOrganizerTags.PlaceSubmit).performClick()

        assertEquals(
            listOf(GameDetailIntent.SelectPlaceCandidate("fabi"), GameDetailIntent.ConfirmPlaceMember(DefaultReason)),
            intents,
        )
    }

    @Test
    fun folhaDeColocarAvisaDaEsperaSoComJogoCheio() {
        runComposeUiTest {
            setScreen(placeState())
            onAllNodesWithText("Jogo cheio: entra na lista de espera.").assertCountEquals(0)
        }
        runComposeUiTest {
            setScreen(placeState(full = true))
            onNodeWithText("Jogo cheio: entra na lista de espera.").assertExists()
            onNodeWithTag(GameOrganizerTags.PlaceSheet).assertExists()
        }
    }

    @Test
    fun jogoCheioEJaComecadoDesabilitaComOMotivoCurto() = runComposeUiTest {
        setScreen(placeState(selectedId = "edu", full = true, started = true))

        onNodeWithText("Jogo cheio").assertExists()
        onNodeWithTag(GameOrganizerTags.PlaceSubmit).assertIsNotEnabled()
        onAllNodesWithText("Jogo cheio: entra na lista de espera.").assertCountEquals(0)
    }

    @Test
    fun semCandidatosAFolhaDizQueTodosJaEstaoNoJogo() = runComposeUiTest {
        setScreen(placeState().let { it.copy(organizer = it.organizer.copy(candidates = emptyList())) })

        onNodeWithTag(GameOrganizerTags.PlaceEmpty).assertExists()
    }

    @Test
    fun colocarEmVooTravaOBotao() = runComposeUiTest {
        setScreen(placeState(selectedId = "edu").let { it.copy(organizer = it.organizer.copy(placing = true)) })

        onNodeWithTag(GameOrganizerTags.PlaceSubmit).assertIsNotEnabled()
    }

    @Test
    fun falhaAoColocarMostraAMensagem() = runComposeUiTest {
        setScreen(
            placeState(selectedId = "edu")
                .let { it.copy(organizer = it.organizer.copy(placeFailure = GameOrganizerFailure.Generic)) },
        )

        onNodeWithText("Não foi possível. Tente de novo.").assertExists()
    }

    @Test
    fun cadaTagDoOrganizadorEUnica() = runComposeUiTest {
        setScreen(removeState(confirmed = true, reasonOpen = false))

        listOf(
            GameOrganizerTags.RemoveSheet,
            GameOrganizerTags.RemoveConfirm,
            GameOrganizerTags.RemoveKeep,
            GameOrganizerTags.RemoveReasonToggle,
            GameOrganizerTags.RemoveNotify,
            GameOrganizerTags.PlaceAction,
            GameOrganizerTags.remove("3"),
        ).forEach { onAllNodesWithTag(it).assertCountEquals(1) }
    }

    private fun removeState(confirmed: Boolean, reasonOpen: Boolean = false): GameDetailState {
        val removal = GameMemberRemovalUi(
            memberId = if (confirmed) "2" else "wait-1",
            name = if (confirmed) "Bia Souza" else "Caio Lima",
            confirmed = confirmed,
            reasonOpen = reasonOpen,
        )
        return admin.copy(organizer = GameOrganizerUi(removal = removal))
    }

    private fun placeState(selectedId: String? = null, full: Boolean = false, started: Boolean = false): GameDetailState =
        admin.copy(
            header = admin.header?.copy(responseOpen = !started),
            attendance = if (full) GameDetailAttendanceUi(12, 12, 0) else admin.attendance,
            organizer = GameOrganizerUi(placeOpen = true, candidates = candidates, selectedId = selectedId),
        )

    private fun ComposeUiTest.setScreen(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit = {}) = setContent {
        SaqzTheme { GameDetailScreen(state, {}, onIntent) }
    }
}
