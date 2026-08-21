package br.com.saqz.groups.presentation.ui.members

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.members.GroupMemberAction
import br.com.saqz.groups.presentation.members.GroupMembersIntent
import br.com.saqz.groups.presentation.members.GroupMembersState
import br.com.saqz.groups.presentation.members.MemberUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class GroupMembersScreenTest {

    @Test fun `the own row is not clickable and opens no sheet`() = runComposeUiTest {
        var intent: GroupMembersIntent? = null
        content(onIntent = { intent = it })

        onNodeWithTag(GroupMembersTags.member("lucas")).assertHasNoClickAction()
        onNodeWithTag(GroupMembersTags.member("lucas")).performClick()

        assertNull(intent)
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
        onNodeWithText("Tornar admin").assertExists()
        onNodeWithText("Remover do grupo").assertExists()
        onNodeWithText("Ver perfil").assertDoesNotExist()
        onNodeWithText("Remover admin").assertDoesNotExist()
    }

    @Test fun `the sheet of an admin offers the profile and the demotion`() = runComposeUiTest {
        content(state = state.copy(selected = bia))

        onNodeWithText("Ver perfil").assertExists()
        onNodeWithText("Remover admin").assertExists()
        onNodeWithText("Remover do grupo").assertExists()
        onNodeWithText("Editar jogador").assertDoesNotExist()
        onNodeWithText("Tornar admin").assertDoesNotExist()
    }

    @Test fun `the sheet of an admin viewer removes a member without promoting`() = runComposeUiTest {
        content(state = state.copy(selected = thiago.copy(canManageRoles = false)))

        onNodeWithText("Editar jogador").assertExists()
        onNodeWithText("Remover do grupo").assertExists()
        onNodeWithText("Tornar admin").assertDoesNotExist()
        onNodeWithText("Remover admin").assertDoesNotExist()
    }

    @Test fun `the sheet of the group owner offers only the profile`() = runComposeUiTest {
        content(state = state.copy(selected = lucas.copy(isSelf = false, isOwner = true)))

        onNodeWithText("Ver perfil").assertExists()
        onNodeWithText("Remover admin").assertDoesNotExist()
        onNodeWithText("Remover do grupo").assertDoesNotExist()
        onNodeWithText("Tornar admin").assertDoesNotExist()
        onNodeWithText("Editar jogador").assertDoesNotExist()
    }

    @Test fun `the sheet acts on whoever was touched`() = runComposeUiTest {
        var intent: GroupMembersIntent? = null
        content(state = state.copy(selected = thiago), onIntent = { intent = it })

        onNodeWithTag(GroupMembersTags.action(GroupMemberAction.Promote)).performClick()

        assertEquals(GroupMembersIntent.PerformAction(GroupMemberAction.Promote), intent)
    }

    @Test fun `no sheet is drawn while nobody is selected`() = runComposeUiTest {
        content()

        onNodeWithText("Tornar admin").assertDoesNotExist()
        onNodeWithText("Ver perfil").assertDoesNotExist()
    }

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
