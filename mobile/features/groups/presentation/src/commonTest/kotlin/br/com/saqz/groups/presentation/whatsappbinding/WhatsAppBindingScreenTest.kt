package br.com.saqz.groups.presentation.whatsappbinding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.components.HowItWorksTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WhatsAppBindingScreenTest {
    @Test
    fun `the link under the form opens how it works`() = runComposeUiTest {
        val intents = mutableListOf<WhatsAppBindingIntent>()
        setContent {
            SaqzTheme { WhatsAppBindingScreen(state = WhatsAppBindingState(loading = false), onIntent = intents::add, onBack = {}) }
        }

        onNodeWithTag(WhatsAppBindingTags.HowItWorks).performScrollTo().performClick()

        assertEquals(listOf<WhatsAppBindingIntent>(WhatsAppBindingIntent.OpenHowItWorks), intents)
    }

    @Test
    fun `broken binding asks for the link again instead of re-enabling`() = runComposeUiTest {
        val intents = mutableListOf<WhatsAppBindingIntent>()
        setContent {
            SaqzTheme {
                WhatsAppBindingScreen(
                    state = WhatsAppBindingState(
                        loading = false,
                        bound = true,
                        groupName = "Vôlei do CERET",
                        status = br.com.saqz.groups.domain.communication.GroupWhatsAppStatus.BROKEN,
                        inviteLink = "https://chat.whatsapp.com/AbCdEf123456",
                    ),
                    onIntent = intents::add,
                    onBack = {},
                )
            }
        }

        onNodeWithText("Quebrado").assertIsDisplayed()
        onNodeWithTag(WhatsAppBindingTags.Toggle).assertDoesNotExist()
        onNodeWithText("Vincular de novo").performScrollTo().assertIsDisplayed()
        onNodeWithTag(WhatsAppBindingTags.Link).performScrollTo().performClick()

        assertEquals(listOf<WhatsAppBindingIntent>(WhatsAppBindingIntent.Link), intents)
    }

    @Test
    fun `how it works shows the steps the caveat and the statuses and closes on got it`() = runComposeUiTest {
        val intents = mutableListOf<WhatsAppBindingIntent>()
        setContent {
            SaqzTheme {
                WhatsAppBindingScreen(
                    state = WhatsAppBindingState(loading = false, howItWorksOpen = true),
                    onIntent = intents::add,
                    onBack = {},
                )
            }
        }

        onNodeWithTag(HowItWorksTags.Sheet).assertIsDisplayed()
        onNodeWithText("Cole o link aqui e toque em “Vincular grupo”.").assertIsDisplayed()
        onNodeWithText("Aguardando aprovação").assertIsDisplayed()
        onNodeWithTag(HowItWorksTags.GotIt).performClick()

        assertEquals(listOf<WhatsAppBindingIntent>(WhatsAppBindingIntent.CloseHowItWorks), intents)
    }
}
