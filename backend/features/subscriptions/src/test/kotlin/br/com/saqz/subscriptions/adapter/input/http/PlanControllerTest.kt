package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.subscriptions.application.ListPlans
import br.com.saqz.subscriptions.domain.Plan
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PlanControllerTest {
    @Test
    fun `lists catalog from use case`() {
        val response = PlanController(ListPlans()).list()

        assertEquals(3, response.size)
        assertEquals(Plan.TITULAR, response.first().id)
        assertEquals(3_990, response.first().monthlyPriceCents)
    }
}
