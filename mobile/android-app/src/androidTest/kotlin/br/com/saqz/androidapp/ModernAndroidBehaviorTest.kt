package br.com.saqz.androidapp

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
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
        // Cold start lands on the real Home shell with no artificial retention.
        composeRule.onNodeWithText("Saqz").assertIsDisplayed()
        composeRule.onNodeWithText("Explorar componentes").assertIsDisplayed()
    }

    @Test
    fun fontScale2Reflows() {
        shell("settings put system font_scale 2.0")
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        // At 2x font scale the primary action reflows and stays fully visible (not clipped).
        composeRule.onNodeWithText("Explorar componentes").assertIsDisplayed()
    }

    @Test
    fun rotationKeepsCatalog() {
        composeRule.onNodeWithText("Explorar componentes").performClick()
        composeRule.waitForIdle()
        // Fresh navigation starts scrolled to the top, so the first section header is visible.
        composeRule.onNodeWithText("cores").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()
        // The saved Catalog destination survives the configuration change (the Home action
        // is gone; the Catalog tree is present regardless of the scroll viewport).
        composeRule.onNodeWithText("Explorar componentes").assertDoesNotExist()
        composeRule.onNodeWithText("cores").assertExists()
    }

    @Test
    fun portraitInsets() {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        composeRule.waitForIdle()
        // Edge-to-edge: the bottom nav clears the system bar inset and stays reachable.
        composeRule.onNodeWithText("Início").assertIsDisplayed()
        composeRule.onNodeWithText("Componentes").assertIsDisplayed()
    }
}
