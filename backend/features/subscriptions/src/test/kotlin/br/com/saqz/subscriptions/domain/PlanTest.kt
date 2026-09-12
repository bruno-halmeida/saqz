package br.com.saqz.subscriptions.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanTest {
    @Test
    fun `every annual price is 75 percent of twelve monthly payments`() {
        Plan.entries.forEach { plan ->
            assertEquals(plan.monthlyPriceCents * 12 * 75 / 100, plan.annualPriceCents, plan.name)
        }
    }

    @Test
    fun `titular charges 39,90 monthly and 359,10 annually for one group and 25 athletes`() {
        assertEquals(3_990, Plan.TITULAR.monthlyPriceCents)
        assertEquals(35_910, Plan.TITULAR.annualPriceCents)
        assertEquals(1, Plan.TITULAR.maxGroups)
        assertEquals(25, Plan.TITULAR.maxAthletes)
        assertFalse(Plan.TITULAR.multiAdmin)
        assertFalse(Plan.TITULAR.reports)
        assertFalse(Plan.TITULAR.whatsappSla)
    }

    @Test
    fun `organizador charges 59,90 monthly and 539,10 annually for three groups with unlimited athletes`() {
        assertEquals(5_990, Plan.ORGANIZADOR.monthlyPriceCents)
        assertEquals(53_910, Plan.ORGANIZADOR.annualPriceCents)
        assertEquals(3, Plan.ORGANIZADOR.maxGroups)
        assertNull(Plan.ORGANIZADOR.maxAthletes)
        assertFalse(Plan.ORGANIZADOR.multiAdmin)
        assertFalse(Plan.ORGANIZADOR.reports)
        assertFalse(Plan.ORGANIZADOR.whatsappSla)
    }

    @Test
    fun `ilimitado charges 89,90 monthly and 809,10 annually for unlimited groups and athletes with all extras`() {
        assertEquals(8_990, Plan.ILIMITADO.monthlyPriceCents)
        assertEquals(80_910, Plan.ILIMITADO.annualPriceCents)
        assertNull(Plan.ILIMITADO.maxGroups)
        assertNull(Plan.ILIMITADO.maxAthletes)
        assertTrue(Plan.ILIMITADO.multiAdmin)
        assertTrue(Plan.ILIMITADO.reports)
        assertTrue(Plan.ILIMITADO.whatsappSla)
    }

    @Test
    fun `has exactly the three paid plans with no free tier`() {
        assertEquals(setOf(Plan.TITULAR, Plan.ORGANIZADOR, Plan.ILIMITADO), Plan.entries.toSet())
    }
}
