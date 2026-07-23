package br.com.saqz.designsystem.effects

import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ObserveAsEventsTest {
    private class FakeLifecycleOwner(initial: Lifecycle.State) : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = initial }
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `no delivery below STARTED then buffered events deliver on reaching STARTED`() = runComposeUiTest {
        val owner = FakeLifecycleOwner(Lifecycle.State.CREATED)
        val channel = Channel<String>(Channel.BUFFERED)
        val received = mutableListOf<String>()

        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ObserveAsEvents(channel.receiveAsFlow()) { received += it }
            }
        }

        channel.trySend("a")
        waitForIdle()
        assertTrue(received.isEmpty(), "no delivery while below STARTED")

        owner.registry.currentState = Lifecycle.State.STARTED
        waitForIdle()
        assertEquals(listOf("a"), received, "buffered event delivered once on reaching STARTED")
    }

    @Test
    fun `collection is not restarted and event not redelivered when callback changes across recomposition`() =
        runComposeUiTest {
            val owner = FakeLifecycleOwner(Lifecycle.State.RESUMED)
            var subscriptions = 0
            val flow = flow<String> {
                subscriptions++
                emit("once")
                awaitCancellation()
            }
            val received = mutableListOf<String>()
            var bump by mutableStateOf(0)

            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    ObserveAsEvents(flow) { received += "$it-$bump" }
                    Text("bump=$bump")
                }
            }

            waitForIdle()
            assertEquals(1, subscriptions)
            assertEquals(listOf("once-0"), received)

            bump = 1
            waitForIdle()

            assertEquals(1, subscriptions, "callback change must not restart collection")
            assertEquals(listOf("once-0"), received, "event must not be redelivered on recomposition")
        }
}
