package br.com.saqz.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SaqzPullToRefreshTest {
    @Test
    fun pullingDownCallsRefreshOnceAndKeepsContent() = runComposeUiTest {
        var calls = 0
        var refreshing by mutableStateOf(false)
        setContent {
            SaqzTheme {
                SaqzPullToRefresh(
                    refreshing = refreshing,
                    onRefresh = { calls++; refreshing = true },
                    modifier = Modifier.fillMaxSize().testTag("ptr"),
                ) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Text("Conteúdo") }
                }
            }
        }
        onNodeWithTag(SAQZ_PULL_REFRESH_INDICATOR_TAG).assertDoesNotExist()

        onNodeWithTag("ptr").performTouchInput { swipeDown() }
        waitForIdle()
        assertEquals(1, calls)
        onNodeWithTag(SAQZ_PULL_REFRESH_INDICATOR_TAG).assertExists()
        onNodeWithText("Conteúdo").assertIsDisplayed()

        // Puxar de novo enquanto atualiza não dispara outra busca.
        onNodeWithTag("ptr").performTouchInput { swipeDown() }
        waitForIdle()
        assertEquals(1, calls)

        refreshing = false
        waitForIdle()
        onNodeWithTag(SAQZ_PULL_REFRESH_INDICATOR_TAG).assertDoesNotExist()
    }

    @Test
    fun screenReaderGetsRefreshActionGuardedWhileRefreshing() = runComposeUiTest {
        var calls = 0
        var refreshing by mutableStateOf(false)
        setContent {
            SaqzTheme {
                SaqzPullToRefresh(
                    refreshing = refreshing,
                    onRefresh = { calls++ },
                    modifier = Modifier.fillMaxSize().testTag("ptr"),
                ) { Text("Conteúdo") }
            }
        }
        val action = onNodeWithTag("ptr").fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        assertEquals("Atualizar", action.label)
        runOnIdle { action.action() }
        assertEquals(1, calls)

        refreshing = true
        waitForIdle()
        val whileRefreshing = onNodeWithTag("ptr").fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        runOnIdle { whileRefreshing.action() }
        assertEquals(1, calls)
    }
}
