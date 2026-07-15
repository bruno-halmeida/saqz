package br.com.saqz.androidapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
    fun coldStartReachesHome() {
        // The interactive Home action is present immediately after launch — the launch
        // screen dismissed straight into the shell, not into a splash placeholder.
        composeRule.onNodeWithText("Explorar componentes").assertIsDisplayed()
        composeRule.onNodeWithText("Saqz").assertIsDisplayed()
    }

    @Test
    fun noIntermediateComposeSplash() {
        composeRule.onNodeWithText("Saqz").assertIsDisplayed()
        // A Compose splash screen would leave a retained splash node; there is none.
        assertEquals(0, composeRule.onAllNodesWithTag("saqz-splash").fetchSemanticsNodes().size)
    }
}
