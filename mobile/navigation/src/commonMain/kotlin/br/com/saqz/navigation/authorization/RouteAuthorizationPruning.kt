package br.com.saqz.navigation.authorization

import androidx.navigation3.runtime.NavKey

/**
 * AUTHZ-01/02, RESTORE-04: walks [stack] backward, removing its disallowed
 * suffix while preserving the nearest allowed prefix (stopping at the first
 * allowed predecessor). If nothing in [stack] remains allowed, installs
 * [fallback] as its sole entry.
 *
 * Pure mutation helper: operates on any `MutableList<NavKey>`, so it is testable
 * without a `NavigationSession`/`NavBackStack` (see design.md, "NavigationSession"
 * -> "Testing").
 */
fun pruneDisallowedSuffix(
    stack: MutableList<NavKey>,
    isAllowed: (NavKey) -> Boolean,
    fallback: NavKey,
) {
    while (stack.size > 1 && !isAllowed(stack.last())) {
        stack.removeAt(stack.lastIndex)
    }
    if (!isAllowed(stack.last())) {
        stack.clear()
        stack.add(fallback)
    }
}
