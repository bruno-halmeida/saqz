package br.com.saqz.androidapp

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

// Self-contained subset meant to also run standalone against API 35: the behaviors most
// likely to regress on a newer platform — cold start, font scale, rotation and insets —
// measured on the real MainActivity launcher.
@RunWith(AndroidJUnit4::class)
class ModernAndroidBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun resetConfiguration() {
        shell("settings put system font_scale 1.0")
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun shell(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        // Drain the pipe so the settings write completes before the test proceeds.
        FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        descriptor.close()
    }

    @Test
    fun modernSubsetRunsStandalone() {
        // AUTH-03/AUTH-06: cold start without a session lands on the real login
        // destination with both supported authentication methods available.
        composeRule.onNodeWithText("Saqz").assertIsDisplayed()
        composeRule.onNodeWithTag("login-submit").assertIsDisplayed()
        composeRule.onNodeWithTag("login-google").assertIsDisplayed()
    }

    @Test
    fun fontScale2Reflows() {
        shell("settings put system font_scale 2.0")
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        // EDGE-07: both login actions reflow and remain reachable at 2x font scale.
        composeRule.onNodeWithTag("login-submit").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login-google").performScrollTo().assertIsDisplayed()
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
        // AUTH-06 + EDGE-06/EDGE-07: rotation restores exactly one login
        // destination, with no protected content in the semantics tree.
        assertEquals(
            1,
            composeRule.onAllNodesWithTag("authenticated-access-destination").fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithTag("login-submit").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("cores").assertDoesNotExist()
    }

    @Test
    fun portraitInsets() {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        composeRule.waitForIdle()
        // EDGE-07: edge-to-edge keeps both authentication actions reachable.
        composeRule.onNodeWithTag("login-submit").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login-google").performScrollTo().assertIsDisplayed()
    }
}
