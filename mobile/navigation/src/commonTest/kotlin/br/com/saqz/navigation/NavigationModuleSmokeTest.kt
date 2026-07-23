package br.com.saqz.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Smoke test for T02: proves the `:navigation` module resolves and compiles against
 * the Navigation Compose 3 Multiplatform artifacts (navigation3-ui, transitively
 * navigation3-runtime) pinned in the version catalog.
 */
class NavigationModuleSmokeTest {

    private data object SmokeKey : NavKey

    @Test
    fun `navigation3 NavKey contract resolves from the pinned artifact`() {
        assertIs<NavKey>(SmokeKey)
    }
}
