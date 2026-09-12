package br.com.saqz.access.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.domain.appaccess.AppAccessError
import br.com.saqz.access.presentation.appaccess.AppOnboardingAuthState
import br.com.saqz.access.presentation.appaccess.AppOnboardingIntent
import br.com.saqz.access.presentation.appaccess.AppOnboardingState
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppOnboardingScreenTest {
    @Test
    fun `intro offers continue and skip actions`() = runComposeUiTest {
        val intents = mutableListOf<AppOnboardingIntent>()
        setContent {
            SaqzTheme { AppOnboardingScreen(AppOnboardingState(), intents::add) }
        }

        onNodeWithTag(AppOnboardingTags.Continue).assertIsDisplayed().performClick()
        onNodeWithTag(AppOnboardingTags.Skip).assertIsDisplayed().performClick()

        assertEquals(listOf(AppOnboardingIntent.Continue, AppOnboardingIntent.Skip), intents)
    }

    @Test
    fun `confirmation exposes target-safe copy and cancel`() = runComposeUiTest {
        var confirmed = false
        var canceled = false
        setContent {
            SaqzTheme {
                AppOnboardingScreen(
                    state = AppOnboardingState(),
                    onIntent = {},
                    authState = AppOnboardingAuthState.NeedsAccountConfirmation("target-id"),
                    currentAccountName = "Ana",
                    onConfirmAccount = { confirmed = true },
                    onClose = { canceled = true },
                )
            }
        }

        onNodeWithText("Você está conectado como Ana. O link pertence a outra conta. Deseja trocar de conta?").assertExists()
        onNodeWithTag(AppOnboardingTags.Continue).performClick()
        onNodeWithTag(AppOnboardingTags.Cancel).performClick()

        assertEquals(true, confirmed)
        assertEquals(true, canceled)
        onNodeWithText("target-id").assertDoesNotExist()
    }

    @Test
    fun `failed handoff offers new link and normal login without secret`() = runComposeUiTest {
        var reset = false
        var login = false
        setContent {
            SaqzTheme {
                AppOnboardingScreen(
                    state = AppOnboardingState(),
                    onIntent = {},
                    authState = AppOnboardingAuthState.Failed(AppAccessError.CodeInvalid),
                    onNewLink = { reset = true },
                    onOpenLogin = { login = true },
                )
            }
        }

        onNodeWithTag(AppOnboardingTags.Error).assertIsDisplayed()
        onNodeWithText("Este link expirou ou já foi usado. Abra um novo link para continuar.").assertExists()
        onNodeWithTag(AppOnboardingTags.Retry).performClick()
        onNodeWithTag(AppOnboardingTags.Login).performClick()

        assertEquals(true, reset)
        assertEquals(true, login)
    }

    @Test
    fun `handoff loading disables intro controls`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                AppOnboardingScreen(
                    state = AppOnboardingState(),
                    onIntent = {},
                    authState = AppOnboardingAuthState.Redeeming,
                )
            }
        }

        onNodeWithTag(AppOnboardingTags.Loading).assertIsDisplayed()
        onNodeWithTag(AppOnboardingTags.Continue).assertDoesNotExist()
        onNodeWithTag(AppOnboardingTags.Skip).assertDoesNotExist()
    }
}
