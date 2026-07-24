package br.com.saqz.navigation.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import br.com.saqz.designsystem.component.SaqzTopBar
import br.com.saqz.groups.navigation.GroupsRoute
import br.com.saqz.navigation.NavigationSession
import br.com.saqz.navigation.ProductRoute
import br.com.saqz.navigation.ProductTab

/**
 * GROUPNAV-01/REG-01: which shell wraps a Groups route's content.
 *
 * - [SELECTOR]: `AppHome` and `Selector` keep the existing four-item selector
 *   chrome (bottom menu, no top bar) -- design.md, GroupsNavigationHost "Chrome".
 * - [SCOPED]: every group-scoped entry uses the existing top bar and omits the
 *   bottom menu (MENU-08).
 * - [BARE]: Loading/LoadError/Setup/CreateGroup preserve their current chrome-free
 *   behavior.
 */
enum class GroupsChrome { SELECTOR, SCOPED, BARE }

fun chromeFor(key: NavKey): GroupsChrome = when (key) {
    ProductRoute.AppHome, GroupsRoute.Selector -> GroupsChrome.SELECTOR
    GroupsRoute.Setup,
    GroupsRoute.Loading,
    GroupsRoute.LoadError,
    GroupsRoute.CreateGroup,
    -> GroupsChrome.BARE
    else -> GroupsChrome.SCOPED
}

/**
 * GROUPNAV-04 / BACK-03: canonicalizes the GROUPS stack when a group becomes the
 * selected destination. A single-membership user gets `[GroupHome]`, so the group
 * stack is at its root and the TopBar back is hidden ([groupsBackVisible] false).
 * Multiple memberships get `[Selector, GroupHome]`, so back returns to Selector.
 * No-op when the stack already equals the target shape (STATE-03 idempotency).
 */
fun canonicalizeSelectedGroup(stack: MutableList<NavKey>, multipleMemberships: Boolean) {
    val target: List<NavKey> = if (multipleMemberships) {
        listOf(GroupsRoute.Selector, GroupsRoute.GroupHome)
    } else {
        listOf(GroupsRoute.GroupHome)
    }
    if (stack != target) {
        stack.clear()
        stack.addAll(target)
    }
}

/**
 * GROUPNAV-02/04, BACK-01/03: the Groups TopBar back is shown only when the GROUPS
 * stack is deeper than its root, so back pops the actual predecessor (GameDetail ->
 * Games -> GroupHome/Selector). A single-membership `[GroupHome]` root (depth 1)
 * hides it.
 */
fun groupsBackVisible(groupsStackDepth: Int): Boolean = groupsStackDepth > 1

/**
 * GROUPNAV-03/BACK-02: group-scoped chrome. The TopBar back delegates to the same
 * [onBack] callback the system back uses (`NavigationSession.goBack`), and is hidden
 * when [canGoBack] is false (BACK-03). No bottom menu (MENU-08).
 */
@Composable
fun GroupsScopedScaffold(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SaqzTopBar(title = title, onBack = if (canGoBack) onBack else null)
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * GROUPNAV-01/MENU-13: selector chrome. Content above the app-supplied four-item
 * [bottomBar] (Início/Grupos/Avisos/Mais). The bottom bar is a slot because its
 * labels/icons are Groups module-internal resources that `:navigation` cannot import.
 */
@Composable
fun GroupsSelectorScaffold(
    bottomBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        bottomBar()
    }
}

/**
 * MODNAV-01/GROUPNAV-01: installs the host-owned `AppHome` slot plus every
 * feature-owned Groups route into the shared product entry provider. Localized
 * titles ([titleFor]), the four-item tab bar ([bottomBar]), and each route's screen
 * ([content]) are supplied as bindings by the composition root (T23), because Groups
 * strings/icons/screens are module-internal to `:features:groups` and cannot be
 * imported here (design.md, "Runtime State Ownership" -> composition-root adapter).
 * This host owns only chrome selection and the shared back callback; it never looks
 * up Koin or projects business state.
 */
fun EntryProviderScope<NavKey>.installGroupsEntries(
    session: NavigationSession,
    titleFor: @Composable (NavKey) -> String,
    bottomBar: @Composable () -> Unit,
    content: @Composable (NavKey) -> Unit,
) {
    @Composable
    fun install(key: NavKey) {
        when (chromeFor(key)) {
            GroupsChrome.SELECTOR -> GroupsSelectorScaffold(bottomBar) { content(key) }
            GroupsChrome.SCOPED -> GroupsScopedScaffold(
                title = titleFor(key),
                canGoBack = groupsBackVisible(session.stackFor(ProductTab.GROUPS).size),
                onBack = session::goBack,
            ) { content(key) }
            GroupsChrome.BARE -> content(key)
        }
    }
    entry<ProductRoute.AppHome> { install(ProductRoute.AppHome) }
    entry<GroupsRoute.Setup> { install(GroupsRoute.Setup) }
    entry<GroupsRoute.Selector> { install(GroupsRoute.Selector) }
    entry<GroupsRoute.Loading> { install(GroupsRoute.Loading) }
    entry<GroupsRoute.LoadError> { install(GroupsRoute.LoadError) }
    entry<GroupsRoute.GroupHome> { install(GroupsRoute.GroupHome) }
    entry<GroupsRoute.ProfileCompletion> { install(GroupsRoute.ProfileCompletion) }
    entry<GroupsRoute.People> { install(GroupsRoute.People) }
    entry<GroupsRoute.Games> { install(GroupsRoute.Games) }
    entry<GroupsRoute.GameDetail> { key -> install(key) }
    entry<GroupsRoute.GameEditor> { install(GroupsRoute.GameEditor) }
    entry<GroupsRoute.Notices> { install(GroupsRoute.Notices) }
    entry<GroupsRoute.More> { install(GroupsRoute.More) }
    entry<GroupsRoute.Settings> { install(GroupsRoute.Settings) }
    entry<GroupsRoute.Memberships> { install(GroupsRoute.Memberships) }
    entry<GroupsRoute.Invite> { install(GroupsRoute.Invite) }
    entry<GroupsRoute.CreateGroup> { install(GroupsRoute.CreateGroup) }
}
