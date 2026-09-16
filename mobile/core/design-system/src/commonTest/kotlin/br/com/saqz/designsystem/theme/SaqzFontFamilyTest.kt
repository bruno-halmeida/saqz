package br.com.saqz.designsystem.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SaqzFontFamilyTest {
    // Runs on iosSimulatorArm64 (Quick design). Inter is Android-only, so the
    // checksum/weight cases are instrumented (InterPackagingTest); iOS keeps the
    // system font.
    @Test
    @OptIn(ExperimentalTestApi::class)
    fun iosUsesSystemDefault() = runComposeUiTest {
        lateinit var family: FontFamily
        setContent { family = saqzFontFamily() }
        assertEquals(FontFamily.Default, family)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun displayFamilyIsNotTheSystemDefault() = runComposeUiTest {
        lateinit var family: FontFamily
        setContent { family = saqzDisplayFontFamily() }
        assertNotEquals(FontFamily.Default, family)
    }
}
