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
        assertEquals(35_910, titular.annualPriceCents)
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

    @Test
    fun `catalog exposes the store ids of each plan and the feature flags`() {
        val plans = ListPlans().execute().associateBy { it.id }

        val titular = plans.getValue(Plan.TITULAR)
        assertEquals("TITULAR", titular.name)
        assertEquals("app.saqz.titular.mensal", titular.appStoreMonthlyProductId)
        assertEquals("app.saqz.titular.anual", titular.appStoreAnnualProductId)
        assertEquals("app.saqz.titular", titular.googlePlayProductId)
        assertEquals("mensal", titular.googlePlayMonthlyBasePlanId)
        assertEquals("anual", titular.googlePlayAnnualBasePlanId)
        assertEquals(false, titular.reports)
        assertEquals(false, titular.whatsappSla)

        val organizador = plans.getValue(Plan.ORGANIZADOR)
        assertEquals("ORGANIZADOR", organizador.name)
        assertEquals("app.saqz.organizador.mensal", organizador.appStoreMonthlyProductId)
        assertEquals("app.saqz.organizador", organizador.googlePlayProductId)
        assertEquals(5_990, organizador.monthlyPriceCents)
        assertEquals(53_910, organizador.annualPriceCents)
        assertEquals(false, organizador.multiAdmin)
        assertEquals(false, organizador.reports)
        assertEquals(false, organizador.whatsappSla)

        assertEquals("app.saqz.ilimitado.anual", plans.getValue(Plan.ILIMITADO).appStoreAnnualProductId)
        assertEquals("app.saqz.ilimitado", plans.getValue(Plan.ILIMITADO).googlePlayProductId)
    }
}
