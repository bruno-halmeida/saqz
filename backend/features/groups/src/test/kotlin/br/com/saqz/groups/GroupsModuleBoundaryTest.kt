package br.com.saqz.groups

import br.com.saqz.sharedkernel.actor.AuthenticatedActor
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class GroupsModuleBoundaryTest {
    @Test
    fun `can consume shared kernel contracts without an Access dependency`() {
        val userId = UUID.randomUUID()

        assertEquals(userId, AuthenticatedActor(userId).userId)
    }
}
