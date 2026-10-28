package br.com.saqz.groups.presentation.ui.list

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.presentation.list.GroupCardAttendance
import br.com.saqz.groups.presentation.list.GroupCardGameUi
import br.com.saqz.groups.presentation.list.GroupCardUi
import br.com.saqz.groups.presentation.list.GroupListIntent
import br.com.saqz.groups.presentation.list.GroupListState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupListScreenTest {
    private fun card(id: String, attendance: GroupCardAttendance, admin: Boolean = false) = GroupCardUi(
        id = id, name = "Grupo $id", isAdmin = admin, place = "CERET — Quadra 2",
        nextGame = GroupCardGameUi(
            gameId = "g-$id", weekday = "QUI", day = "29", time = "19h30", live = false, confirmedCount = 14,
            capacity = 18, attendance = attendance, startsAtEpochSeconds = 1,
        ),
    )

    @Test
    fun `athlete first access pastes the invite and offers creating a group`() = runComposeUiTest {
        val intents = mutableListOf<GroupListIntent>()
        setContent { SaqzTheme { GroupListScreen(state = GroupListState(isLoading = false), onIntent = intents::add) } }

        onNodeWithTag(GroupListTags.Empty).assertIsDisplayed()
        onNodeWithText("Entre no seu grupo").assertIsDisplayed()
        onNodeWithText("Recebeu o link do grupo no WhatsApp? Toque nele ou cole aqui.").assertIsDisplayed()
        onNodeWithText("Colar o convite").performClick()
        onNodeWithTag(GroupListTags.EmptyCreate).performClick()

        assertEquals(listOf(GroupListIntent.OpenInviteSheet, GroupListIntent.CreateGroup), intents)
    }

    @Test
    fun `owner first access creates the group and keeps the invite as the dashed line`() = runComposeUiTest {
        val intents = mutableListOf<GroupListIntent>()
        setContent {
            SaqzTheme { GroupListScreen(state = GroupListState(isLoading = false), onIntent = intents::add, isPlanOwner = true) }
        }

        onNodeWithText("Crie o seu primeiro grupo").assertIsDisplayed()
        onNodeWithText("Cole o link que mandaram no WhatsApp", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(GroupListTags.EmptyCreate).performClick()
        onNodeWithTag(GroupListTags.EmptyInvite).performClick()

        assertEquals(listOf(GroupListIntent.CreateGroup, GroupListIntent.OpenInviteSheet), intents)
    }

    @Test
    fun `invite sheet submits the pasted link`() = runComposeUiTest {
        val intents = mutableListOf<GroupListIntent>()
        setContent {
            SaqzTheme {
                GroupListScreen(
                    state = GroupListState(isLoading = false, inviteSheetOpen = true, inviteLink = "https://saqz.app/"),
                    onIntent = intents::add,
                )
            }
        }

        onNodeWithText("Recebeu um convite?").assertIsDisplayed()
        onNodeWithTag(GroupListTags.InviteSheetSubmit).performClick()

        assertEquals(listOf<GroupListIntent>(GroupListIntent.SubmitInviteLink), intents)
    }

    @Test
    fun `header counts groups waiting for an answer and each photo shows the answer`() = runComposeUiTest {
        val state = GroupListState(
            isLoading = false,
            groups = listOf(
                card("a", GroupCardAttendance.Pending, admin = true),
                card("b", GroupCardAttendance.Going),
                card("c", GroupCardAttendance.Waitlisted),
                card("d", GroupCardAttendance.Out),
            ),
        )
        setContent { SaqzTheme { GroupListScreen(state = state, onIntent = {}) } }

        onNodeWithTag(GroupListTags.Summary).assertTextEquals("4 grupos · 1 esperando sua resposta")
        onNodeWithTag(GroupListTags.confirm("a"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(GroupListTags.admin("a"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(GroupListTags.status("b"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Você vai", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Na espera", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Não vai", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `summary without anyone waiting is only the group count`() = runComposeUiTest {
        val state = GroupListState(isLoading = false, groups = listOf(card("b", GroupCardAttendance.Going)))
        setContent { SaqzTheme { GroupListScreen(state = state, onIntent = {}) } }

        onNodeWithTag(GroupListTags.Summary).assertTextEquals("1 grupo")
    }

    @Test
    fun `spots read as going of total and open spots - or full`() = runComposeUiTest {
        val full = card("f", GroupCardAttendance.Going).let { it.copy(nextGame = it.nextGame!!.copy(confirmedCount = 18)) }
        val state = GroupListState(isLoading = false, groups = listOf(card("a", GroupCardAttendance.Going), full))
        setContent { SaqzTheme { GroupListScreen(state = state, onIntent = {}) } }

        onNodeWithText("14 de 18 vão", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("4 vagas abertas", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Completo", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `confirm on the photo opens the sheet and the row opens the group`() = runComposeUiTest {
        val intents = mutableListOf<GroupListIntent>()
        val state = GroupListState(isLoading = false, groups = listOf(card("a", GroupCardAttendance.Pending)))
        setContent { SaqzTheme { GroupListScreen(state = state, onIntent = intents::add) } }

        onNodeWithTag(GroupListTags.confirm("a"), useUnmergedTree = true).performClick()
        onNodeWithTag(GroupListTags.group("a")).performClick()

        assertEquals(listOf(GroupListIntent.OpenConfirm("a"), GroupListIntent.OpenGroup("a")), intents)
    }

    @Test
    fun `the sheet asks and answers going or not going`() = runComposeUiTest {
        val intents = mutableListOf<GroupListIntent>()
        val state = GroupListState(
            isLoading = false,
            groups = listOf(card("a", GroupCardAttendance.Pending)),
            confirmGroupId = "a",
        )
        setContent { SaqzTheme { GroupListScreen(state = state, onIntent = intents::add) } }

        onNodeWithText("Você vai nesse jogo?").assertIsDisplayed()
        onNodeWithTag(GroupListTags.ConfirmYes).performClick()
        onNodeWithTag(GroupListTags.ConfirmNo).performClick()

        assertEquals(
            listOf<GroupListIntent>(
                GroupListIntent.Respond(AttendanceIntent.Confirm),
                GroupListIntent.Respond(AttendanceIntent.Decline),
            ),
            intents,
        )
    }

    @Test
    fun `the sheet shows the offline warning and locks the buttons while answering`() = runComposeUiTest {
        val state = GroupListState(
            isLoading = false,
            groups = listOf(card("a", GroupCardAttendance.Pending)),
            confirmGroupId = "a",
            responding = true,
            responseFailed = true,
            responseOffline = true,
        )
        setContent { SaqzTheme { GroupListScreen(state = state, onIntent = {}) } }

        onNodeWithTag(GroupListTags.ConfirmError).assertTextEquals("Sem conexão. Sua resposta não foi enviada.")
        onNodeWithTag(GroupListTags.ConfirmYes).assertIsNotEnabled()
    }

    @Test
    fun `game in progress says now on the seal`() = runComposeUiTest {
        val live = card("a", GroupCardAttendance.Out).let { it.copy(nextGame = it.nextGame!!.copy(live = true)) }
        setContent { SaqzTheme { GroupListScreen(state = GroupListState(isLoading = false, groups = listOf(live)), onIntent = {}) } }

        onNodeWithText("AGORA", useUnmergedTree = true).assertIsDisplayed()
    }
}
