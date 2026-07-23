package br.com.saqz.designsystem.text

import androidx.compose.material.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.uitext_probe_greeting
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class UiTextTest {
    @Test
    fun rawRendersItsLiteralValue() = runComposeUiTest {
        setContent { Text(UiText.Raw("Mensagem crua").asString()) }
        onNodeWithText("Mensagem crua").assertIsDisplayed()
    }

    @Test
    fun resResolvesStringResourceWithArguments() = runComposeUiTest {
        setContent {
            Text(UiText.Res(Res.string.uitext_probe_greeting, listOf("Ana")).asString())
        }
        onNodeWithText("Olá, Ana").assertIsDisplayed()
    }
}
