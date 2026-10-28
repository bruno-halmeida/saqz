package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.domain.appupdate.AppUpdatePrompt
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppUpdateSheetTest {
    @Test
    fun requiredHasNoCloseAndTheStoreButtonKeepsTheSheet() = runComposeUiTest {
        var opened = 0
        var dismissed = 0
        setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize()) {
                    AppUpdateSheet(AppUpdatePrompt.Required, AppUpdateStore.GooglePlay, { opened++ }, { dismissed++ })
                }
            }
        }
        onNodeWithText("Atualize o Saqz").assertExists()
        onNodeWithText("Tem versão nova do app. Para continuar usando, é só atualizar.").assertExists()
        assertEquals(0, onAllNodes(hasContentDescription("Fechar")).fetchSemanticsNodes().size)

        onRoot().performTouchInput { click(Offset(10f, 10f)) }
        onNodeWithText("Atualizar no Google Play").performClick()

        assertEquals(1, opened)
        assertEquals(0, dismissed)
        onNodeWithTag(AppUpdateTags.Sheet).assertExists()
    }

    @Test
    fun requiredOnTheIphoneNamesTheAppStore() = runComposeUiTest {
        setContent { SaqzTheme { AppUpdateSheet(AppUpdatePrompt.Required, AppUpdateStore.AppStore, {}, {}) } }
        onNodeWithText("Atualizar na App Store").assertExists()
    }

    @Test
    fun recommendedClosesByEveryExitAndEachOneCountsAsDismissed() = runComposeUiTest {
        var opened = 0
        var dismissed = 0
        var prompt by mutableStateOf<AppUpdatePrompt>(AppUpdatePrompt.Recommended("1.0.16"))
        setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize()) {
                    AppUpdateSheet(prompt, AppUpdateStore.GooglePlay, { opened++ }, { dismissed++ })
                }
            }
        }
        onNodeWithText("Tem versão nova do Saqz").assertExists()
        onNodeWithText("Atualize para ter as novidades e as correções.").assertExists()

        onNodeWithTag(AppUpdateTags.NotNow).performClick()
        onAllNodesWithContentDescription("Fechar").onLast().performClick()
        onAllNodesWithContentDescription("Fechar")[0].performClick()
        onNodeWithTag(AppUpdateTags.Update).performClick()

        assertEquals(4, dismissed)
        assertEquals(1, opened)

        prompt = AppUpdatePrompt.None
        waitForIdle()
        onNodeWithTag(AppUpdateTags.Sheet).assertDoesNotExist()
    }
}
