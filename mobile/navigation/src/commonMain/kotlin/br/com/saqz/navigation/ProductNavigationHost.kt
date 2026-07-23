package br.com.saqz.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import br.com.saqz.navigation.entry.rememberStackEntryDecorators
import br.com.saqz.navigation.entry.scopeEntryProvider

/**
 * ACCESSNAV-04: which entry set the single [NavDisplay] shows. `ACCESS` renders the
 * access stack; `AUTHENTICATED` renders the tab stacks. Reaching `Ready` switches the
 * mode instead of pushing Groups onto the access stack.
 */
enum class NavigationMode { ACCESS, AUTHENTICATED }

private const val STACK_ACCESS = "access"
private const val STACK_HOME = "home"
private const val STACK_GROUPS = "groups"
private const val STACK_NOTICES = "notices"
private const val STACK_MORE = "more"

/**
 * MODNAV-01: the one active `NavDisplay` for the whole product (Approach A). It owns no
 * business state: the five app-owned back stacks, the selected tab, the navigation mode,
 * and the shared [onBack] handler (`NavigationSession.goBack`) are hoisted to the
 * composition root (T23), which builds the one shared [entryProvider] from the Access,
 * Groups, and Finance installers and supplies the serializable stacks. This host only
 * decorates each stack independently and renders the correct active entry list.
 *
 * SPEC_DEVIATION: design.md lists "creates NavigationSession" under ProductNavigationHost.
 * Reason: session/stack construction (`rememberNavBackStack` + `rememberSerializable` tab)
 * and shared-provider assembly are the composition-root binding surface owned by T23
 * ("Integrate product navigation at the composition root"). Hoisting them keeps this host a
 * pure, unit-testable display composer and avoids duplicating T23's wiring; the observable
 * behavior (one NavDisplay, access/home/tab topology, one shared back handler) is unchanged.
 *
 * - LIFE-01/02: every stack is decorated unconditionally in a stable order through
 *   [rememberDecoratedNavEntries] with the saveable-state-before-ViewModel chain, and each
 *   stack's entry identities are namespaced by [scopeEntryProvider] so an equal singleton
 *   key in two stacks never shares a ViewModel/saved state.
 * - TAB-01/03, RESTORE-01/04: all four tab stacks are always decorated (retained), so a
 *   restored non-empty stack + selected tab renders without the display ever going empty.
 * - BACK-04: at a non-home tab the display is `home + active tab` entries, so `NavDisplay`'s
 *   own system/predictive back stays enabled at the non-home root and [onBack] there selects
 *   Início.
 * - BACK-02: `NavDisplay.onBack` and every TopBar receive the same [onBack] lambda, so
 *   equivalent invocations from the same snapshot remove the same key.
 */
@Composable
fun ProductNavigationHost(
    mode: NavigationMode,
    selectedTab: ProductTab,
    accessBackStack: List<NavKey>,
    homeBackStack: List<NavKey>,
    groupsBackStack: List<NavKey>,
    noticesBackStack: List<NavKey>,
    moreBackStack: List<NavKey>,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accessEntries = rememberDecoratedNavEntries(
        backStack = accessBackStack,
        entryDecorators = rememberStackEntryDecorators(),
        entryProvider = scopeEntryProvider(STACK_ACCESS, entryProvider),
    )
    val homeEntries = rememberDecoratedNavEntries(
        backStack = homeBackStack,
        entryDecorators = rememberStackEntryDecorators(),
        entryProvider = scopeEntryProvider(STACK_HOME, entryProvider),
    )
    val groupsEntries = rememberDecoratedNavEntries(
        backStack = groupsBackStack,
        entryDecorators = rememberStackEntryDecorators(),
        entryProvider = scopeEntryProvider(STACK_GROUPS, entryProvider),
    )
    val noticesEntries = rememberDecoratedNavEntries(
        backStack = noticesBackStack,
        entryDecorators = rememberStackEntryDecorators(),
        entryProvider = scopeEntryProvider(STACK_NOTICES, entryProvider),
    )
    val moreEntries = rememberDecoratedNavEntries(
        backStack = moreBackStack,
        entryDecorators = rememberStackEntryDecorators(),
        entryProvider = scopeEntryProvider(STACK_MORE, entryProvider),
    )

    val displayed: List<NavEntry<NavKey>> = when (mode) {
        NavigationMode.ACCESS -> accessEntries
        NavigationMode.AUTHENTICATED -> when (selectedTab) {
            ProductTab.HOME -> homeEntries
            ProductTab.GROUPS -> homeEntries + groupsEntries
            ProductTab.NOTICES -> homeEntries + noticesEntries
            ProductTab.MORE -> homeEntries + moreEntries
        }
    }

    NavDisplay(entries = displayed, onBack = onBack, modifier = modifier)
}
