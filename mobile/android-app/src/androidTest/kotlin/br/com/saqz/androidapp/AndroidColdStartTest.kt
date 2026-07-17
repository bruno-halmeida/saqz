package br.com.saqz.androidapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Cold start proves the native launch screen hands straight to the real Compose shell:
// no timer, no intermediate Compose splash, no artificial retention.
@RunWith(AndroidJUnit4::class)
class AndroidColdStartTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldStartReachesLogin() {
        // AUTH-06: without a persisted Firebase session, the native launch screen
        // dismisses straight into login and never exposes protected catalog content.
        composeRule.onNodeWithText("Saqz").assertIsDisplayed()
        composeRule.onNodeWithTag("login-email").assertIsDisplayed()
        composeRule.onNodeWithTag("login-password").assertIsDisplayed()
        composeRule.onNodeWithTag("login-submit").assertIsDisplayed()
        composeRule.onNodeWithText("Explorar componentes").assertDoesNotExist()
        composeRule.onNodeWithText("cores").assertDoesNotExist()
    }

    @Test
    fun noIntermediateComposeSplash() {
        composeRule.onNodeWithText("Saqz").assertIsDisplayed()
        // A Compose splash screen would leave a retained splash node; there is none.
        assertEquals(0, composeRule.onAllNodesWithTag("saqz-splash").fetchSemanticsNodes().size)
    }
}
