package br.com.saqz.access.ui.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class SaqzAccessibilityPreferencesTest {
    @Test
    fun defaultsAreFalse() {
        val prefs = SaqzAccessibilityPreferences()
        assertFalse(prefs.reduceMotion)
        assertFalse(prefs.reduceTransparency)
    }

    @Test
    fun reduceMotionSelectsReducedPolicy() = runComposeUiTest {
        lateinit var motion: SaqzMotionPolicy
        setContent {
            SaqzTheme(SaqzAccessibilityPreferences(reduceMotion = true)) {
                motion = SaqzTheme.motion
            }
        }
        assertEquals(SaqzMotionPolicy.Reduced, motion)
    }

    @Test
    fun normalSelectsNormalPolicy() = runComposeUiTest {
        lateinit var motion: SaqzMotionPolicy
        setContent {
            SaqzTheme(SaqzAccessibilityPreferences(reduceMotion = false)) {
                motion = SaqzTheme.motion
            }
        }
        assertEquals(SaqzMotionPolicy.Normal, motion)
    }
}
