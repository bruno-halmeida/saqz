package br.com.saqz.groups.presentation.ui.list

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.presentation.list.GroupCardAttendance
import br.com.saqz.groups.presentation.list.GroupCardGameUi
import br.com.saqz.groups.presentation.list.GroupCardUi
import br.com.saqz.groups.presentation.list.GroupListIntent
import br.com.saqz.groups.presentation.list.GroupListState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupListScreenTest {
    @Test
    fun `first access offers both paths and the invite one opens the sheet`() = runComposeUiTest {
        val intents = mutableListOf<GroupListIntent>()
        setContent { SaqzTheme { GroupListScreen(state = GroupListState(isLoading = false), onIntent = intents::add) } }

        onNodeWithTag(GroupListTags.Empty).assertIsDisplayed()
        onNodeWithText("Crie o seu ou entre pelo convite que a galera te mandou.").assertIsDisplayed()
        onNodeWithText("Criar grupo").performClick()
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
    fun `header counts groups awaiting confirmation and each card shows its attendance`() = runComposeUiTest {
        fun card(id: String, attendance: GroupCardAttendance?) = GroupCardUi(
            id = id, name = "Grupo $id", meta = "Quadra", modality = GroupModality.COURT_VOLLEYBALL, isAdmin = false,
            nextGame = GroupCardGameUi(label = "04/08 · 19h30 · 8 de 12", attendance = attendance),
        )
        val state = GroupListState(
            isLoading = false,
            groups = listOf(
                card("a", GroupCardAttendance.Pending),
                card("b", GroupCardAttendance.Going),
                card("c", GroupCardAttendance.Waitlisted),
                card("d", GroupCardAttendance.Out),
            ),
        )
        setContent { SaqzTheme { GroupListScreen(state = state, onIntent = {}) } }

        onNodeWithText("4 grupos · 1 pedindo confirmação").assertIsDisplayed()
        onNodeWithText("Confirme").assertIsDisplayed()
        onNodeWithText("Você vai").assertIsDisplayed()
        onNodeWithText("Na espera").assertIsDisplayed()
        onNodeWithText("Não vai").assertIsDisplayed()
    }
}
