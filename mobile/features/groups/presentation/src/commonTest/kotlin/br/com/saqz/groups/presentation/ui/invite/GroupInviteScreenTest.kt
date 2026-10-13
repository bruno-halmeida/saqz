package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.invite.GroupInviteIntent
import br.com.saqz.groups.presentation.invite.GroupInviteState
import br.com.saqz.groups.presentation.invite.InviteStatus
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `generating the first code shows loading instead of the empty state`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupInviteScreen(state = loaded.copy(isGenerating = true), onBack = {}, onIntent = {})
            }
        }

        onNodeWithText("Criar código").assertDoesNotExist()
    }

    private val active = loaded.copy(
        inviteStatus = InviteStatus.Active,
        inviteCode = "44DF-8SA8",
        inviteUrl = "https://links.saqz.app/?saqz_invite=44DF8SA8",
    )

    @Test
    fun `active invite shows the big code and copy buttons with whatsapp as the main action`() = runComposeUiTest {
        val intents = mutableListOf<GroupInviteIntent>()
        setContent { SaqzTheme { GroupInviteScreen(state = active, onBack = {}, onIntent = intents::add) } }

        onNodeWithText("Código do grupo").assertExists()
        onNodeWithTag(GroupInviteTags.Code).assertExists()
        onNodeWithText("44DF-8SA8").assertExists()
        onNodeWithText("Copiar código").assertExists()
        onNodeWithText("Copiar link").assertExists()
        onNodeWithText("Compartilhar no WhatsApp").assertExists()
        onNodeWithTag(GroupInviteTags.CopyCode).performScrollTo().performClick()
        onNodeWithTag(GroupInviteTags.Copy).performScrollTo().performClick()

        assertEquals(listOf<GroupInviteIntent>(GroupInviteIntent.CopyCode, GroupInviteIntent.CopyLink), intents)
    }

    @Test
    fun `the old create new code button is gone and trocar codigo only asks for confirmation`() = runComposeUiTest {
        val intents = mutableListOf<GroupInviteIntent>()
        setContent { SaqzTheme { GroupInviteScreen(state = active, onBack = {}, onIntent = intents::add) } }

        onNodeWithText("Criar novo código").assertDoesNotExist()
        onNodeWithText("O antigo para de valer").assertDoesNotExist()
        onNodeWithText("Desativar link").assertDoesNotExist()
        onNodeWithTag(GroupInviteTags.Rotate).performScrollTo().performClick()

        assertEquals(listOf<GroupInviteIntent>(GroupInviteIntent.RequestRotate), intents)
    }

    @Test
    fun `rotate confirmation explains the consequence and offers trocar and cancelar`() = runComposeUiTest {
        val intents = mutableListOf<GroupInviteIntent>()
        setContent {
            SaqzTheme {
                GroupInviteScreen(state = active.copy(isRotateConfirmVisible = true), onBack = {}, onIntent = intents::add)
            }
        }

        onNodeWithText("Trocar o código?").assertExists()
        onNodeWithText("Quem tem o código ou o link antigo não vai conseguir entrar.").assertExists()
        onNodeWithText("Cancelar").performClick()
        onNodeWithTag(GroupInviteTags.RotateConfirm).performClick()

        assertEquals(listOf(GroupInviteIntent.DismissRotate, GroupInviteIntent.ConfirmRotate), intents)
    }
}
