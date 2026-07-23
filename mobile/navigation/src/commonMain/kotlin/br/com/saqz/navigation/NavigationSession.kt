package br.com.saqz.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.groups.navigation.GroupsRoute
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.navigation.authorization.pruneDisallowedSuffix

/**
 * The four retained bottom-nav tabs (TAB-01). `Games`/`GameDetail` live inside the
 * `GROUPS` stack; they are not a fifth tab (see design.md, "Tabs and mode").
 */
enum class ProductTab { HOME, GROUPS, NOTICES, MORE }

/**
 * Single writer of route state for the four retained tab back stacks.
 *
 * Every public method fully applies its mutation before returning, so calls from
 * the TopBar back button and the system back handler (GROUPNAV-03, BACK-02) go
 * through the exact same [goBack] code path and observe/produce one consistent
 * state (STATE-03) -- there is no separate mutation path for either caller.
 *
 * This type implements the pure in-memory command surface: tab selection,
 * duplicate-safe forward navigation, the back algorithm, transient/selection
 * reconciliation, authorization pruning, and restoration/scope clearing.
 * Compose-level saved state (`rememberSerializable`/`rememberNavBackStack`) and
 * the conditional cold-relaunch snapshot store are added by later tasks
 * (T16-T21, T26); this class never talks to platform saved-state storage directly.
 */
class NavigationSession(
    private val stacks: Map<ProductTab, MutableList<NavKey>>,
    initialTab: ProductTab = ProductTab.HOME,
) {

    init {
        require(stacks.keys == ProductTab.entries.toSet()) {
            "NavigationSession requires exactly one stack per ProductTab"
        }
        require(stacks.values.all { it.isNotEmpty() }) {
            "Every tab stack must start with a root key"
        }
    }

    /** Captured before any mutation so scope-clearing (T10) can restore a safe root. */
    private val initialRoots: Map<ProductTab, NavKey> = stacks.mapValues { (_, stack) -> stack.first() }

    private var currentGroupId: String? = null

    var selectedTab: ProductTab = initialTab
        private set

    private val activeStack: MutableList<NavKey>
        get() = stacks.getValue(selectedTab)

    /** WHEN there is no valid previous entry (BACK-03/04), hosts hide TopBar back. */
    val canGoBack: Boolean
        get() = activeStack.size > 1 || selectedTab != ProductTab.HOME

    fun stackFor(tab: ProductTab): List<NavKey> = stacks.getValue(tab)

    /** Selecting the current tab is a no-op (TAB-02): it never duplicates its root. */
    fun selectTab(tab: ProductTab) {
        if (tab == selectedTab) return
        selectedTab = tab
    }

    /** Appends [key] to the active stack unless it is already the active top (STATE-03). */
    fun push(key: NavKey) {
        val stack = activeStack
        if (stack.lastOrNull() == key) return
        stack.add(key)
    }

    /**
     * BACK-04: pops a nested key when the active stack is deeper than its root;
     * otherwise, at a non-home tab root, selects Início; at the Início root,
     * returns `false` so the platform/`NavDisplay` handles back.
     */
    fun goBack(): Boolean {
        val stack = activeStack
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (selectedTab != ProductTab.HOME) {
            selectTab(ProductTab.HOME)
            return true
        }
        return false
    }

    /**
     * GROUPNAV-06 / STATE-01..03: reconciles the GROUPS stack's root with the
     * authoritative [GroupSelectionState]. `Loading`/`LoadError` are transient and
     * are replaced, never pushed, so back can never reveal an obsolete transient
     * key (STATE-02). `Setup`/`Selector` are stable roots that are likewise
     * replaced in place while the selection flow has not navigated deeper. Once
     * the GROUPS stack has grown past its root (deeper navigation already
     * occurred), this reconciliation is a no-op; authorization pruning (T09) owns
     * that case.
     */
    fun reconcileGroupSelection(state: GroupSelectionState) {
        val stack = stacks.getValue(ProductTab.GROUPS)
        if (stack.size != 1) return
        val target = state.toGroupsRoute()
        if (stack[0] != target) stack[0] = target
    }

    /**
     * AUTHZ-01..02, RESTORE-04: reconciles every retained authenticated stack
     * except Início (`AppHome` is never role-gated) against [isAllowed]. Each
     * stack is walked backward preserving its nearest allowed prefix -- the
     * active stack and every inactive stack are pruned identically. If
     * membership is lost, the GROUPS stack is cleared and replaced with
     * [membershipLostFallback] (Selector or Setup) and Grupos becomes the
     * active tab; otherwise a stack left with no allowed entry is reset to
     * [fallback] (GroupHome).
     */
    fun pruneDisallowed(
        isAllowed: (NavKey) -> Boolean,
        membershipActive: Boolean,
        fallback: NavKey,
        membershipLostFallback: NavKey,
    ) {
        if (!membershipActive) {
            val groupsStack = stacks.getValue(ProductTab.GROUPS)
            groupsStack.clear()
            groupsStack.add(membershipLostFallback)
            selectTab(ProductTab.GROUPS)
            return
        }
        for (tab in ProductTab.entries) {
            if (tab == ProductTab.HOME) continue
            pruneDisallowedSuffix(stacks.getValue(tab), isAllowed, fallback)
        }
    }

    /**
     * RESTORE-02: clears every retained tab stack back to its initial root and
     * selects Início, before the host disposes the authenticated mode on logout.
     */
    fun clearAuthenticated() {
        for (tab in ProductTab.entries) {
            resetStack(tab)
        }
        currentGroupId = null
        selectedTab = ProductTab.HOME
    }

    /**
     * RESTORE-03, STATE-03: clears every group-bound stack (GROUPS, NOTICES,
     * MORE; Início is app-local, not group-scoped) back to its initial root
     * before switching to [groupId]. A no-op when [groupId] is already the
     * current group scope, so redundant reconciliation calls stay idempotent.
     */
    fun clearGroupScope(groupId: String) {
        if (currentGroupId == groupId) return
        currentGroupId = groupId
        resetStack(ProductTab.GROUPS)
        resetStack(ProductTab.NOTICES)
        resetStack(ProductTab.MORE)
    }

    private fun resetStack(tab: ProductTab) {
        val stack = stacks.getValue(tab)
        stack.clear()
        stack.add(initialRoots.getValue(tab))
    }

    private fun GroupSelectionState.toGroupsRoute(): NavKey = when (this) {
        GroupSelectionState.NoGroup -> GroupsRoute.Setup
        is GroupSelectionState.Selector -> GroupsRoute.Selector
        is GroupSelectionState.Loading -> GroupsRoute.Loading
        is GroupSelectionState.LoadError -> GroupsRoute.LoadError
        is GroupSelectionState.Selected -> GroupsRoute.GroupHome
    }
}
