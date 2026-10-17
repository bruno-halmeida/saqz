package br.com.saqz.groups.domain.plan

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanLimitPolicyTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test
    fun `group creation is allowed under the cap`() {
        assertTrue(PlanLimitPolicy.canCreateGroup(ownedGroupCount = 0, groupLimit = 1))
        assertTrue(PlanLimitPolicy.canCreateGroup(ownedGroupCount = 2, groupLimit = 3))
        assertTrue(PlanLimitPolicy.canCreateGroup(ownedGroupCount = 10, groupLimit = null))
    }

    @Test
    fun `group creation is refused at or above the cap including zero for no plan`() {
        assertFalse(PlanLimitPolicy.canCreateGroup(ownedGroupCount = 0, groupLimit = 0))
        assertFalse(PlanLimitPolicy.canCreateGroup(ownedGroupCount = 1, groupLimit = 1))
        assertFalse(PlanLimitPolicy.canCreateGroup(ownedGroupCount = 3, groupLimit = 3))
    }

    @Test
    fun `athlete entry is allowed under the cap and unlimited plans`() {
        assertTrue(PlanLimitPolicy.canEnterAsAthlete(setOf(a), b, athleteLimit = 25))
        assertTrue(PlanLimitPolicy.canEnterAsAthlete(emptySet(), a, athleteLimit = 1))
        assertTrue(PlanLimitPolicy.canEnterAsAthlete((1..100).map { UUID.randomUUID() }.toSet(), a, null))
    }

    @Test
    fun `athlete entry is refused when the group is at the cap`() {
        val full = (1..25).map { UUID.randomUUID() }.toSet()
        assertFalse(PlanLimitPolicy.canEnterAsAthlete(full, UUID.randomUUID(), athleteLimit = 25))
        assertTrue(PlanLimitPolicy.canEnterAsAthlete(full - full.first(), UUID.randomUUID(), athleteLimit = 25))
    }

    @Test
    fun `current member is never blocked even when the group is full`() {
        val open = (1..25).map { UUID.randomUUID() }.toSet()
        assertTrue(PlanLimitPolicy.canEnterAsAthlete(open, open.first(), athleteLimit = 25))
    }

    @Test
    fun `a member who left frees the slot at once`() {
        val full = (1..10).map { UUID.randomUUID() }.toSet()
        assertFalse(PlanLimitPolicy.canEnterAsAthlete(full, a, athleteLimit = 10))
        assertTrue(PlanLimitPolicy.canEnterAsAthlete(full - full.first(), a, athleteLimit = 10))
    }

    @Test
    fun `no subscription with limit zero refuses everyone who is not a member`() {
        assertFalse(PlanLimitPolicy.canEnterAsAthlete(emptySet(), a, athleteLimit = 0))
    }
}
