package br.com.saqz.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RefreshWhenVisibleTest {
    private class TestLifecycleOwner(initialState: Lifecycle.State) : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
            currentState = initialState
        }
    }

    @Test
    fun refreshesOnEveryResumeAndOnEveryReentry() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED)
        var refreshes = 0
        var onScreen by mutableStateOf(true)

        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (onScreen) RefreshWhenVisible { refreshes++ }
            }
        }
        waitForIdle()
        assertEquals(0, refreshes)

        // Primeiro plano.
        runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        waitForIdle()
        assertEquals(1, refreshes)

        // Segundo plano e volta.
        runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        waitForIdle()
        runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        waitForIdle()
        assertEquals(2, refreshes)

        // Troca de aba ou tela filha por cima: sai da composição e reentra com o owner já RESUMED.
        runOnIdle { onScreen = false }
        waitForIdle()
        runOnIdle { onScreen = true }
        waitForIdle()
        assertEquals(3, refreshes)
    }

    @Test
    fun aChangedCounterRefreshesWithTheScreenStillMounted() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED)
        var refreshes = 0
        var version by mutableIntStateOf(0)

        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                RefreshWhenVisible(version) { refreshes++ }
            }
        }
        waitForIdle()
        assertEquals(1, refreshes)

        runOnIdle { version = 1 }
        waitForIdle()
        assertEquals(2, refreshes)
    }
}
