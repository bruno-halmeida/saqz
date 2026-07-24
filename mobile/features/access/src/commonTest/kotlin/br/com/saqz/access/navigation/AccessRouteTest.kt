package br.com.saqz.access.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AccessRouteTest {

    // ACCESSNAV-01: the route inventory is exhaustive - exactly these seven keys.
    private val allRoutes: List<AccessRoute> = listOf(
        AccessRoute.Starting,
        AccessRoute.Login,
        AccessRoute.Registration,
        AccessRoute.PasswordReset,
        AccessRoute.Verification,
        AccessRoute.NameCompletion,
        AccessRoute.PhoneCompletion,
        AccessRoute.Bootstrap,
    )

    @Test
    fun `route inventory contains exactly the eight specified keys`() {
        assertEquals(8, allRoutes.size)
        assertEquals(8, allRoutes.distinct().size)
    }

    @Test
    fun `every route is a NavKey`() {
        allRoutes.forEach { route -> assertTrue(route is NavKey) }
    }

    @Test
    fun `exhaustive when over AccessRoute covers every key without an else branch`() {
        allRoutes.forEach { route ->
            val label = when (route) {
                AccessRoute.Starting -> "Starting"
                AccessRoute.Login -> "Login"
                AccessRoute.Registration -> "Registration"
                AccessRoute.PasswordReset -> "PasswordReset"
                AccessRoute.Verification -> "Verification"
                AccessRoute.NameCompletion -> "NameCompletion"
                AccessRoute.PhoneCompletion -> "PhoneCompletion"
                AccessRoute.Bootstrap -> "Bootstrap"
            }
            assertTrue(label.isNotBlank())
        }
    }

    @Test
    fun `each route key is equal to itself and unequal to every other key`() {
        allRoutes.forEachIndexed { index, route ->
            assertEquals(route, route)
            allRoutes.forEachIndexed { otherIndex, other ->
                if (index != otherIndex) assertNotEquals(route, other)
            }
        }
    }

    @Test
    fun `each concrete route serializes and deserializes to an equal instance`() {
        assertEquals(AccessRoute.Starting, Json.decodeFromString(AccessRoute.Starting.serializer(), Json.encodeToString(AccessRoute.Starting.serializer(), AccessRoute.Starting)))
        assertEquals(AccessRoute.Login, Json.decodeFromString(AccessRoute.Login.serializer(), Json.encodeToString(AccessRoute.Login.serializer(), AccessRoute.Login)))
        assertEquals(AccessRoute.Registration, Json.decodeFromString(AccessRoute.Registration.serializer(), Json.encodeToString(AccessRoute.Registration.serializer(), AccessRoute.Registration)))
        assertEquals(AccessRoute.PasswordReset, Json.decodeFromString(AccessRoute.PasswordReset.serializer(), Json.encodeToString(AccessRoute.PasswordReset.serializer(), AccessRoute.PasswordReset)))
        assertEquals(AccessRoute.Verification, Json.decodeFromString(AccessRoute.Verification.serializer(), Json.encodeToString(AccessRoute.Verification.serializer(), AccessRoute.Verification)))
        assertEquals(AccessRoute.NameCompletion, Json.decodeFromString(AccessRoute.NameCompletion.serializer(), Json.encodeToString(AccessRoute.NameCompletion.serializer(), AccessRoute.NameCompletion)))
        assertEquals(AccessRoute.Bootstrap, Json.decodeFromString(AccessRoute.Bootstrap.serializer(), Json.encodeToString(AccessRoute.Bootstrap.serializer(), AccessRoute.Bootstrap)))
    }
}
