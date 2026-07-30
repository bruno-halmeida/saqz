package br.com.saqz.groups.domain.plan

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanLimitPolicyTest {
    private val now = Instant.parse("2026-07-30T12:00:00Z")
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val c = UUID.randomUUID()
    private val d = UUID.randomUUID()

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
        assertTrue(PlanLimitPolicy.canEnterAsAthlete(setOf(a), setOf(a), b, athleteLimit = 25))
        assertTrue(PlanLimitPolicy.canEnterAsAthlete(emptySet(), emptySet(), a, athleteLimit = 1))
        assertTrue(
            PlanLimitPolicy.canEnterAsAthlete(
                emptySet(),
                (1..100).map { UUID.randomUUID() }.toSet(),
                a,
                null,
            ),
        )
    }

    @Test
    fun `athlete entry is refused at the titular cap of 25`() {
        val full = (1..25).map { UUID.randomUUID() }.toSet()
        assertFalse(PlanLimitPolicy.canEnterAsAthlete(full, full, UUID.randomUUID(), athleteLimit = 25))
        assertTrue(PlanLimitPolicy.canEnterAsAthlete(full, full, full.first(), athleteLimit = 25))
    }

    @Test
    fun `open membership is idempotent even when the roster is full`() {
        val open = (1..25).map { UUID.randomUUID() }.toSet()
        assertTrue(PlanLimitPolicy.canEnterAsAthlete(open, open, open.first(), athleteLimit = 25))
    }

    @Test
    fun `occupying athletes union members waitlist and recently closed`() {
        val closedRecent = ClosedAthleteOccupancy(c, now.minusSeconds(60))
        val closedOld = ClosedAthleteOccupancy(d, now.minus(PlanLimitPolicy.RECENTLY_CLOSED_WINDOW).minusSeconds(1))

        val occupying = PlanLimitPolicy.occupyingAthletes(
            openMemberIds = setOf(a),
            openWaitlistIds = setOf(b),
            closedOccupancies = listOf(closedRecent, closedOld),
            now = now,
        )

        assertEquals(setOf(a, b, c), occupying)
        assertFalse(d in occupying)
    }

    @Test
    fun `waitlist gaming cannot bypass the athlete cap`() {
        val members = (1..20).map { UUID.randomUUID() }.toSet()
        val waitlist = (1..5).map { UUID.randomUUID() }.toSet()
        val open = members + waitlist
        val occupying = PlanLimitPolicy.occupyingAthletes(
            openMemberIds = members,
            openWaitlistIds = waitlist,
            closedOccupancies = emptyList(),
            now = now,
        )

        assertEquals(25, occupying.size)
        assertFalse(PlanLimitPolicy.canEnterAsAthlete(open, occupying, UUID.randomUUID(), athleteLimit = 25))
    }

    @Test
    fun `recently closed athletes still occupy slots for thirty days`() {
        val closed = (1..25).map {
            ClosedAthleteOccupancy(UUID.randomUUID(), now.minusSeconds(86_400))
        }
        val occupying = PlanLimitPolicy.occupyingAthletes(
            openMemberIds = emptySet(),
            openWaitlistIds = emptySet(),
            closedOccupancies = closed,
            now = now,
        )

        assertEquals(25, occupying.size)
        assertFalse(PlanLimitPolicy.canEnterAsAthlete(emptySet(), occupying, UUID.randomUUID(), athleteLimit = 25))
    }

    @Test
    fun `recently closed athlete cannot reenter when owner has no subscription`() {
        val removed = UUID.randomUUID()
        val occupying = PlanLimitPolicy.occupyingAthletes(
            openMemberIds = emptySet(),
            openWaitlistIds = emptySet(),
            closedOccupancies = listOf(ClosedAthleteOccupancy(removed, now.minusSeconds(3_600))),
            now = now,
        )

        assertFalse(
            PlanLimitPolicy.canEnterAsAthlete(
                currentlyOpenAthleteIds = emptySet(),
                occupyingAthleteIds = occupying,
                athleteId = removed,
                athleteLimit = 0,
            ),
        )
    }
}
