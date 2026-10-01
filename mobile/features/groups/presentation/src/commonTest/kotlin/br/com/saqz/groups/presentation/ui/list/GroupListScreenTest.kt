package br.com.saqz.groups.presentation.ui.list

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
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
}
