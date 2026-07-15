package br.com.saqz.designsystem.resources

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

class ResourceSentinelTest {
    @Test
    @OptIn(ExperimentalTestApi::class)
    fun resolvesSentinelString() = runComposeUiTest {
        setContent {
            Text(stringResource(Res.string.preflight_sentinel))
        }

        onNodeWithText("Preflight Sentinel").assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun resolvesSentinelDrawable() = runComposeUiTest {
        setContent {
            Image(
                painter = painterResource(Res.drawable.preflight_sentinel),
                contentDescription = "sentinel",
            )
        }

        onNodeWithContentDescription("sentinel").assertExists()
    }
}
