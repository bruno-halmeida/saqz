package br.com.saqz.navigation.serialization

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.groups.navigation.FinanceRoute
import br.com.saqz.groups.navigation.GroupsRoute
import br.com.saqz.navigation.ProductRoute
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * MODNAV-05: exhaustive round-trip for every concrete NavKey leaf under the exact
 * [navigationSavedStateConfiguration], proving reflection-free polymorphic
 * serialization for iOS/non-JVM targets. Omitting a registration below would make
 * its corresponding case fail with a [SerializationException].
 */
class NavigationSavedStateConfigurationTest {

    private val navKeyStrategy = PolymorphicSerializer(NavKey::class)

    private fun roundTrip(key: NavKey): NavKey {
        val savedState = encodeToSavedState(navKeyStrategy, key, navigationSavedStateConfiguration)
        return decodeFromSavedState(navKeyStrategy, savedState, navigationSavedStateConfiguration)
    }

    private val allKeys: List<NavKey> = listOf(
        AccessRoute.Starting,
        AccessRoute.Login,
        AccessRoute.Registration,
        AccessRoute.PasswordReset,
        AccessRoute.Verification,
        AccessRoute.NameCompletion,
        AccessRoute.Bootstrap,
        GroupsRoute.Setup,
        GroupsRoute.Selector,
        GroupsRoute.Loading,
        GroupsRoute.LoadError,
        GroupsRoute.GroupHome,
        GroupsRoute.ProfileCompletion,
        GroupsRoute.People,
        GroupsRoute.Games,
        GroupsRoute.GameDetail("game-42"),
        GroupsRoute.Notices,
        GroupsRoute.More,
        GroupsRoute.Settings,
        GroupsRoute.Memberships,
        GroupsRoute.Invite,
        GroupsRoute.CreateGroup,
        FinanceRoute.Finance,
        FinanceRoute.OwnCharges,
        ProductRoute.AppHome,
    )

    @Test
    fun `every registered key round-trips to an equal instance`() {
        assertEquals(25, allKeys.size)
        allKeys.forEach { key -> assertEquals(key, roundTrip(key)) }
    }

    @Test
    fun `GameDetail round-trips preserving its gameId argument`() {
        val decoded = roundTrip(GroupsRoute.GameDetail("game-77")) as GroupsRoute.GameDetail
        assertEquals("game-77", decoded.gameId)
    }

    @Test
    fun `an unregistered NavKey fails to encode under the exact configuration`() {
        // Discrimination check: a NavKey that was never registered in
        // navigationSavedStateConfiguration must fail, proving the round-trip
        // tests above are only green because every real leaf IS registered.
        assertFailsWith<SerializationException> {
            encodeToSavedState(navKeyStrategy, UnregisteredProbeKey, navigationSavedStateConfiguration)
        }
    }
}

private data object UnregisteredProbeKey : NavKey
