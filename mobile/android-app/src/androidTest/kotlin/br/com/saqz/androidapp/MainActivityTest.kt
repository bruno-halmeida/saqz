package br.com.saqz.androidapp

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

// Restoration and accessibility measured on the real launcher: insets, IME and rotation
// with a closed overlay preserved.
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private val signedOut = SignedOutAccessRule()
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(signedOut).around(composeRule)

    @After
    fun resetOrientation() {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @Test
    fun displaysTheSharedSaqzPlaceholder() {
        composeRule.onNodeWithText("Organize seu grupo.", substring = true).assertIsDisplayed()
    }

    @Test
    fun landscapeInsets() {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()
        // EDGE-07: both authentication actions remain reachable around system insets.
        composeRule.onNodeWithTag("login-submit").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login-google").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun imeKeepsInputVisible() {
        val email = composeRule.onNodeWithTag("login-email")
        // Tapping the field focuses it and requests the IME; imePadding shrinks the content
        // viewport, and the field stays reachable/visible within that reduced viewport.
        email.performScrollTo().performClick()
        composeRule.waitForIdle()
        email.performScrollTo()
        email.assertIsDisplayed()
    }

    @Test
    fun rotationKeepsSingleLoginDestination() {
        assertEquals(
            1,
            composeRule.onAllNodesWithTag("authenticated-access-destination").fetchSemanticsNodes().size,
        )
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()
        // AUTH-06 + EDGE-06/EDGE-07: restoration preserves one signed-out
        // destination and never resurrects protected catalog content.
        assertEquals(
            1,
            composeRule.onAllNodesWithTag("authenticated-access-destination").fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithTag("login-submit").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("cores").assertDoesNotExist()
    }
}
