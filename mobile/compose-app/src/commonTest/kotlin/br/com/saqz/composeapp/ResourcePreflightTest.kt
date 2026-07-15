package br.com.saqz.composeapp

import androidx.compose.foundation.Image
import androidx.compose.material.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test

class ResourcePreflightTest {
    @Test
    @OptIn(ExperimentalTestApi::class)
    fun umbrellaResolvesSentinelString() = runComposeUiTest {
        setContent {
            Text(stringResource(ResourcePreflight.sentinelString))
        }

        onNodeWithText("Preflight Sentinel").assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun umbrellaResolvesSentinelDrawable() = runComposeUiTest {
        setContent {
            Image(
                painter = painterResource(ResourcePreflight.sentinelDrawable),
                contentDescription = "umbrella-sentinel",
            )
        }

        onNodeWithContentDescription("umbrella-sentinel").assertExists()
    }
}
