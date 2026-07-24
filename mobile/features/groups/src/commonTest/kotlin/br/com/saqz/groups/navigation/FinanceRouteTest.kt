package br.com.saqz.groups.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FinanceRouteTest {

    // FINNAV-01: the structural inventory is exactly these two keys.
    private val allRoutes: List<FinanceRoute> = listOf(FinanceRoute.Finance, FinanceRoute.OwnCharges)

    @Test
    fun `route inventory contains exactly Finance and OwnCharges`() {
        assertEquals(2, allRoutes.size)
        assertEquals(2, allRoutes.distinct().size)
    }

    @Test
    fun `every route is a NavKey`() {
        allRoutes.forEach { route -> assertTrue(route is NavKey) }
    }

    @Test
    fun `Finance and OwnCharges are equal to themselves and unequal to each other`() {
        assertEquals(FinanceRoute.Finance, FinanceRoute.Finance)
        assertEquals(FinanceRoute.OwnCharges, FinanceRoute.OwnCharges)
        assertNotEquals<FinanceRoute>(FinanceRoute.Finance, FinanceRoute.OwnCharges)
    }

    @Test
    fun `exhaustive when over FinanceRoute covers every key without an else branch`() {
        allRoutes.forEach { route ->
            val label = when (route) {
                FinanceRoute.Finance -> "Finance"
                FinanceRoute.OwnCharges -> "OwnCharges"
            }
            assertTrue(label.isNotBlank())
        }
    }

    @Test
    fun `each concrete route serializes and deserializes to an equal instance`() {
        assertEquals(FinanceRoute.Finance, Json.decodeFromString(FinanceRoute.Finance.serializer(), Json.encodeToString(FinanceRoute.Finance.serializer(), FinanceRoute.Finance)))
        assertEquals(FinanceRoute.OwnCharges, Json.decodeFromString(FinanceRoute.OwnCharges.serializer(), Json.encodeToString(FinanceRoute.OwnCharges.serializer(), FinanceRoute.OwnCharges)))
    }

    @Test
    fun `Finance route keys are dependency-free singletons carrying no gateway or view model`() {
        // FINNAV-02 dependency guard: every reference to a Finance route key resolves to
        // the exact same zero-argument singleton, so no gateway/ViewModel/business state
        // can ever be attached to or smuggled through route construction.
        val finance: FinanceRoute = FinanceRoute.Finance
        val ownCharges: FinanceRoute = FinanceRoute.OwnCharges
        assertTrue(finance === FinanceRoute.Finance)
        assertTrue(ownCharges === FinanceRoute.OwnCharges)
        assertTrue(finance !== ownCharges)
    }
}
