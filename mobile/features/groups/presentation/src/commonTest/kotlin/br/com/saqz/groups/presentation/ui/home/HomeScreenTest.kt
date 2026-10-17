package br.com.saqz.groups.presentation.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.home.HomeGroupUi
import br.com.saqz.groups.presentation.home.HomeAdminGroupUi
import br.com.saqz.groups.presentation.home.HomeAdminReadModelUi
import br.com.saqz.groups.presentation.home.HomeGameToSettleUi
import br.com.saqz.groups.presentation.home.HomeMonthlyChargesUi
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeMemberUi
import br.com.saqz.groups.presentation.home.HomePendingRequestUi
import br.com.saqz.groups.presentation.home.HomeNextGameUi
import br.com.saqz.groups.presentation.home.HomeState
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    @Test
    fun `loading state renders the home skeleton`() = runComposeUiTest {
        setScreen(HomeState())

        onNodeWithTag(HomeTags.Loading).assertIsDisplayed()
    }

    @Test
    fun `error state renders the standard retry action`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(HomeState(isLoading = false, loadFailed = true), intents::add)

        onNodeWithTag(HomeTags.Error).assertIsDisplayed()
        onNodeWithText("Não foi possível carregar sua Home").assertIsDisplayed()
        onNodeWithText("Tentar novamente").performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.Retry), intents)
    }

    @Test
    fun `tocar no cartao do proximo jogo abre a tela do jogo`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(nextGameState(), intents::add)

        onNodeWithText("Terça, 19h30").performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.OpenGame("ceret", "game-1")), intents)
    }

    @Test
    fun `fonte grande quebra o hero e o chip por palavra sem partir letras`() = runComposeUiTest {
        val adminGroup = HomeGroupUi(id = "ceret", name = "Vôlei do CERET", meta = "12 atletas", isAdmin = true)
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 3f)) {
                SaqzTheme {
                    // Largura de iPhone: a janela de teste padrão é de tablet e esconderia a quebra.
                    Box(Modifier.width(390.dp)) {
                        HomeScreen(
                            state = nextGameState().copy(
                                member = nextGameState().member?.copy(groups = listOf(adminGroup)),
                            ),
                            onIntent = {},
                        )
                    }
                }
            }
        }

        // "Administrador" é uma palavra só: tem que ficar numa linha, e o "PRÓXIMO JOGO" quebra no máximo
        // entre as palavras (2 linhas), nunca letra a letra.
        assertTrue(lineCounts("PRÓXIMO JOGO").all { it <= 2 }, "kicker partido: ${lineCounts("PRÓXIMO JOGO")}")
    }

    @Test
    fun `next game state renders hero presence actions status and groups`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(nextGameState(), intents::add)

        onNodeWithText("Fala, Bruna!").assertIsDisplayed()
        onNodeWithTag(HomeTags.NextGame).assertIsDisplayed()
        onNodeWithText("PRÓXIMO JOGO").assertIsDisplayed()
        onNodeWithText("Terça, 19h30").assertIsDisplayed()
        onNodeWithText("28 de julho · CERET — Quadra 2 · Tatuapé").assertIsDisplayed()
        onNodeWithText("9 de 12 confirmados").assertIsDisplayed()
        onNodeWithText("Vou").performClick()
        onNodeWithText("Não vou").performClick()
        onNodeWithTag(HomeTags.Groups).assertIsDisplayed()
        onNodeWithTag(HomeTags.group("ceret")).performClick()
        onNodeWithTag(HomeTags.Notifications).performClick()

        assertEquals(
            listOf(
                HomeIntent.Respond(AttendanceIntent.Confirm),
                HomeIntent.Respond(AttendanceIntent.Decline),
                HomeIntent.OpenGroup("ceret"),
                HomeIntent.OpenNotifications,
            ),
            intents,
        )
    }

    @Test
    fun `empty state opens groups from both actions`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(nextGameState(nextGame = null), intents::add)

        onNodeWithTag(HomeTags.Empty).assertIsDisplayed()
        onNodeWithText("Sem jogo marcado").assertIsDisplayed()
        onNodeWithText("Ver meus grupos").performClick()
        onNodeWithText("Ver todos").performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.OpenGroups, HomeIntent.OpenGroups), intents)
    }

    @Test
    fun `first access stacks the two buttons so labels never wrap`() = runComposeUiTest {
        setScreen(firstAccessState(), {})

        val create = onNodeWithTag(HomeTags.FirstAccessCreate).getUnclippedBoundsInRoot()
        val invite = onNodeWithTag(HomeTags.FirstAccessInvite).getUnclippedBoundsInRoot()

        // O principal fica em cima e o convite embaixo, cada um com a largura toda.
        assertTrue(invite.top >= create.bottom)
        assertEquals(create.left, invite.left)
        assertEquals(create.right, invite.right)
    }

    @Test
    fun `first access renders the onboarding hero and steps and routes both paths`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(firstAccessState(), intents::add)

        onNodeWithText("Boas-vindas ao Saqz").assertIsDisplayed()
        onNodeWithTag(HomeTags.FirstAccess).assertIsDisplayed()
        onNodeWithText("Seu grupo em 1 minuto").assertIsDisplayed()
        onNodeWithTag(HomeTags.Steps).assertIsDisplayed()
        onNodeWithText("Agora").assertIsDisplayed()
        // Sem grupo não há hero de "sem jogo" nem cabeçalho "Seus grupos" solto.
        onNodeWithTag(HomeTags.Empty).assertDoesNotExist()
        onNodeWithTag(HomeTags.Groups).assertDoesNotExist()
        onNodeWithTag(HomeTags.FirstAccessCreate).performClick()
        onNodeWithTag(HomeTags.step(1)).performClick()
        onNodeWithTag(HomeTags.step(2)).assertHasNoClickAction()
        onNodeWithTag(HomeTags.step(3)).assertHasNoClickAction()
        onNodeWithTag(HomeTags.FirstAccessInvite).performClick()

        assertEquals(
            listOf(HomeIntent.CreateGroup, HomeIntent.CreateGroup, HomeIntent.OpenInviteSheet),
            intents,
        )
    }

    @Test
    fun `invite sheet submits the pasted link and shows the parse error`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(
            firstAccessState().copy(inviteSheetOpen = true, inviteLink = "https://saqz.app/", inviteLinkInvalid = true),
            intents::add,
        )

        onNodeWithText("Recebeu um convite?").assertIsDisplayed()
        onNodeWithText("Não achei um convite aí. Confira o link ou o código e tente de novo.").assertIsDisplayed()
        onNodeWithTag(HomeTags.InviteSheetSubmit).assertIsEnabled().performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.SubmitInviteLink), intents)
    }

    @Test
    fun `invite sheet keeps submit disabled while the field is blank`() = runComposeUiTest {
        setScreen(firstAccessState().copy(inviteSheetOpen = true))

        onNodeWithTag(HomeTags.InviteSheetSubmit).assertIsNotEnabled()
    }

    @Test
    fun `waitlisted reserva state renders the reserva chip box and leave and view game actions`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(
            nextGameState(nextGame = nextGame(AttendanceStatus.Waitlisted).copy(waitlistKind = HomeWaitlistKind.Reserva, waitlistPosition = 1, confirmedCount = 12, capacity = 12)),
            intents::add,
        )

        onNodeWithText("Lista de espera · 1º").assertIsDisplayed()
        onNodeWithText("As vagas acabaram. Você entrou na lista de espera.").assertIsDisplayed()
        onAllNodesWithText("Vou").assertCountEquals(0)
        onAllNodesWithText("Não vou").assertCountEquals(0)
        onNodeWithTag(HomeWaitlistTags.ReservaLeave).assertIsDisplayed()
        onNodeWithTag(HomeWaitlistTags.ReservaViewGame).performClick()
        onNodeWithTag(HomeWaitlistTags.ConfirmedSection).assertIsDisplayed()

        assertEquals(listOf<HomeIntent>(HomeIntent.OpenGame("ceret", "game-1")), intents)
    }

    @Test
    fun `waitlisted reserva com vaga aberta nao diz que as vagas acabaram`() = runComposeUiTest {
        setScreen(
            nextGameState(nextGame = nextGame(AttendanceStatus.Waitlisted).copy(waitlistKind = HomeWaitlistKind.Reserva, waitlistPosition = 1, confirmedCount = 0, capacity = 4)),
            {},
        )

        onNodeWithText("Você está na lista de espera.").assertIsDisplayed()
        onAllNodesWithText("As vagas acabaram. Você entrou na lista de espera.").assertCountEquals(0)
    }

    @Test
    fun `waitlisted avulso list state renders the queue section with the self row highlighted`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(
            nextGameState(
                nextGame = nextGame(AttendanceStatus.Waitlisted).copy(
                    waitlistKind = HomeWaitlistKind.AvulsoList,
                    waitlistPosition = 2,
                    waitlistedRoster = listOf(
                        br.com.saqz.groups.presentation.home.HomeWaitlistRowUi("Lucas Pereira", 1, false),
                        br.com.saqz.groups.presentation.home.HomeWaitlistRowUi("Bruna Silva", 2, true),
                    ),
                    confirmedCountTotal = 9,
                ),
            ),
            intents::add,
        )

        onNodeWithText("Lista de espera").assertIsDisplayed()
        onNodeWithText("Mensalista na espera sobe primeiro.").assertIsDisplayed()
        onNodeWithText("2º na lista · 9 confirmados").assertIsDisplayed()
        onNodeWithText("Quer entrar direto nos próximos?").assertIsDisplayed()
        onNodeWithTag(HomeWaitlistTags.QueueSection).assertIsDisplayed()
        onNodeWithText("Você").assertIsDisplayed()
        onNodeWithTag(HomeWaitlistTags.AvulsoLeave).performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.Respond(AttendanceIntent.Decline)), intents)
    }

    @Test
    fun `once the game started the two response buttons are disabled`() = runComposeUiTest {
        setScreen(nextGameState(nextGame = nextGame().copy(confirmationOpen = false, responseOpen = false)))

        onNodeWithText("Vou").assertIsNotEnabled()
        onNodeWithText("Não vou").assertIsNotEnabled()
    }

    @Test
    fun `remaining spots chip shows before the game starts`() = runComposeUiTest {
        val twoLeft = nextGame().copy(confirmedCount = 10, confirmedSummary = "10 de 12 confirmados")
        setScreen(nextGameState(nextGame = twoLeft))
        onNodeWithText("Restam 2 vagas").assertExists()
    }

    @Test
    fun `game in progress has no remaining spots chip`() = runComposeUiTest {
        val started = nextGame().copy(
            confirmedCount = 11,
            confirmedSummary = "11 de 12 confirmados",
            confirmationOpen = false,
            responseOpen = false,
        )
        setScreen(nextGameState(nextGame = started))
        onAllNodesWithText("Resta 1 vaga").assertCountEquals(0)
    }

    @Test
    fun `after the deadline the buttons stay enabled and the card explains the waitlist`() = runComposeUiTest {
        setScreen(nextGameState(nextGame = nextGame().copy(confirmationOpen = false, deadline = "")))

        onNodeWithText("Vou").assertIsEnabled()
        onNodeWithText("Não vou").assertIsEnabled()
        onNodeWithText("O prazo acabou: quem confirmar agora entra na lista de espera e sobe pela ordem se houver vaga.").assertIsDisplayed()
    }

    // ---- Backend v.0.0.17: depois do prazo o servidor recusa o "Vou"/"Não vou" ----

    private fun closedByDeadline() = nextGame().copy(confirmationOpen = false, closedByDeadline = true, deadline = "")

    @Test
    fun `a deadline refused by the server closes the answers without saying the game started`() = runComposeUiTest {
        setScreen(nextGameState(nextGame = closedByDeadline()))

        onNodeWithText("As confirmações deste jogo encerraram. Fale com o organizador.").assertIsDisplayed()
        onAllNodesWithText("Em andamento. Não dá mais para responder.").assertCountEquals(0)
        onAllNodesWithText(
            "O prazo acabou: quem confirmar agora entra na lista de espera e sobe pela ordem se houver vaga.",
        ).assertCountEquals(0)
        onNodeWithText("Vou").assertIsNotEnabled()
        onNodeWithText("Não vou").assertIsNotEnabled()
    }

    @Test
    fun `a deadline refused by the server has no remaining spots chip`() = runComposeUiTest {
        val almostFull = closedByDeadline().copy(confirmedCount = 11, confirmedSummary = "11 de 12 confirmados")
        setScreen(nextGameState(nextGame = almostFull))

        onAllNodesWithText("Resta 1 vaga").assertCountEquals(0)
    }

    @Test
    fun `a game in progress still says in progress and not the closed by deadline sentence`() = runComposeUiTest {
        setScreen(nextGameState(nextGame = nextGame().copy(confirmationOpen = false, responseOpen = false, deadline = "")))

        onNodeWithText("Em andamento. Não dá mais para responder.").assertIsDisplayed()
        onAllNodesWithText("As confirmações deste jogo encerraram. Fale com o organizador.").assertCountEquals(0)
    }

    @Test
    fun `answered game shows the status panel instead of the enabled buttons`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(nextGameState(nextGame = nextGame(AttendanceStatus.Confirmed)), intents::add)

        onNodeWithText("Presença confirmada.").assertIsDisplayed()
        onAllNodesWithTag(HomeTags.ResponseYes).assertCountEquals(0)
        onAllNodesWithTag(HomeTags.ResponseNo).assertCountEquals(0)

        // "Alterar" revela os botões; "Cancelar" volta para o painel sem emitir intent.
        onNodeWithTag(HomeTags.ResponseChange).performClick()
        onNodeWithTag(HomeTags.ResponseYes).assertIsDisplayed()
        onNodeWithTag(HomeTags.ResponseCancel).performClick()
        onAllNodesWithTag(HomeTags.ResponseYes).assertCountEquals(0)

        assertEquals(emptyList<HomeIntent>(), intents)
    }

    @Test
    fun `changing an answered response emits the new choice`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(nextGameState(nextGame = nextGame(AttendanceStatus.Confirmed)), intents::add)

        onNodeWithTag(HomeTags.ResponseChange).performClick()
        onNodeWithTag(HomeTags.ResponseNo).performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.Respond(AttendanceIntent.Decline)), intents)
    }

    @Test
    fun `started game keeps the answered panel with change disabled`() = runComposeUiTest {
        setScreen(
            nextGameState(
                nextGame = nextGame(AttendanceStatus.Declined).copy(confirmationOpen = false, responseOpen = false),
            ),
        )

        onNodeWithText("Tudo bem, na próxima. Sua vaga já foi liberada.").assertIsDisplayed()
        onNodeWithTag(HomeTags.ResponseChange).assertIsNotEnabled()
    }

    @Test
    fun `started game disables the waitlist leave button but keeps view game enabled`() = runComposeUiTest {
        setScreen(
            nextGameState(
                nextGame = nextGame(AttendanceStatus.Waitlisted).copy(
                    waitlistKind = HomeWaitlistKind.Reserva,
                    waitlistPosition = 1,
                    confirmationOpen = false,
                    responseOpen = false,
                ),
            ),
        )

        onNodeWithTag(HomeWaitlistTags.ReservaLeave).assertIsNotEnabled()
        onNodeWithTag(HomeWaitlistTags.ReservaViewGame).assertIsDisplayed()
    }

    @Test
    fun `waitlist actions put view game above leave`() = runComposeUiTest {
        setScreen(
            nextGameState(
                nextGame = nextGame(AttendanceStatus.Waitlisted).copy(waitlistKind = HomeWaitlistKind.Reserva, waitlistPosition = 1),
            ),
        )

        val view = onNodeWithTag(HomeWaitlistTags.ReservaViewGame).getUnclippedBoundsInRoot()
        val leave = onNodeWithTag(HomeWaitlistTags.ReservaLeave).getUnclippedBoundsInRoot()
        assertTrue(view.top < leave.top, "Ver o jogo fica acima de Sair: ${view.top} × ${leave.top}")
    }

    @Test
    fun `admin without next game offers create game and invite in the hero`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        val state = adminState()
        setScreen(state.copy(member = checkNotNull(state.member).copy(nextGame = null)), intents::add)

        onNodeWithTag(HomeTags.Empty).assertIsDisplayed()
        onNodeWithText("Sem jogo marcado").assertIsDisplayed()
        onNodeWithTag(HomeAdminTags.EmptyCreateGame).performClick()
        onNodeWithTag(HomeAdminTags.EmptyInvite).performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.OpenGameEditor("ceret"), HomeIntent.OpenInvite("ceret")), intents)
    }

    @Test
    fun `admin hero renders the three-way score and the member response actions`() = runComposeUiTest {
        setScreen(adminState())

        onNodeWithTag(HomeAdminTags.Hero).assertIsDisplayed()
        onNodeWithText("Vão").assertIsDisplayed()
        onNodeWithText("Não vão").assertIsDisplayed()
        onNodeWithText("Sem resposta").assertIsDisplayed()
        // Dono e admin também jogam: o placar não substitui a resposta deles.
        onNodeWithTag(HomeTags.ResponseYes).assertIsDisplayed()
        onNodeWithTag(HomeTags.ResponseNo).assertIsDisplayed()
    }

    @Test
    fun `admin hero response button emits the same respond intent`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(adminState(), intents::add)

        onNodeWithTag(HomeTags.ResponseYes).performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.Respond(AttendanceIntent.Confirm)), intents)
    }

    @Test
    fun `admin waiting rows expose the matching navigation intents`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(adminState(), intents::add)

        onNodeWithTag(HomeAdminTags.entryRequests("ceret")).performClick()
        onNodeWithTag(HomeAdminTags.monthly("ceret")).performClick()
        onNodeWithTag(HomeAdminTags.settle("ceret")).performClick()

        assertEquals(
            listOf(
                HomeIntent.OpenInvite("ceret"),
                HomeIntent.OpenCashbox("ceret"),
                HomeIntent.OpenGameSettlement("ceret", "game-1"),
            ),
            intents,
        )
    }

    @Test
    fun `admin without pending items hides the waiting section`() = runComposeUiTest {
        setScreen(adminState(withPendingItems = false))

        onNodeWithTag(HomeAdminTags.Shortcuts).assertIsDisplayed()
        onAllNodesWithTag(HomeAdminTags.Waiting).assertCountEquals(0)
    }

    @Test
    fun `admin with next game shows only create game and invite shortcuts`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(adminState(), intents::add)

        onNodeWithTag(HomeAdminTags.ShortcutCreateGame).performClick()
        onNodeWithTag(HomeAdminTags.ShortcutInvite).performClick()
        onAllNodesWithText("Caixa").assertCountEquals(0)
        onAllNodesWithText("Grupos").assertCountEquals(0)

        assertEquals(listOf<HomeIntent>(HomeIntent.OpenGameEditor("ceret"), HomeIntent.OpenInvite("ceret")), intents)
    }

    @Test
    fun `admin without next game hides the shortcuts row`() = runComposeUiTest {
        val state = adminState()
        setScreen(state.copy(member = checkNotNull(state.member).copy(nextGame = null)))

        onAllNodesWithTag(HomeAdminTags.Shortcuts).assertCountEquals(0)
        onNodeWithTag(HomeAdminTags.EmptyCreateGame).assertIsDisplayed()
    }

    @Test
    fun `admin subtitle renders under the greeting and the bell is reachable`() = runComposeUiTest {
        setScreen(adminState())

        onNodeWithText("Fala, Bruna!").assertIsDisplayed()
        onNodeWithText("1 grupos · 3 coisas esperando você").assertIsDisplayed()
        onNodeWithTag(HomeTags.Notifications).assertIsDisplayed()
    }

    @Test
    fun `pending request gets its own card and creating a group becomes secondary`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        val base = firstAccessState()
        setScreen(base.copy(member = base.member!!.copy(pendingRequests = listOf(HomePendingRequestUi("g-b", "RV Aprova B")))), intents::add)

        onNodeWithTag(HomePendingRequestTag).assertIsDisplayed()
        onNodeWithText("RV Aprova B").assertIsDisplayed()
        onNodeWithText("Pedido enviado · aguardando o organizador").assertIsDisplayed()
        onNodeWithTag(HomeTags.FirstAccess).assertDoesNotExist()
        onNodeWithTag(HomeTags.Steps).assertDoesNotExist()
        onNodeWithText("Seu grupo em 1 minuto").assertDoesNotExist()
        onNodeWithTag(HomePendingCreateButtonTag).performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.CreateGroup), intents)
    }

    @Test
    fun `pending request card offers cancelar pedido and it only asks for confirmation`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        val base = firstAccessState()
        setScreen(base.copy(member = base.member!!.copy(pendingRequests = listOf(HomePendingRequestUi("g-b", "RV Aprova B")))), intents::add)

        onNodeWithTag(HomePendingCancelTag).performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.RequestCancelEntryRequest("g-b")), intents)
    }

    @Test
    fun `cancel confirmation explains the consequence and offers voltar and cancelar pedido`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        val base = firstAccessState()
        val request = HomePendingRequestUi("g-b", "RV Aprova B")
        setScreen(
            base.copy(
                member = base.member!!.copy(pendingRequests = listOf(request)),
                cancelEntryCandidate = request,
            ),
            intents::add,
        )

        onNodeWithText("Cancelar o pedido para entrar em RV Aprova B?").assertIsDisplayed()
        onNodeWithText("Você sai da fila de aprovação. Para entrar depois, peça de novo pelo convite.").assertIsDisplayed()
        onNodeWithText("Voltar").performClick()
        onNodeWithTag(HomePendingCancelConfirmTag).performClick()

        assertEquals(
            listOf<HomeIntent>(HomeIntent.DismissCancelEntryRequest, HomeIntent.ConfirmCancelEntryRequest),
            intents,
        )
    }

    @Test
    fun `cancelling in flight disables the cancel button so a double tap cannot repeat`() = runComposeUiTest {
        val base = firstAccessState()
        setScreen(
            base.copy(
                member = base.member!!.copy(pendingRequests = listOf(HomePendingRequestUi("g-b", "RV Aprova B"))),
                cancelingEntry = true,
            ),
        )

        onNodeWithTag(HomePendingCancelTag).assertIsNotEnabled()
    }

    @Test
    fun `toast state renders the confirmation feedback`() = runComposeUiTest {
        setScreen(nextGameState().copy(toast = br.com.saqz.groups.presentation.home.HomeToast.Confirmed))

        onNodeWithTag(HomeTags.Toast).assertIsDisplayed()
        onNodeWithText("Presença confirmada. Bom jogo!").assertIsDisplayed()
    }

    @Test
    fun `home without pending charges renders neither the section nor the notice`() = runComposeUiTest {
        setScreen(nextGameState())

        onAllNodesWithTag(HomeTags.OwnCharges).assertCountEquals(0)
        onAllNodesWithTag(HomeTags.ownChargePixCopy("ceret")).assertCountEquals(0)
    }

    @Test
    fun `charges on time render no section`() = runComposeUiTest {
        setScreen(nextGameState().copy(ownCharges = previewOwnCharges()))

        onAllNodesWithTag(HomeTags.OwnCharges).assertCountEquals(0)
        onAllNodesWithText("Minhas cobranças").assertCountEquals(0)
    }

    @Test
    fun `overdue charge ticket opens the group and the button copies the key`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(nextGameState().copy(ownCharges = previewOwnChargesOverdue()), intents::add)

        onNodeWithTag(HomeTags.OwnCharges).assertIsDisplayed()
        onNodeWithText("Minhas cobranças").assertIsDisplayed()
        onNodeWithText("MENSALIDADE DE JULHO").assertIsDisplayed()
        onNodeWithText("R$ 80,00").assertIsDisplayed()
        onNodeWithText("Pix de Ana Souza · Nubank").assertIsDisplayed()
        onNodeWithTag(HomeTags.ownCharge("ceret")).performClick()
        onNodeWithTag(HomeTags.ownChargePixCopy("ceret")).performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.OpenGroup("ceret"), HomeIntent.CopyPix("ceret")), intents)
    }

    @Test
    fun `only overdue groups get a ticket and the copied state swaps the button`() = runComposeUiTest {
        setScreen(nextGameState().copy(ownCharges = previewOwnChargesOverdue(), pixCopiedGroupId = "ceret"))

        onNodeWithText("Venceu em 05/07").assertIsDisplayed()
        onNodeWithText("2 cobranças em aberto").assertIsDisplayed()
        // O grupo no prazo (pacaembu) não entra na Início.
        onAllNodesWithTag(HomeTags.ownCharge("pacaembu")).assertCountEquals(0)
        onNodeWithText("Chave copiada").assertIsDisplayed()
        onAllNodesWithText("Copiar chave Pix").assertCountEquals(0)
    }

    @Test
    fun `pix copied toast renders the manual settlement hint`() = runComposeUiTest {
        setScreen(nextGameState().copy(toast = br.com.saqz.groups.presentation.home.HomeToast.PixCopied))

        onNodeWithText("Chave copiada. Depois de pagar, o admin dá baixa.").assertIsDisplayed()
    }

    @Test
    fun `charge banner carries the aggregate text and opens the home`() = runComposeUiTest {
        var opened = 0
        setContent {
            SaqzTheme {
                HomeOwnChargesBanner(charges = previewOwnCharges(), onClick = { opened++ })
            }
        }

        onNodeWithText("Você tem R$ 80,00 em aberto").assertIsDisplayed()
        onNodeWithTag(HomeTags.OwnChargesBanner).performClick()
        assertEquals(1, opened)
    }

    private fun ComposeUiTest.lineCounts(text: String): List<Int> =
        onAllNodesWithText(text).fetchSemanticsNodes().map { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            results.first().lineCount
        }

    private fun ComposeUiTest.setScreen(
        state: HomeState,
        onIntent: (HomeIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            HomeScreen(state = state, onIntent = onIntent)
        }
    }
}

private fun firstAccessState() = HomeState(
    isLoading = false,
    displayName = "Bruna",
    member = HomeMemberUi(nextGame = null, groups = emptyList()),
)

private fun nextGameState(nextGame: HomeNextGameUi? = nextGame()) = HomeState(
    isLoading = false,
    displayName = "Bruna",
    member = HomeMemberUi(
        nextGame = nextGame,
        groups = listOf(HomeGroupUi("ceret", "Vôlei do CERET", "26 pessoas · 18 jogos")),
    ),
)

private fun nextGame(status: AttendanceStatus? = null) = HomeNextGameUi(
    groupId = "ceret",
    gameId = "game-1",
    groupName = "Vôlei do CERET",
    dateTime = "Ter, 28/07 · 19h30",
    local = "CERET — Quadra 2 · Tatuapé",
    deadline = "As confirmações encerram hoje às 18h.",
    confirmedSummary = "9 de 12 confirmados",
    confirmedCount = 9,
    capacity = 12,
    rosterNames = listOf("Ana Souza", "Bruna Lima", "Caio"),
    ownAttendance = status,
    weekday = "terça",
    time = "19h30",
    display = "Terça, 19h30",
    meta = "28 de julho · CERET — Quadra 2 · Tatuapé",
)

private fun adminState(withPendingItems: Boolean = true) = nextGameState().copy(
    member = checkNotNull(nextGameState().member).copy(
        adminSubtitle = if (withPendingItems) "1 grupos · 3 coisas esperando você" else null,
        admin = HomeAdminReadModelUi(
            groups = listOf(
                HomeAdminGroupUi(
                    id = "ceret",
                    name = "Vôlei do CERET",
                    entryRequestCount = if (withPendingItems) 3 else 0,
                    monthlyCharges = if (withPendingItems) {
                        HomeMonthlyChargesUi(2, "R$ 640,00", "JUL")
                    } else {
                        null
                    },
                    gameToSettle = if (withPendingItems) {
                        HomeGameToSettleUi("game-1", "28/07", 4, "R$ 320,00")
                    } else {
                        null
                    },
                ),
            ),
        ),
    ),
)
