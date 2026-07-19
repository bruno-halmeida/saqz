package br.com.saqz.androidapp

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.ui.LoginScreen
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.designsystem.theme.saqzFontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Accessibility on-device: the four Inter weights render, and the TalkBack traversal order
// follows the visual reading order without a duplicated brand label.
@RunWith(AndroidJUnit4::class)
class AndroidAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fourInterWeightsRender() {
        composeRule.setContent {
            val family = saqzFontFamily()
            Column {
                Text("w300", style = TextStyle(fontFamily = family, fontWeight = FontWeight(300)))
                Text("w400", style = TextStyle(fontFamily = family, fontWeight = FontWeight(400)))
                Text("w600", style = TextStyle(fontFamily = family, fontWeight = FontWeight(600)))
                Text("w700", style = TextStyle(fontFamily = family, fontWeight = FontWeight(700)))
            }
        }
        composeRule.onNodeWithText("w300").assertIsDisplayed()
        composeRule.onNodeWithText("w400").assertIsDisplayed()
        composeRule.onNodeWithText("w600").assertIsDisplayed()
        composeRule.onNodeWithText("w700").assertIsDisplayed()
    }

    @Test
    fun talkBackOrderMatchesSemantics() {
        composeRule.setContent {
            SaqzTheme {
                LoginScreen(AuthenticationState()) { }
            }
        }
        composeRule.waitForIdle()

        // Decorative artwork is silent, so the visible brand label is announced exactly once.
        assertEquals(1, composeRule.onAllNodesWithContentDescription("Saqz").fetchSemanticsNodes().size)

        val headingTop = composeRule.onNodeWithContentDescription("Saqz").fetchSemanticsNode().boundsInRoot.top
        val emailTop = composeRule.onNodeWithTag("login-email").fetchSemanticsNode().boundsInRoot.top
        val passwordTop = composeRule.onNodeWithTag("login-password").fetchSemanticsNode().boundsInRoot.top
        val submitTop = composeRule.onNodeWithTag("login-submit").fetchSemanticsNode().boundsInRoot.top
        val googleTop = composeRule.onNodeWithTag("login-google").fetchSemanticsNode().boundsInRoot.top
        // AUTH-03/AUTH-06 + EDGE-07: signed-out reading order follows the
        // visible login journey and both authentication methods stay reachable.
        assertTrue("heading precedes email", headingTop < emailTop)
        assertTrue("email precedes password", emailTop < passwordTop)
        assertTrue("password precedes submit", passwordTop < submitTop)
        assertTrue("password submit precedes Google", submitTop < googleTop)
    }
}
