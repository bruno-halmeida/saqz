package br.com.saqz.groups.presentation.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.communication.NotificationCenterScreen
import br.com.saqz.groups.presentation.communication.NotificationCenterState
import br.com.saqz.groups.presentation.communication.NotificationCenterTags
import br.com.saqz.groups.presentation.communication.NotificationPromptAudience
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NotificationPromptSheetTest {
    @Test
    fun athleteSheetShowsTheTextsAndBothButtons() = runComposeUiTest {
        var enabled = 0
        var notNow = 0
        setContent { SaqzTheme { NotificationPromptSheet(NotificationPromptAudience.Athlete, { enabled++ }, { notNow++ }) } }
        onNodeWithText("Quer saber quando tem jogo?").assertExists()
        onNodeWithText("A gente avisa quando a lista abrir, quando o prazo estiver acabando e se o jogo mudar. Nada de spam.").assertExists()
        onNodeWithTag(NotificationPromptTags.Enable).performClick()
        onNodeWithTag(NotificationPromptTags.NotNow).performClick()
        assertEquals(1, enabled)
        assertEquals(1, notNow)
    }

    @Test
    fun organizerSheetHasItsOwnBody() = runComposeUiTest {
        setContent { SaqzTheme { NotificationPromptSheet(NotificationPromptAudience.Organizer, {}, {}) } }
        onNodeWithText("A gente avisa quando alguém pedir pra entrar, quando a galera confirmar e quando o prazo estiver acabando.").assertExists()
        onNodeWithText("Ligar avisos").assertExists()
        onNodeWithText("Agora não").assertExists()
    }

    @Test
    fun notificationCenterOffersTheSettingsOnlyWhenNotificationsAreOff() = runComposeUiTest {
        var opened = 0
        var off by androidx.compose.runtime.mutableStateOf(false)
        setContent {
            SaqzTheme {
                NotificationCenterScreen(
                    NotificationCenterState(loading = false), settings = false, onBack = {}, onIntent = {},
                    notificationsOff = off, onOpenNotificationSettings = { opened++ },
                )
            }
        }
        onNodeWithTag(NotificationCenterTags.NotificationsOff).assertDoesNotExist()
        off = true
        onNodeWithText("Avisos desligados").assertExists()
        onNodeWithTag(NotificationCenterTags.NotificationsOffAction).performClick()
        assertEquals(1, opened)
    }
}
