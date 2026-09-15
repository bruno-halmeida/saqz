package br.com.saqz.groups.presentation.whatsappbinding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.communication.GroupWhatsAppStatus
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WhatsAppBindingScreenTest {
    @Test fun loadingShowsSpinner() = runComposeUiTest {
        setContent { SaqzTheme { screen(WhatsAppBindingState(loading = true)) } }
        onNodeWithTag(WhatsAppBindingTags.Loading).assertIsDisplayed()
    }

    @Test fun errorOffersRetryThatReloads() = runComposeUiTest {
        val intents = mutableListOf<WhatsAppBindingIntent>()
        setContent { SaqzTheme { screen(WhatsAppBindingState(loading = false, error = true), intents::add) } }
        onNodeWithTag(WhatsAppBindingTags.Error).assertIsDisplayed()
        onNodeWithTag(WhatsAppBindingTags.Retry).performClick()
        assertEquals(listOf<WhatsAppBindingIntent>(WhatsAppBindingIntent.Load), intents)
    }

    @Test fun unboundShowsInviteFormDisabledUntilLinkIsPresent() = runComposeUiTest {
        setContent { SaqzTheme { screen(WhatsAppBindingState(loading = false)) } }
        onNodeWithTag(WhatsAppBindingTags.Empty).assertIsDisplayed()
        onNodeWithTag(WhatsAppBindingTags.InviteLink).assertIsDisplayed()
        onNodeWithTag(WhatsAppBindingTags.Link).assertIsNotEnabled()
    }

    @Test fun filledInviteLinkSubmitsBindingIntent() = runComposeUiTest {
        val intents = mutableListOf<WhatsAppBindingIntent>()
        setContent {
            SaqzTheme { screen(WhatsAppBindingState(loading = false, inviteLink = "chat.whatsapp.com/abc"), intents::add) }
        }
        onNodeWithTag(WhatsAppBindingTags.Link).performClick()
        assertEquals(listOf<WhatsAppBindingIntent>(WhatsAppBindingIntent.Link), intents)
    }

    @Test fun activeBindingShowsGroupNameAndDisables() = runComposeUiTest {
        val intents = mutableListOf<WhatsAppBindingIntent>()
        setContent { SaqzTheme { screen(bound(GroupWhatsAppStatus.ACTIVE), intents::add) } }
        onNodeWithTag(WhatsAppBindingTags.GroupName).assertIsDisplayed()
        onNodeWithTag(WhatsAppBindingTags.Status).assertIsDisplayed()
        onNodeWithTag(WhatsAppBindingTags.Toggle).performClick()
        assertEquals(listOf<WhatsAppBindingIntent>(WhatsAppBindingIntent.SetEnabled(false)), intents)
    }

    @Test fun disabledBindingCanBeReenabled() = runComposeUiTest {
        val intents = mutableListOf<WhatsAppBindingIntent>()
        setContent { SaqzTheme { screen(bound(GroupWhatsAppStatus.DISABLED), intents::add) } }
        onNodeWithTag(WhatsAppBindingTags.Toggle).performClick()
        assertEquals(listOf<WhatsAppBindingIntent>(WhatsAppBindingIntent.SetEnabled(true)), intents)
    }

    @Test fun brokenBindingCanBeReenabled() = runComposeUiTest {
        val intents = mutableListOf<WhatsAppBindingIntent>()
        setContent { SaqzTheme { screen(bound(GroupWhatsAppStatus.BROKEN), intents::add) } }
        onNodeWithTag(WhatsAppBindingTags.Toggle).performClick()
        assertEquals(listOf<WhatsAppBindingIntent>(WhatsAppBindingIntent.SetEnabled(true)), intents)
    }

    @Test fun confirmationNamesTheReturnedGroup() = runComposeUiTest {
        setContent {
            SaqzTheme {
                screen(bound(GroupWhatsAppStatus.ACTIVE).copy(confirmedGroupName = "Vôlei do CERET"))
            }
        }
        onNodeWithTag(WhatsAppBindingTags.Confirmation).assertIsDisplayed()
        onNodeWithText("Grupo vinculado: Vôlei do CERET").assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    private fun screen(state: WhatsAppBindingState, onIntent: (WhatsAppBindingIntent) -> Unit = {}) {
        WhatsAppBindingScreen(state = state, onIntent = onIntent, onBack = {})
    }

    private fun bound(status: GroupWhatsAppStatus) = WhatsAppBindingState(
        loading = false,
        bound = true,
        groupName = "Vôlei do CERET",
        status = status,
    )
}