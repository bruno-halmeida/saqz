package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListPlansTest {
    @Test
    fun `catalog exposes the three plans with prices limits and features`() {
        val plans = ListPlans().execute()

        assertEquals(3, plans.size)
        assertEquals(
            listOf(Plan.TITULAR, Plan.ORGANIZADOR, Plan.ILIMITADO),
            plans.map { it.id },
        )

        val titular = plans.first { it.id == Plan.TITULAR }
        assertEquals(3_990, titular.monthlyPriceCents)
        assertEquals(39_900, titular.annualPriceCents)
        assertEquals(1, titular.maxGroups)
        assertEquals(25, titular.maxAthletes)
        assertEquals(false, titular.multiAdmin)

        val organizador = plans.first { it.id == Plan.ORGANIZADOR }
        assertEquals(3, organizador.maxGroups)
        assertNull(organizador.maxAthletes)

        val ilimitado = plans.first { it.id == Plan.ILIMITADO }
        assertNull(ilimitado.maxGroups)
        assertTrue(ilimitado.multiAdmin)
        assertTrue(ilimitado.reports)
        assertTrue(ilimitado.whatsappSla)
    }
}
