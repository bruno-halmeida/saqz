package br.com.saqz.groups.presentation.ui.members

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.members.GroupMemberAction
import br.com.saqz.groups.presentation.members.GroupMembersFilter
import br.com.saqz.groups.presentation.members.GroupMembersIntent
import br.com.saqz.groups.presentation.members.GroupMembersNotice
import br.com.saqz.groups.presentation.members.JoinRequestUi
import br.com.saqz.groups.presentation.members.GroupMembersState
import br.com.saqz.groups.presentation.members.MemberUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class GroupMembersScreenTest {

    @Test fun `the own row opens the sheet so owner and admin can edit themselves`() = runComposeUiTest {
        var intent: GroupMembersIntent? = null
        content(onIntent = { intent = it })

        onNodeWithTag(GroupMembersTags.member("lucas")).performClick()

        assertEquals(GroupMembersIntent.OpenMember("lucas"), intent)
    }

    @Test fun `the row of another member opens the sheet on that person`() = runComposeUiTest {
        var intent: GroupMembersIntent? = null
        content(onIntent = { intent = it })

        onNodeWithTag(GroupMembersTags.member("thiago")).assertHasClickAction()
        onNodeWithTag(GroupMembersTags.member("thiago")).performClick()

        assertEquals(GroupMembersIntent.OpenMember("thiago"), intent)
    }

    @Test fun `the sheet of a common member offers editing and promotion`() = runComposeUiTest {
        content(state = state.copy(selected = thiago))

        onNodeWithText("Editar jogador").assertExists()
        onNodeWithText("Tornar administrador").assertExists()
        onNodeWithText("Remover do grupo").assertExists()
        onNodeWithText("Ver perfil").assertExists()
        onNodeWithText("Retirar acesso de administrador").assertDoesNotExist()
    }

    @Test fun `the sheet of an admin offers the profile the edit and the demotion`() = runComposeUiTest {
        content(state = state.copy(selected = bia))

        onNodeWithText("Ver perfil").assertExists()
        onNodeWithText("Editar jogador").assertExists()
        onNodeWithText("Retirar acesso de administrador").assertExists()
        onNodeWithText("Remover do grupo").assertExists()
        onNodeWithText("Tornar administrador").assertDoesNotExist()
    }

    @Test fun `the sheet of an admin viewer removes a member without promoting`() = runComposeUiTest {
        content(state = state.copy(selected = thiago.copy(canManageRoles = false)))

        onNodeWithText("Ver perfil").assertExists()
        onNodeWithText("Editar jogador").assertExists()
        onNodeWithText("Remover do grupo").assertExists()
        onNodeWithText("Tornar administrador").assertDoesNotExist()
        onNodeWithText("Retirar acesso de administrador").assertDoesNotExist()
    }

    @Test fun profileActionDispatchesWithoutRemovingEditAction() = runComposeUiTest {
        val intents = mutableListOf<GroupMembersIntent>()
        content(state = state.copy(selected = thiago), onIntent = { intents += it })

        onNodeWithTag(GroupMembersTags.action(GroupMemberAction.ViewProfile)).performClick()
        onNodeWithTag(GroupMembersTags.action(GroupMemberAction.EditMember)).performClick()

        assertEquals(
            listOf<GroupMembersIntent>(
                GroupMembersIntent.PerformAction(GroupMemberAction.ViewProfile),
                GroupMembersIntent.PerformAction(GroupMemberAction.EditMember),
            ),
            intents,
        )
    }

    @Test fun `the sheet of the group owner offers edit but never demote or remove`() = runComposeUiTest {
        content(state = state.copy(selected = lucas.copy(isSelf = false, isOwner = true)))

        onNodeWithText("Ver perfil").assertExists()
        onNodeWithText("Denunciar").assertExists()
        onNodeWithText("Bloquear").assertExists()
        onNodeWithText("Retirar acesso de administrador").assertDoesNotExist()
        onNodeWithText("Remover do grupo").assertDoesNotExist()
        onNodeWithText("Tornar administrador").assertDoesNotExist()
        onNodeWithText("Editar jogador").assertExists()
    }

    @Test fun `the sheet acts on whoever was touched`() = runComposeUiTest {
        var intent: GroupMembersIntent? = null
        content(state = state.copy(selected = thiago), onIntent = { intent = it })

        onNodeWithTag(GroupMembersTags.action(GroupMemberAction.Promote)).performClick()

        assertEquals(GroupMembersIntent.PerformAction(GroupMemberAction.Promote), intent)
    }

    @Test fun `report and block dispatch on the touched member`() = runComposeUiTest {
        val intents = mutableListOf<GroupMembersIntent>()
        content(state = state.copy(selected = thiago.copy(canManageAthletes = false, canManageRoles = false)), onIntent = { intents += it })

        onNodeWithText("Editar jogador").assertDoesNotExist()
        onNodeWithTag(GroupMembersTags.action(GroupMemberAction.Report)).performClick()
        onNodeWithTag(GroupMembersTags.action(GroupMemberAction.Block)).performClick()

        assertEquals(
            listOf<GroupMembersIntent>(
                GroupMembersIntent.PerformAction(GroupMemberAction.Report),
                GroupMembersIntent.PerformAction(GroupMemberAction.Block),
            ),
            intents,
        )
    }

    @Test fun `a blocked member offers unblock instead of block`() = runComposeUiTest {
        content(state = state.copy(selected = thiago.copy(isBlocked = true)))

        onNodeWithText("Desbloquear").assertExists()
        onNodeWithText("Bloquear").assertDoesNotExist()
    }

    @Test fun `no sheet is drawn while nobody is selected`() = runComposeUiTest {
        content()

        onNodeWithText("Tornar administrador").assertDoesNotExist()
        onNodeWithText("Ver perfil").assertDoesNotExist()
    }

    @Test fun `the pending filter shows the real number and the section lists the request`() = runComposeUiTest {
        content(state = state.copy(canReviewRequests = true, pendingCount = 1, joinRequests = listOf(ana)))

        onNodeWithText("Pendentes · 1").assertExists()
        onNodeWithText("Pedidos para entrar").assertExists()
        onNodeWithText("Ana Lima").assertExists()
        onNodeWithText("Pediu há 5 min").assertExists()
    }

    @Test fun `accept and decline of a request dispatch their own intents`() = runComposeUiTest {
        val intents = mutableListOf<GroupMembersIntent>()
        content(
            state = state.copy(canReviewRequests = true, pendingCount = 1, joinRequests = listOf(ana)),
            onIntent = { intents += it },
        )

        onNodeWithTag(GroupMembersTags.accept("ana")).performClick()
        onNodeWithTag(GroupMembersTags.decline("ana")).performClick()

        assertEquals(
            listOf(GroupMembersIntent.AcceptRequest("ana"), GroupMembersIntent.DeclineRequest("ana")),
            intents,
        )
    }

    @Test fun `a request with the call in flight cannot be tapped again`() = runComposeUiTest {
        content(
            state = state.copy(
                canReviewRequests = true,
                pendingCount = 1,
                joinRequests = listOf(ana),
                busyRequestIds = setOf("ana"),
            ),
        )

        onNodeWithTag(GroupMembersTags.accept("ana")).assertIsNotEnabled()
        onNodeWithTag(GroupMembersTags.decline("ana")).assertIsNotEnabled()
    }

    @Test fun `without permission there is no pending filter`() = runComposeUiTest {
        content()

        onNodeWithTag(GroupMembersTags.filter(GroupMembersFilter.Pending)).assertDoesNotExist()
        onNodeWithTag(GroupMembersTags.filter(GroupMembersFilter.All)).assertExists()
    }

    @Test fun `the approval notice names the person`() = runComposeUiTest {
        content(state = state.copy(notice = GroupMembersNotice.RequestApproved("Ana")))

        onNodeWithText("Ana entrou no grupo.").assertExists()
    }

    @Test fun `the decline notice and the confirmation use the invite texts`() = runComposeUiTest {
        var intent: GroupMembersIntent? = null
        content(
            state = state.copy(notice = GroupMembersNotice.RequestRejected, declineCandidate = ana),
            onIntent = { intent = it },
        )

        onNodeWithText("Pedido recusado.").assertExists()
        onNodeWithText("Recusar o pedido de Ana Lima?").assertExists()
        onNodeWithTag(GroupMembersTags.ConfirmDeclineAction).performClick()

        assertEquals(GroupMembersIntent.ConfirmDecline, intent)
    }

    @Test fun `a failed list keeps the other one on screen`() = runComposeUiTest {
        content(
            state = state.copy(
                canReviewRequests = true,
                pendingCount = 1,
                joinRequests = listOf(ana),
                rosterFailed = true,
                members = emptyList(),
                admins = emptyList(),
            ),
        )

        onNodeWithTag(GroupMembersTags.RosterFailed).assertExists()
        onNodeWithText("Ana Lima").assertExists()
    }

    private val ana = JoinRequestUi("ana", "Ana Lima", "Pediu há 5 min")

    private fun ComposeUiTest.content(
        state: GroupMembersState = this@GroupMembersScreenTest.state,
        onIntent: (GroupMembersIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            GroupMembersScreen(state = state, onIntent = onIntent, onBack = {})
        }
    }

    // "lucas" é quem está usando o app — a única linha sem toque no 2k.
    private val lucas = MemberUi(
        id = "lucas",
        name = "Lucas Prado",
        meta = "Criou o grupo · levantador",
        isAdmin = true,
        isSelf = true,
        stats = "42 jogos · 98% de presença",
    )

    private val bia = MemberUi(
        id = "bia",
        name = "Bia Souza",
        meta = "Ponteira · desde março",
        isAdmin = true,
        isSelf = false,
        stats = "18 jogos · 92% de presença",
    )

    private val thiago = MemberUi(
        id = "thiago",
        name = "Thiago Melo",
        meta = "Central · mensalista",
        isAdmin = false,
        isSelf = false,
        stats = "31 jogos · 88% de presença",
    )

    // Elenco curto de propósito: a lista inteira cabe na tela e nenhum toque depende
    // de rolagem.
    private val state = GroupMembersState(
        isLoading = false,
        totalCount = 3,
        adminCount = 2,
        admins = listOf(lucas, bia),
        members = listOf(thiago),
        shownCount = 1,
    )
}
