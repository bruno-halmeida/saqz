package br.com.saqz.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ObserveAsEventsTest {
    private class TestLifecycleOwner(initialState: Lifecycle.State) : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
            currentState = initialState
        }
    }

    @Test
    fun eventsAreIgnoredWhileTheScreenIsStopped() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.CREATED)
        val events = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val received = mutableListOf<String>()

        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ObserveAsEvents(events, onEvent = received::add)
            }
        }

        runOnIdle { events.tryEmit("em background") }
        waitForIdle()
        assertEquals(emptyList(), received)

        runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        waitForIdle()
        runOnIdle { events.tryEmit("em primeiro plano") }
        waitForIdle()
        assertEquals(listOf("em primeiro plano"), received)
    }
}
