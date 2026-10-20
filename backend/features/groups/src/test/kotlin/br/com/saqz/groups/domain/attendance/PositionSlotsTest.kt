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

    @Test fun `5-1 suggestion is 1 setter, 1 opposite, 2 outsides, 2 middles, optional libero zero, times the teams`() {
        val two = PositionSlotSuggestion.of(GameSystem.FIVE_ONE)!!
        assertEquals(mapOf(
            AthletePosition.LEVANTADOR to 2, AthletePosition.OPOSTO to 2, AthletePosition.PONTA to 4,
            AthletePosition.CENTRAL to 4, AthletePosition.LIBERO to 0,
        ), two)
        assertEquals(6, PositionSlotSuggestion.of(GameSystem.FIVE_ONE, teams = 1)!!.values.sum())
        assertEquals(36, PositionSlotSuggestion.of(GameSystem.FIVE_ONE, teams = 6)!!.values.sum())
    }

    @Test fun `6-0 has no positions and teams out of range has no suggestion`() {
        assertEquals(emptyMap(), PositionSlotSuggestion.of(GameSystem.SIX_ZERO))
        assertNull(PositionSlotSuggestion.of(GameSystem.FIVE_ONE, teams = 0))
        assertNull(PositionSlotSuggestion.of(GameSystem.FIVE_ONE, teams = 7))
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
