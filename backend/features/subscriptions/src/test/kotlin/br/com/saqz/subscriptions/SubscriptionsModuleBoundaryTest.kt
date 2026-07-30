package br.com.saqz.subscriptions

import br.com.saqz.sharedkernel.actor.AuthenticatedActor
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class SubscriptionsModuleBoundaryTest {
    @Test
    fun `can consume shared kernel contracts without another feature dependency`() {
        val userId = UUID.randomUUID()

        assertEquals(userId, AuthenticatedActor(userId).userId)
    }
}
