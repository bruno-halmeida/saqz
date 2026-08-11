package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.SavedState
import androidx.savedstate.savedState
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute
import br.com.saqz.profile.presentation.navigation.ProfileRoute
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * VUL-35: a retained task can hold a back stack written by a build that still knew routes
 * this reset deleted. Restoring it must not throw — the decode happens before
 * [reconcileAccessStack] can canonicalize the stack, so a throw leaves the app unable to
 * reopen. Every payload the current key set cannot read falls back to the default stack.
 */
class SaqzAccessBackStackRestoreTest {

    // Stands in for a route the current build no longer registers: `AccessRoute.Registration`
    // and `AccessRoute.PasswordReset` (this slice), or any `GroupsRoute`/`ProductRoute` (C1).
    @Serializable
    private data object RemovedObjectRoute : NavKey

    // The same, but carrying an argument — `GroupsRoute.GameDetail(gameId)` shaped, so the
    // failure is a payload shape the current key set cannot even structurally accept.
    @Serializable
    private data class RemovedDataRoute(val id: String) : NavKey

    private val legacyConfiguration = SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(AccessRoute.Login::class, AccessRoute.Login.serializer())
                subclass(RemovedObjectRoute::class, RemovedObjectRoute.serializer())
                subclass(RemovedDataRoute::class, RemovedDataRoute.serializer())
            }
        }
    }

    private fun legacySavedStack(vararg keys: NavKey): SavedState = encodeToSavedState(
        NavBackStackSerializer(PolymorphicSerializer(NavKey::class)),
        NavBackStack(*keys),
        legacyConfiguration,
    )

    private fun restore(savedState: SavedState): List<NavKey> =
        decodeFromSavedState(saqzAccessBackStackSerializer, savedState, saqzLocalNavConfiguration)
            .toList()

    @Test
    fun `a saved stack topped by a removed route restores to the default stack`() {
        val restored = restore(legacySavedStack(AccessRoute.Login, RemovedObjectRoute))

        assertEquals(defaultAccessBackStack().toList(), restored)
        assertEquals(listOf<NavKey>(AccessRoute.Starting), restored)
    }

    @Test
    fun `a removed route carrying arguments also restores to the default stack`() {
        assertEquals(
            defaultAccessBackStack().toList(),
            restore(legacySavedStack(AccessRoute.Login, RemovedDataRoute("game-42"))),
        )
    }

    @Test
    fun `a structurally invalid payload restores to the default stack`() {
        assertEquals(defaultAccessBackStack().toList(), restore(savedState { putString("junk", "x") }))
    }

    @Test
    fun `a stack of surviving routes still restores exactly`() {
        // Discrimination check: the fallback must not swallow stacks that ARE readable,
        // otherwise the tests above would pass with a serializer that discards everything.
        val saved = encodeToSavedState(
            saqzAccessBackStackSerializer,
            NavBackStack<NavKey>(AccessRoute.Bootstrap),
            saqzLocalNavConfiguration,
        )

        assertEquals(listOf<NavKey>(AccessRoute.Bootstrap), restore(saved))
    }

    @Test
    fun `subscriptions routes round-trip through the real registered configuration`() {
        // VUL-108 P2: SubscriptionsRoute leaves must be registered in
        // saqzLocalNavConfiguration even before any screen pushes them, or the very first
        // navigation to one of them would fail to survive rotation.
        val saved = encodeToSavedState(
            saqzAccessBackStackSerializer,
            NavBackStack<NavKey>(
                SubscriptionsRoute.PlanSelection,
                SubscriptionsRoute.Payment(planId = "TITULAR", cycle = "MONTHLY", couponCode = "BEMVINDO10"),
                SubscriptionsRoute.PlanActive,
            ),
            saqzLocalNavConfiguration,
        )

        assertEquals(
            listOf<NavKey>(
                SubscriptionsRoute.PlanSelection,
                SubscriptionsRoute.Payment(planId = "TITULAR", cycle = "MONTHLY", couponCode = "BEMVINDO10"),
                SubscriptionsRoute.PlanActive,
            ),
            restore(saved),
        )
    }

    @Test
    fun `subscription required route round-trips through the real registered configuration`() {
        val saved = encodeToSavedState(
            saqzAccessBackStackSerializer,
            NavBackStack<NavKey>(SaqzShellDestination.Groups, SubscriptionRequired),
            saqzLocalNavConfiguration,
        )

        assertEquals(
            listOf<NavKey>(SaqzShellDestination.Groups, SubscriptionRequired),
            restore(saved),
        )
    }

    @Test
    fun `profile routes round-trip through the real registered configuration`() {
        val saved = encodeToSavedState(
            saqzAccessBackStackSerializer,
            NavBackStack<NavKey>(
                ProfileRoute.Edit,
                ProfileRoute.Exit("atleta@example.test"),
            ),
            saqzLocalNavConfiguration,
        )

        assertEquals(
            listOf<NavKey>(ProfileRoute.Edit, ProfileRoute.Exit("atleta@example.test")),
            restore(saved),
        )
    }

    @Test
    fun `the untolerant serializer still throws on the same payload`() {
        // Proves the tolerance above comes from the wrapper, not from the saved state
        // format silently accepting unregistered keys.
        assertFailsWith<Exception> {
            decodeFromSavedState(
                NavBackStackSerializer(PolymorphicSerializer(NavKey::class)),
                legacySavedStack(AccessRoute.Login, RemovedObjectRoute),
                saqzLocalNavConfiguration,
            )
        }
    }
}
