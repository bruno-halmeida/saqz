package br.com.saqz.navigation.entry

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** Singleton probe route shared across the fake stacks in this test. */
private object ProbeRoute : NavKey

private class FakeViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * LIFE-01/02: verifies the [scopeEntryProvider] + [rememberStackEntryDecorators] chain
 * gives every retained stack its own ViewModel-store slot for an equal singleton route
 * key, retains that slot while the stack is not the active display, and releases it
 * once the key is definitively removed from that stack's backStack.
 */
@OptIn(ExperimentalTestApi::class)
class StackScopedEntriesTest {

    private fun probeProvider(capture: (ViewModelStore) -> Unit): (NavKey) -> NavEntry<NavKey> =
        entryProvider {
            entry<ProbeRoute> {
                val owner = LocalViewModelStoreOwner.current
                SideEffect { owner?.viewModelStore?.let(capture) }
            }
        }

    @Test
    fun equalKeyInTwoStacksGetsDistinctViewModelStores() = runComposeUiTest {
        var storeA: ViewModelStore? = null
        var storeB: ViewModelStore? = null
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides remember { FakeViewModelStoreOwner() }) {
                val decoratorsA = rememberStackEntryDecorators<NavKey>()
                val decoratorsB = rememberStackEntryDecorators<NavKey>()
                val entriesA = rememberDecoratedNavEntries(
                    backStack = listOf<NavKey>(ProbeRoute),
                    entryDecorators = decoratorsA,
                    entryProvider = scopeEntryProvider("stack-a", probeProvider { storeA = it }),
                )
                val entriesB = rememberDecoratedNavEntries(
                    backStack = listOf<NavKey>(ProbeRoute),
                    entryDecorators = decoratorsB,
                    entryProvider = scopeEntryProvider("stack-b", probeProvider { storeB = it }),
                )
                entriesA.last().Content()
                entriesB.last().Content()
            }
        }
        waitForIdle()

        assertNotSame(
            requireNotNull(storeA),
            requireNotNull(storeB),
            "an equal singleton route key composed in two distinct stacks must not share a ViewModel store",
        )
    }

    @Test
    fun inactiveStackRetainsItsViewModelStore() = runComposeUiTest {
        var recorded: ViewModelStore? = null
        var active by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides remember { FakeViewModelStoreOwner() }) {
                val decorators = rememberStackEntryDecorators<NavKey>()
                val entries = rememberDecoratedNavEntries(
                    backStack = listOf<NavKey>(ProbeRoute),
                    entryDecorators = decorators,
                    entryProvider = scopeEntryProvider("stack-a", probeProvider { recorded = it }),
                )
                if (active) entries.last().Content()
            }
        }
        waitForIdle()
        val firstStore = requireNotNull(recorded)

        active = false
        waitForIdle()
        active = true
        waitForIdle()

        assertSame(
            firstStore,
            recorded,
            "a stack that stays in the backStack while inactive must retain its ViewModel store",
        )
    }

    @Test
    fun removingKeyFromBackStackReleasesItsViewModelStore() = runComposeUiTest {
        var recorded: ViewModelStore? = null
        var present by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides remember { FakeViewModelStoreOwner() }) {
                val decorators = rememberStackEntryDecorators<NavKey>()
                val backStack: List<NavKey> = if (present) listOf(ProbeRoute) else emptyList()
                val entries = rememberDecoratedNavEntries(
                    backStack = backStack,
                    entryDecorators = decorators,
                    entryProvider = scopeEntryProvider("stack-a", probeProvider { recorded = it }),
                )
                entries.lastOrNull()?.Content()
            }
        }
        waitForIdle()
        val firstStore = requireNotNull(recorded)

        present = false
        waitForIdle()
        present = true
        waitForIdle()

        assertNotSame(
            firstStore,
            recorded,
            "definitively removing a key from its stack (e.g. logout/group clearing) must release its prior ViewModel store",
        )
    }
}
