package br.com.saqz.designsystem.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun transparencyKeepsTranslucentChrome() = runComposeUiTest {
        lateinit var chrome: SaqzChrome
        setContent {
            SaqzTheme(SaqzAccessibilityPreferences(reduceTransparency = false)) {
                chrome = LocalSaqzChrome.current
            }
        }
        assertTrue(chrome.surface.alpha < 1f)
    }

    @Test
    fun reducedTransparencyUsesOpaqueSurface() = runComposeUiTest {
        lateinit var chrome: SaqzChrome
        lateinit var colors: SaqzColorTokens
        setContent {
            SaqzTheme(SaqzAccessibilityPreferences(reduceTransparency = true)) {
                chrome = LocalSaqzChrome.current
                colors = SaqzTheme.colors
            }
        }
        assertEquals(1f, chrome.surface.alpha)
        assertEquals(colors.surface, chrome.surface)
    }

    @Test
    fun opaqueChromeKeepsHairline() = runComposeUiTest {
        lateinit var chrome: SaqzChrome
        lateinit var colors: SaqzColorTokens
        setContent {
            SaqzTheme(SaqzAccessibilityPreferences(reduceTransparency = true)) {
                chrome = LocalSaqzChrome.current
                colors = SaqzTheme.colors
            }
        }
        assertEquals(colors.hairline, chrome.hairlineColor)
        assertEquals(1.dp, chrome.hairlineThickness)
    }
}
