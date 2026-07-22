package br.com.saqz.composeapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

class SaqzAppTest {
    @Test
    @OptIn(ExperimentalTestApi::class)
    fun rendersTheSharedSaqzPlaceholder() {
        val dependencies = startTestSaqzKoin()
        try {
            runComposeUiTest {
                setContent { SaqzApp(dependencies) }

                onNodeWithText("Organize seu grupo.", substring = true).assertIsDisplayed()
            }
        } finally {
            stopTestSaqzKoin()
        }
    }
}
