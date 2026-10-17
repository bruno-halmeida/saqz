package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.invite.GroupInviteIntent
import br.com.saqz.groups.presentation.invite.GroupInviteState
import br.com.saqz.groups.presentation.invite.GroupInviteToast
import br.com.saqz.groups.presentation.invite.RecentMemberUi
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

    @Test
    fun `desativar convite only asks for confirmation`() = runComposeUiTest {
        val intents = mutableListOf<GroupInviteIntent>()
        setContent { SaqzTheme { GroupInviteScreen(state = active, onBack = {}, onIntent = intents::add) } }

        onNodeWithTag(GroupInviteTags.Deactivate).performScrollTo().performClick()

        assertEquals(listOf<GroupInviteIntent>(GroupInviteIntent.RequestDeactivate), intents)
    }

    @Test
    fun `deactivate confirmation explains the consequence and offers desativar and cancelar`() = runComposeUiTest {
        val intents = mutableListOf<GroupInviteIntent>()
        setContent {
            SaqzTheme {
                GroupInviteScreen(
                    state = active.copy(isDeactivateConfirmVisible = true),
                    onBack = {},
                    onIntent = intents::add,
                )
            }
        }

        onNodeWithText("Desativar o convite?").assertExists()
        onNodeWithText("Quem tem o código ou o link não vai conseguir entrar.").assertExists()
        onNodeWithText("Cancelar").performClick()
        onNodeWithTag(GroupInviteTags.DeactivateConfirmAction).performClick()

        assertEquals(listOf<GroupInviteIntent>(GroupInviteIntent.DismissDeactivate, GroupInviteIntent.ConfirmDeactivate), intents)
    }

    @Test
    fun `deactivating disables both invite actions so a double tap cannot repeat`() = runComposeUiTest {
        setContent {
            SaqzTheme { GroupInviteScreen(state = active.copy(isDeactivating = true), onBack = {}, onIntent = {}) }
        }

        onNodeWithTag(GroupInviteTags.Deactivate).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(GroupInviteTags.Rotate).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `without an active invite there is nothing to deactivate`() = runComposeUiTest {
        setContent { SaqzTheme { GroupInviteScreen(state = loaded, onBack = {}, onIntent = {}) } }

        onNodeWithTag(GroupInviteTags.Deactivate).assertDoesNotExist()
        onNodeWithText("Desativar convite").assertDoesNotExist()
    }

    // ---- Backend v.0.0.17: o GET do convite não devolve o link nem o código ----

    private val activeWithoutLink = loaded.copy(inviteStatus = InviteStatus.Active, inviteCode = null, inviteUrl = null)

    @Test
    fun `an invite reopened without link or code offers to generate a link and warns about the old one`() =
        runComposeUiTest {
            val intents = mutableListOf<GroupInviteIntent>()
            setContent {
                SaqzTheme { GroupInviteScreen(state = activeWithoutLink, onBack = {}, onIntent = intents::add) }
            }

            onNodeWithText("Gerar link de convite").assertExists()
            onNodeWithText("O link anterior deixa de funcionar.").assertExists()
            onNodeWithTag(GroupInviteTags.Rotate).assertDoesNotExist()
            onNodeWithText("Trocar código").assertDoesNotExist()
            onNodeWithTag(GroupInviteTags.Copy).assertDoesNotExist()
            onNodeWithTag(GroupInviteTags.GenerateLink).performScrollTo().performClick()

            assertEquals(listOf<GroupInviteIntent>(GroupInviteIntent.ConfirmRotate), intents)
        }

    @Test
    fun `generating the link shows progress on the action and does not offer it twice`() = runComposeUiTest {
        val intents = mutableListOf<GroupInviteIntent>()
        setContent {
            SaqzTheme {
                GroupInviteScreen(
                    state = activeWithoutLink.copy(isGenerating = true),
                    onBack = {},
                    onIntent = intents::add,
                )
            }
        }

        onNodeWithTag(GroupInviteTags.GenerateLink).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(GroupInviteTags.GenerateLink).performClick()

        assertEquals(emptyList<GroupInviteIntent>(), intents)
    }

    @Test
    fun `with the current backend the link comes back and the generate action never shows`() = runComposeUiTest {
        setContent { SaqzTheme { GroupInviteScreen(state = active, onBack = {}, onIntent = {}) } }

        onNodeWithTag(GroupInviteTags.GenerateLink).assertDoesNotExist()
        onNodeWithText("Gerar link de convite").assertDoesNotExist()
        onNodeWithText("O link anterior deixa de funcionar.").assertDoesNotExist()
        onNodeWithTag(GroupInviteTags.Rotate).assertExists()
    }

    @Test
    fun `aprovar mostra o retorno com o nome e a pessoa aparece em Quem entrou`() = runComposeUiTest {
        val state = active.copy(
            toast = GroupInviteToast.RequestApproved("Ana"),
            recentMembers = listOf(RecentMemberUi("u1", "Ana")),
        )
        setContent { SaqzTheme { GroupInviteScreen(state = state, onBack = {}, onIntent = {}) } }

        onNodeWithText("Ana entrou no grupo.").assertExists()
        onNodeWithText("Ana").performScrollTo().assertExists()
    }

    @Test
    fun `recusar mostra pedido recusado`() = runComposeUiTest {
        val state = active.copy(toast = GroupInviteToast.RequestRejected)
        setContent { SaqzTheme { GroupInviteScreen(state = state, onBack = {}, onIntent = {}) } }

        onNodeWithText("Pedido recusado.").assertExists()
    }
}
