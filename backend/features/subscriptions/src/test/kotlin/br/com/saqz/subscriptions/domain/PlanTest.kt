package br.com.saqz.subscriptions.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanTest {
    @Test
    fun `titular charges 39,90 monthly and 399,00 annually for one group and 25 athletes`() {
        assertEquals(3_990, Plan.TITULAR.monthlyPriceCents)
        assertEquals(39_900, Plan.TITULAR.annualPriceCents)
        assertEquals(1, Plan.TITULAR.maxGroups)
        assertEquals(25, Plan.TITULAR.maxAthletes)
        assertFalse(Plan.TITULAR.multiAdmin)
        assertFalse(Plan.TITULAR.reports)
        assertFalse(Plan.TITULAR.whatsappSla)
    }

    @Test
    fun `organizador charges 59,90 monthly and 599,00 annually for three groups with unlimited athletes`() {
        assertEquals(5_990, Plan.ORGANIZADOR.monthlyPriceCents)
        assertEquals(59_900, Plan.ORGANIZADOR.annualPriceCents)
        assertEquals(3, Plan.ORGANIZADOR.maxGroups)
        assertNull(Plan.ORGANIZADOR.maxAthletes)
        assertFalse(Plan.ORGANIZADOR.multiAdmin)
        assertFalse(Plan.ORGANIZADOR.reports)
        assertFalse(Plan.ORGANIZADOR.whatsappSla)
    }

    @Test
    fun `ilimitado charges 89,90 monthly and 899,00 annually for unlimited groups and athletes with all extras`() {
        assertEquals(8_990, Plan.ILIMITADO.monthlyPriceCents)
        assertEquals(89_900, Plan.ILIMITADO.annualPriceCents)
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
