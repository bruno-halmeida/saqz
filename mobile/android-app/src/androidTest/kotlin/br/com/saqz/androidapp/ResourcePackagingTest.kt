package br.com.saqz.androidapp

import androidx.compose.foundation.Image
import androidx.compose.material.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.preflight_sentinel
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.ui.GroupOnboardingScreen
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test

class ResourcePackagingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun apkRendersSentinelString() {
        composeRule.setContent {
            Text(stringResource(Res.string.preflight_sentinel))
        }

        composeRule.onNodeWithText("Preflight Sentinel").assertIsDisplayed()
    }

    @Test
    fun apkRendersSentinelDrawable() {
        composeRule.setContent {
            Image(
                painter = painterResource(Res.drawable.preflight_sentinel),
                contentDescription = "apk-sentinel",
            )
        }

        composeRule.onNodeWithContentDescription("apk-sentinel").assertExists()
    }

    @Test
    fun apkRendersGroupsStringResourceV46() {
        composeRule.setContent {
            SaqzTheme {
                GroupOnboardingScreen(GroupSelectionState.NoGroup) {}
            }
        }

        composeRule.onNodeWithText("Voce ainda nao participa de um grupo").assertIsDisplayed()
    }
}
