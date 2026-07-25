package br.com.saqz.androidapp

import androidx.compose.foundation.Image
import androidx.compose.material.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.access_brand
import br.com.saqz.access.resources.saqz_lettering
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
            Text(stringResource(Res.string.access_brand))
        }

        composeRule.onNodeWithText("Saqz").assertIsDisplayed()
    }

    @Test
    fun apkRendersSentinelDrawable() {
        composeRule.setContent {
            Image(
                painter = painterResource(Res.drawable.saqz_lettering),
                contentDescription = "apk-sentinel",
            )
        }

        composeRule.onNodeWithContentDescription("apk-sentinel").assertExists()
    }
}
