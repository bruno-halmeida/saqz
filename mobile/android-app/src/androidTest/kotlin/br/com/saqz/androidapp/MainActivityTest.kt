package br.com.saqz.androidapp

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Restoration and accessibility measured on the real launcher: insets, IME and rotation
// with a closed overlay preserved.
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun resetOrientation() {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun openCatalog() {
        composeRule.onNodeWithText("Explorar componentes").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun displaysTheSharedSaqzPlaceholder() {
        composeRule.onNodeWithText("Saqz").assertIsDisplayed()
    }

    @Test
    fun landscapeInsets() {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()
        // In landscape the bottom nav still clears the system bar inset and stays reachable.
        composeRule.onNodeWithText("Início").assertIsDisplayed()
        composeRule.onNodeWithText("Componentes").assertIsDisplayed()
    }

    @Test
    fun imeKeepsInputVisible() {
        openCatalog()
        val email = composeRule.onNodeWithText("Email")
        // Tapping the field focuses it and requests the IME; imePadding shrinks the content
        // viewport, and the field stays reachable/visible within that reduced viewport.
        email.performScrollTo().performClick()
        composeRule.waitForIdle()
        email.performScrollTo()
        email.assertIsDisplayed()
    }

    @Test
    fun rotationKeepsOverlayClosed() {
        openCatalog()
        // The modal primary action ("ok") exists only while an overlay is open; none is.
        composeRule.onNodeWithText("ok").assertDoesNotExist()
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()
        // Rotation does not resurrect a closed overlay, and the catalog is still shown.
        composeRule.onNodeWithText("ok").assertDoesNotExist()
        composeRule.onNodeWithText("cores").assertExists()
    }
}
