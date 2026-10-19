package br.com.saqz.access.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** DEB-085: a tela de conta suspensa é neutra (sem motivo), abre o e-mail da equipe e deixa sair. */
@OptIn(ExperimentalTestApi::class)
class AccountSuspendedScreenTest {
    @Test fun `shows the neutral text with the support address and no reason`() = runComposeUiTest {
        setContent { SaqzTheme { AccountSuspendedScreen(onContact = {}, onLogout = {}) } }

        onNodeWithText("Sua conta foi suspensa.", substring = true).assertExists()
        onNodeWithText(
            "Para entender o motivo ou pedir revisão, escreva para contato@egysis.com.",
        ).assertExists()
    }

    @Test fun `contact and logout buttons call their callbacks`() = runComposeUiTest {
        var contact = 0
        var logout = 0
        setContent { SaqzTheme { AccountSuspendedScreen(onContact = { contact++ }, onLogout = { logout++ }) } }

        onNodeWithTag(AccountSuspendedTags.Contact).performClick()
        assertEquals(1, contact)
        assertEquals(0, logout)
        onNodeWithTag(AccountSuspendedTags.Logout).performClick()
        assertEquals(1, logout)
    }

    @Test fun `the mail link goes to the support address`() {
        assertEquals(true, AccountSuspendedMailto.startsWith("mailto:contato@egysis.com"))
    }
}
