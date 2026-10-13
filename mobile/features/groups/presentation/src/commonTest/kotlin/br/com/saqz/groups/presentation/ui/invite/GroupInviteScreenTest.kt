package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.invite.GroupInviteState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GroupInviteScreenTest {
    private val loaded = GroupInviteState(isLoading = false, groupName = "Vôlei de terça")

    @Test
    fun `game created header shows the success title and the next step`() = runComposeUiTest {
        setContent { SaqzTheme { GroupInviteScreen(state = loaded, onBack = {}, onIntent = {}, gameCreated = true) } }

        onNodeWithTag(GroupInviteTags.GameCreated).assertExists()
        onNodeWithText("Jogo marcado!").assertExists()
        onNodeWithText("Agora chame a galera: mande o link no WhatsApp e a lista começa a fechar sozinha.")
            .assertExists()
    }

    @Test
    fun `without the flag the invite screen has no game created header`() = runComposeUiTest {
        setContent { SaqzTheme { GroupInviteScreen(state = loaded, onBack = {}, onIntent = {}) } }

        onNodeWithTag(GroupInviteTags.GameCreated).assertDoesNotExist()
    }

    @Test
    fun `generating the first link shows loading instead of the empty state`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupInviteScreen(state = loaded.copy(isGenerating = true), onBack = {}, onIntent = {})
            }
        }

        onNodeWithText("Criar novo link").assertDoesNotExist()
    }
}
