package br.com.saqz.groups.presentation.communication

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.communication.NotificationPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class CommunicationScreenTest {
    @Test fun noticeReaderSeesMessagesAndNoPublishControl() = runComposeUiTest {
        setContent { SaqzTheme {
            GroupThreadScreen(GroupThreadState(loading = false, canPost = false, messages = listOf(ThreadMessageUi("notice", "Ana", "Treino às 20h", "09/09 12:00"))), true, {}, {})
        } }
        onNodeWithTag("message-notice").assertExists()
        onNodeWithText("Treino às 20h").assertExists()
        onNodeWithTag("message-send").assertDoesNotExist()
        onNodeWithText("Somente administradores publicam avisos.").assertExists()
    }
    @Test fun chatSendButtonHasAnExplicitIntent() = runComposeUiTest {
        val intents = mutableListOf<GroupThreadIntent>()
        setContent { SaqzTheme {
            GroupThreadScreen(GroupThreadState(loading = false, canPost = true, draft = "Olá"), false, {}, { intents += it })
        } }
        onNodeWithTag("message-send").performClick()
        assertEquals(listOf<GroupThreadIntent>(GroupThreadIntent.Send), intents)
    }
    @Test fun settingsToggleProducesTheCompletePreferencesPayloadAndSaveIsSeparate() = runComposeUiTest {
        val intents = mutableListOf<NotificationCenterIntent>()
        setContent { SaqzTheme {
            NotificationCenterScreen(NotificationCenterState(loading = false), true, {}, { intents += it })
        } }
        onNodeWithTag("preferences-notices").performClick()
        assertEquals(NotificationCenterIntent.Preferences(NotificationPreferences(false, true, true)), intents.single())
        onNodeWithText("Salvar preferências").performClick()
        assertEquals(NotificationCenterIntent.Save, intents.last())
    }
    @Test fun whatsappOptInChangesOnlyTheSelectedCategory() = runComposeUiTest {
        val intents = mutableListOf<NotificationCenterIntent>()
        val preferences = NotificationPreferences(
            push = br.com.saqz.groups.domain.communication.PushPreferences(messages = false),
            whatsapp = br.com.saqz.groups.domain.communication.WhatsAppPreferences())
        setContent { SaqzTheme {
            NotificationCenterScreen(NotificationCenterState(loading = false, preferences = preferences,
                settingsChannel = NotificationSettingsChannel.WHATSAPP), true, {}, { intents += it })
        } }
        onNodeWithTag("preferences-whatsapp-notices").performClick()
        assertEquals(NotificationCenterIntent.Preferences(preferences.copy(
            whatsapp = br.com.saqz.groups.domain.communication.WhatsAppPreferences(notices = true))), intents.single())
        onNodeWithTag("preferences-whatsapp-messages").assertDoesNotExist()
    }
    @Test fun pushSettingsAreDisabledWhileSaving() = runComposeUiTest {
        setContent { SaqzTheme {
            NotificationCenterScreen(NotificationCenterState(loading = false, busy = true,
                settingsChannel = NotificationSettingsChannel.PUSH), true, {}, {})
        } }
        onNodeWithTag("preferences-push-charges").assertIsNotEnabled()
    }

}
