package br.com.saqz.groups.domain.attendance

import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.game.GameStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PositionSlotsTest {
    private fun load(position: AthletePosition, slots: Int, confirmed: Int = 0) = PositionLoad(position, slots, confirmed, 0)
    private val loads = listOf(
        load(AthletePosition.PONTA, 2), load(AthletePosition.CENTRAL, 2), load(AthletePosition.LEVANTADOR, 1), load(AthletePosition.LIBERO, 0),
    )

    @Test fun `5-1 suggestion by total - team of 6 is 1 setter and 1 opposite and 2 outsides and 2 middles and libero zero`() {
        assertEquals(
            mapOf(AthletePosition.LIBERO to 0, AthletePosition.PONTA to 6, AthletePosition.CENTRAL to 6, AthletePosition.OPOSTO to 3, AthletePosition.LEVANTADOR to 3),
            PositionSlotSuggestion.of(GameSystem.FIVE_ONE, 18),
        )
        assertEquals(12, PositionSlotSuggestion.of(GameSystem.FIVE_ONE, 12)!!.values.sum())
    }

    @Test fun `5-1 suggestion sends what is left of the division to the outsides and always sums to the total`() {
        // 20 = 3 times de 6 + 2: as 2 que sobram vão para ponta.
        val twenty = PositionSlotSuggestion.of(GameSystem.FIVE_ONE, 20)!!
        assertEquals(8, twenty.getValue(AthletePosition.PONTA))
        assertEquals(6, twenty.getValue(AthletePosition.CENTRAL))
        assertEquals(3, twenty.getValue(AthletePosition.OPOSTO))
        assertEquals(3, twenty.getValue(AthletePosition.LEVANTADOR))
        (4..100).forEach { assertEquals(it, PositionSlotSuggestion.of(GameSystem.FIVE_ONE, it)!!.values.sum()) }
    }

    @Test fun `6-0 has no positions and a total out of range has no suggestion`() {
        assertEquals(emptyMap(), PositionSlotSuggestion.of(GameSystem.SIX_ZERO, 18))
        assertNull(PositionSlotSuggestion.of(GameSystem.FIVE_ONE, 3))
        assertNull(PositionSlotSuggestion.of(GameSystem.FIVE_ONE, 101))
    }

    @Test fun `validation - empty turns positions off, negative, repeated, all zero and total out of range are refused`() {
        assertNull(PositionSlotsValidator.validate(emptyList()))
        assertNull(PositionSlotsValidator.validate(listOf(AthletePosition.PONTA to 2, AthletePosition.CENTRAL to 2, AthletePosition.LIBERO to 0)))
        assertEquals(PositionSlotsError.NEGATIVE_SLOTS, PositionSlotsValidator.validate(listOf(AthletePosition.PONTA to -1, AthletePosition.CENTRAL to 6)))
        assertEquals(PositionSlotsError.DUPLICATED_POSITION, PositionSlotsValidator.validate(listOf(AthletePosition.PONTA to 2, AthletePosition.PONTA to 2)))
        assertEquals(PositionSlotsError.NO_SLOTS, PositionSlotsValidator.validate(listOf(AthletePosition.PONTA to 0)))
        assertEquals(PositionSlotsError.TOTAL_OUT_OF_RANGE, PositionSlotsValidator.validate(listOf(AthletePosition.PONTA to 3)))
        assertEquals(PositionSlotsError.TOTAL_OUT_OF_RANGE, PositionSlotsValidator.validate(listOf(AthletePosition.PONTA to 101)))
    }

    private val eighteen = listOf(AthletePosition.PONTA to 6, AthletePosition.CENTRAL to 6, AthletePosition.OPOSTO to 3, AthletePosition.LEVANTADOR to 3)

    @Test fun `contract - 5-1 requires the positions and the optional total must match their sum`() {
        assertNull(PositionSlotsValidator.validate(GameSystem.FIVE_ONE, eighteen, total = 18))
        assertNull(PositionSlotsValidator.validate(GameSystem.FIVE_ONE, eighteen, total = null))
        assertEquals(PositionSlotsError.POSITIONS_REQUIRED, PositionSlotsValidator.validate(GameSystem.FIVE_ONE, emptyList(), total = 18))
        assertEquals(PositionSlotsError.NO_SLOTS, PositionSlotsValidator.validate(GameSystem.FIVE_ONE, listOf(AthletePosition.PONTA to 0), total = null))
        assertEquals(PositionSlotsError.TOTAL_MISMATCH, PositionSlotsValidator.validate(GameSystem.FIVE_ONE, eighteen, total = 16))
    }

    @Test fun `contract - 6-0 or no system requires an empty list`() {
        assertNull(PositionSlotsValidator.validate(GameSystem.SIX_ZERO, emptyList(), total = null))
        assertNull(PositionSlotsValidator.validate(null, emptyList(), total = 12))
        assertEquals(PositionSlotsError.POSITIONS_NOT_ALLOWED, PositionSlotsValidator.validate(GameSystem.SIX_ZERO, eighteen, total = 18))
        assertEquals(PositionSlotsError.POSITIONS_NOT_ALLOWED, PositionSlotsValidator.validate(null, eighteen, total = null))
    }

    @Test fun `requested position wins when the game has slots in it, otherwise it is refused`() {
        assertEquals(AthletePosition.CENTRAL, PositionChoice.resolve(loads, AthletePosition.CENTRAL, AthletePosition.PONTA, null))
        assertNull(PositionChoice.resolve(loads, AthletePosition.LIBERO, AthletePosition.PONTA, null))
        assertNull(PositionChoice.resolve(loads, AthletePosition.OPOSTO, null, null))
    }

    @Test fun `old app request - main, then secondary, then the position with most free slots, tie by enum order`() {
        assertEquals(AthletePosition.CENTRAL, PositionChoice.resolve(loads, null, AthletePosition.CENTRAL, AthletePosition.PONTA))
        assertEquals(AthletePosition.LEVANTADOR, PositionChoice.resolve(loads, null, AthletePosition.LIBERO, AthletePosition.LEVANTADOR))
        assertEquals(AthletePosition.PONTA, PositionChoice.resolve(loads, null, null, null))
        val busy = listOf(load(AthletePosition.PONTA, 2, 1), load(AthletePosition.CENTRAL, 2, 0))
        assertEquals(AthletePosition.CENTRAL, PositionChoice.resolve(busy, null, null, null))
        assertNull(PositionChoice.resolve(emptyList(), null, AthletePosition.PONTA, null))
    }

    private fun context(confirmed: Int, force: Boolean, source: AttendanceSource = AttendanceSource.ORGANIZER, current: AttendanceStatus? = null) = AttendanceDecisionContext(
        gameStatus = GameStatus.PUBLISHED, confirmationDeadline = Instant.parse("2026-08-01T10:00:00Z"), now = Instant.parse("2026-08-01T09:00:00Z"),
        capacity = 2, confirmedCount = confirmed, currentStatus = current, source = source, reason = "Chegou agora",
        membershipType = AthleteMembershipType.AVULSO, startsAt = Instant.parse("2026-08-02T10:00:00Z"), force = force,
    )

    @Test fun `organizer force confirms into a full position and marks it forced`() {
        val forced = assertIs<AttendanceDecision.Transition>(AttendanceTransitionPolicy.decide(context(confirmed = 2, force = true), AttendanceIntent.CONFIRM))
        assertEquals(AttendanceStatus.CONFIRMED, forced.newStatus)
        assertTrue(forced.forced)
        val fromWaitlist = assertIs<AttendanceDecision.Transition>(AttendanceTransitionPolicy.decide(context(2, true, current = AttendanceStatus.WAITLISTED), AttendanceIntent.CONFIRM))
        assertEquals(AttendanceStatus.CONFIRMED, fromWaitlist.newStatus)
    }

    @Test fun `without force, or when the athlete asks, a full position still means waitlist`() {
        val noForce = assertIs<AttendanceDecision.Transition>(AttendanceTransitionPolicy.decide(context(2, force = false), AttendanceIntent.CONFIRM))
        assertEquals(AttendanceStatus.WAITLISTED, noForce.newStatus)
        val self = assertIs<AttendanceDecision.Transition>(AttendanceTransitionPolicy.decide(context(2, force = true, source = AttendanceSource.SELF), AttendanceIntent.CONFIRM))
        assertEquals(AttendanceStatus.WAITLISTED, self.newStatus)
        val free = assertIs<AttendanceDecision.Transition>(AttendanceTransitionPolicy.decide(context(1, force = true), AttendanceIntent.CONFIRM))
        assertEquals(false, free.forced)
    }
}
