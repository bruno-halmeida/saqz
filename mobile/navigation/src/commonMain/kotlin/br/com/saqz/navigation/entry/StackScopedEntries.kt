package br.com.saqz.navigation.entry

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * LIFE-01/02: identifies a [NavEntry]'s saveable-state/ViewModel-store slot by both its
 * owning stack and its route identity, so an equal singleton [NavKey] rendered in two
 * different retained stacks (e.g. `GroupsRoute.GroupHome` in two group scopes) never
 * shares saved state or a ViewModel store (design.md, "Serialization and Entry
 * Identity" -> `ScopedEntryProviderFactory`).
 */
// Bundle-compatible on Android: saveable-state keys must be storable in a Bundle,
// so the namespaced identity is a plain String, not a data class.
fun navigationEntryId(stackId: String, routeIdentity: Any): String = "$stackId:$routeIdentity" 

/**
 * Wraps [delegate] so every [NavEntry] it produces carries a [navigationEntryId]
 * content key instead of the shared entry provider's default `key.toString()` identity.
 * The one entry provider built by the Access/Groups/Finance/host installers
 * (`ProductNavigationHost`) is therefore safe to reuse, unmodified, across every
 * retained stack.
 */
fun <T : NavKey> scopeEntryProvider(
    stackId: String,
    delegate: (T) -> NavEntry<T>,
): (T) -> NavEntry<T> = { key ->
    val original = delegate(key)
    NavEntry(
        key = key,
        contentKey = navigationEntryId(stackId, original.contentKey),
        metadata = original.metadata,
    ) { original.Content() }
}

/**
 * The saveable-state-before-ViewModel decorator chain required by every retained stack
 * (design.md, "Research Basis"): `rememberSaveableStateHolderNavEntryDecorator` precedes
 * `rememberViewModelStoreNavEntryDecorator` so entry-scoped `SavedStateHandle` support
 * remains valid.
 */
@Composable
fun <T : Any> rememberStackEntryDecorators(): List<NavEntryDecorator<T>> = listOf(
    rememberSaveableStateHolderNavEntryDecorator(),
    rememberViewModelStoreNavEntryDecorator(),
)
