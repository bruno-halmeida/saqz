package br.com.saqz.navigation.authorization

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure predecessor/fallback matrix for [pruneDisallowedSuffix] (T09).
 * Derived from AUTHZ-01..02.
 */
class RouteAuthorizationPruningTest {

    private data class Key(val name: String, val allowed: Boolean) : NavKey

    private val isAllowed: (NavKey) -> Boolean = { (it as Key).allowed }

    @Test
    fun `preserves the nearest allowed prefix and removes the disallowed suffix`() {
        val stack = mutableListOf<NavKey>(
            Key("GroupHome", allowed = true),
            Key("People", allowed = false),
            Key("GameDetail", allowed = false),
        )

        pruneDisallowedSuffix(stack, isAllowed, fallback = Key("GroupHome", allowed = true))

        assertEquals(listOf<NavKey>(Key("GroupHome", allowed = true)), stack)
    }

    @Test
    fun `stops at the first allowed predecessor`() {
        val stack = mutableListOf<NavKey>(
            Key("GroupHome", allowed = true),
            Key("Games", allowed = true),
            Key("GameDetail", allowed = false),
        )

        pruneDisallowedSuffix(stack, isAllowed, fallback = Key("GroupHome", allowed = true))

        assertEquals(
            listOf<NavKey>(Key("GroupHome", allowed = true), Key("Games", allowed = true)),
            stack,
        )
    }

    @Test
    fun `installs the fallback when no entry in the stack remains allowed`() {
        val stack = mutableListOf<NavKey>(Key("People", allowed = false))
        val fallback: NavKey = Key("GroupHome", allowed = true)

        pruneDisallowedSuffix(stack, isAllowed, fallback)

        assertEquals(listOf(fallback), stack)
    }

    @Test
    fun `a stack that is already fully allowed is left untouched`() {
        val stack = mutableListOf<NavKey>(Key("Notices", allowed = true))

        pruneDisallowedSuffix(stack, isAllowed, fallback = Key("GroupHome", allowed = true))

        assertEquals(listOf<NavKey>(Key("Notices", allowed = true)), stack)
    }
}
