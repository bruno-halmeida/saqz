package br.com.saqz.groups.presentation.communication

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.communication.NotificationPreferences
import br.com.saqz.groups.domain.communication.PushPreferences
import br.com.saqz.groups.domain.communication.WhatsAppPreferences
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
    @Test fun onlySomeoneElsesNoticeHasTheOptionsMenu() = runComposeUiTest {
        val intents = mutableListOf<GroupThreadIntent>()
        setContent { SaqzTheme {
            GroupThreadScreen(GroupThreadState(loading = false, messages = listOf(
                ThreadMessageUi("mine", "Eu", "Meu aviso", "09/09 12:00", authorId = "me", own = true),
                ThreadMessageUi("hers", "Bia", "Aviso da Bia", "09/09 12:00", authorId = "bia"),
            )), true, {}, { intents += it })
        } }
        onNodeWithTag(GroupThreadTags.options("mine")).assertDoesNotExist()
        onNodeWithContentDescription("Opções do aviso de Bia").performClick()
        assertEquals(listOf<GroupThreadIntent>(GroupThreadIntent.OpenMessageActions("hers")), intents)
    }
    @Test fun optionsSheetReportsTheNoticeOrBlocksTheAuthor() = runComposeUiTest {
        val intents = mutableListOf<GroupThreadIntent>()
        val hers = ThreadMessageUi("hers", "Bia", "Aviso da Bia", "09/09 12:00", authorId = "bia")
        setContent { SaqzTheme {
            GroupThreadScreen(GroupThreadState(loading = false, messages = listOf(hers), actionsFor = hers), true, {}, { intents += it })
        } }
        onNodeWithText("Denunciar aviso").performClick()
        onNodeWithText("Bloquear Bia").performClick()
        assertEquals(listOf(GroupThreadIntent.ReportMessage, GroupThreadIntent.BlockAuthor), intents)
    }
    @Test fun objectionableNoticeShowsTheSpecificMessage() = runComposeUiTest {
        setContent { SaqzTheme {
            GroupThreadScreen(GroupThreadState(loading = false, canPost = true, draft = "texto", sendRejected = true), true, {}, {})
        } }
        onNodeWithText("Esse texto tem palavras que não são permitidas no Saqz. Revise e tente de novo.").assertExists()
        onNodeWithText("Não foi possível confirmar o envio. Tente enviar novamente.").assertDoesNotExist()
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
    @Test fun whatsappPreferencesKeepOnlyTheChargeSwitchAndPreserveServerValues() = runComposeUiTest {
        val intents = mutableListOf<NotificationCenterIntent>()
        val preferences = NotificationPreferences(
            push = PushPreferences(messages = false),
            whatsapp = WhatsAppPreferences(notices = true, reminders = true))
        setContent { SaqzTheme {
            NotificationCenterScreen(NotificationCenterState(loading = false, preferences = preferences,
                settingsChannel = NotificationSettingsChannel.WHATSAPP), true, {}, { intents += it })
        } }
        onNodeWithTag("preferences-whatsapp-notices").assertDoesNotExist()
        onNodeWithTag("preferences-whatsapp-reminders").assertDoesNotExist()
        onNodeWithTag("preferences-whatsapp-charges").performClick()
        assertEquals(NotificationCenterIntent.Preferences(preferences.copy(
            whatsapp = WhatsAppPreferences(notices = true, reminders = true, charges = true))), intents.single())
        onNodeWithTag("preferences-whatsapp-messages").assertDoesNotExist()
    }
    @Test fun whatsappChannelIsHiddenWhileTheLaunchPolicyKeepsItOff() = runComposeUiTest {
        var whatsApp by mutableStateOf(false)
        setContent { SaqzTheme {
            NotificationCenterScreen(NotificationCenterState(loading = false), true, {}, {}, whatsAppChannel = whatsApp)
        } }
        onNodeWithText("Push").assertExists()
        onNodeWithText("WhatsApp").assertDoesNotExist()
        whatsApp = true
        onNodeWithText("WhatsApp").assertExists()
    }
    @Test fun pushSettingsAreDisabledWhileSaving() = runComposeUiTest {
        setContent { SaqzTheme {
            NotificationCenterScreen(NotificationCenterState(loading = false, busy = true,
                settingsChannel = NotificationSettingsChannel.PUSH), true, {}, {})
        } }
        onNodeWithTag("preferences-push-charges").assertIsNotEnabled()
    }

}
