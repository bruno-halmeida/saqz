package br.com.saqz.groups.domain.attendance

import br.com.saqz.groups.domain.game.GameStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

class AttendanceTransitionPolicyTest {
    @Test fun `no response confirms below capacity`() = transition(context(), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true)
    @Test fun `no response waitlists at capacity`() = transition(context(confirmed = 2), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `no response waitlists over capacity`() = transition(context(confirmed = 3), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `no response may decline`() = transition(context(), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED, changed = true)
    @Test fun `confirmed member may withdraw to declined`() = transition(context(current = AttendanceStatus.CONFIRMED, confirmed = 1), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED, changed = true)
    @Test fun `declined member may confirm when a spot exists`() = transition(context(current = AttendanceStatus.DECLINED), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true)
    @Test fun `waitlisted member may withdraw to declined`() = transition(context(current = AttendanceStatus.WAITLISTED, confirmed = 2), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED, changed = true)
    @Test fun `confirmed retry is an unchanged confirmation`() = transition(context(current = AttendanceStatus.CONFIRMED, confirmed = 2), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED)
    @Test fun `declined retry is an unchanged decline`() = transition(context(current = AttendanceStatus.DECLINED), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED)
    @Test fun `waitlisted retry keeps stable queue identity`() = transition(context(current = AttendanceStatus.WAITLISTED, confirmed = 0), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED)
    @Test fun `declined rejoin at capacity allocates a new sequence`() = transition(context(current = AttendanceStatus.DECLINED, confirmed = 2), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `capacity decrease never demotes an existing confirmation`() = transition(context(current = AttendanceStatus.CONFIRMED, capacity = 1, confirmed = 2), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED)
    @Test fun `capacity decrease blocks a declined member from confirmation`() = transition(context(current = AttendanceStatus.DECLINED, capacity = 1, confirmed = 2), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `draft rejects athlete self response`() = denied(context(status = GameStatus.DRAFT), AttendanceIntent.CONFIRM, AttendanceDenial.NOT_PUBLISHED)
    @Test fun `draft rejects organizer override`() = denied(organizer(status = GameStatus.DRAFT), AttendanceIntent.CONFIRM, AttendanceDenial.NOT_PUBLISHED)
    @Test fun `cancelled game freezes athlete response`() = denied(context(status = GameStatus.CANCELLED), AttendanceIntent.DECLINE, AttendanceDenial.FROZEN)
    @Test fun `cancelled game freezes organizer override`() = denied(organizer(status = GameStatus.CANCELLED), AttendanceIntent.DECLINE, AttendanceDenial.FROZEN)
    @Test fun `completed game freezes athlete response`() = denied(context(status = GameStatus.COMPLETED), AttendanceIntent.CONFIRM, AttendanceDenial.FROZEN)
    @Test fun `completed game freezes organizer override`() = denied(organizer(status = GameStatus.COMPLETED), AttendanceIntent.CONFIRM, AttendanceDenial.FROZEN)
    @Test fun `athlete may respond before deadline`() = transition(context(now = DEADLINE.minusSeconds(1)), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true)
    @Test fun `athlete may respond exactly at deadline`() = transition(context(now = DEADLINE), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true)
    @Test fun `athlete cannot respond after deadline`() = denied(context(now = DEADLINE.plusNanos(1)), AttendanceIntent.CONFIRM, AttendanceDenial.DEADLINE_PASSED)
    @Test fun `organizer may override after deadline`() = transition(organizer(now = DEADLINE.plusSeconds(1), reason = "Presença confirmada"), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true, reason = "Presença confirmada")
    @Test fun `organizer override requires reason`() = denied(organizer(reason = null), AttendanceIntent.CONFIRM, AttendanceDenial.REASON_REQUIRED)
    @Test fun `organizer override rejects blank reason`() = denied(organizer(reason = "  "), AttendanceIntent.CONFIRM, AttendanceDenial.REASON_REQUIRED)
    @Test fun `organizer override rejects one character reason`() = denied(organizer(reason = "X"), AttendanceIntent.CONFIRM, AttendanceDenial.REASON_INVALID)
    @Test fun `organizer override rejects reason over limit`() = denied(organizer(reason = "x".repeat(501)), AttendanceIntent.CONFIRM, AttendanceDenial.REASON_INVALID)
    @Test fun `organizer override rejects control character`() = denied(organizer(reason = "fora\ndo prazo"), AttendanceIntent.CONFIRM, AttendanceDenial.REASON_INVALID)
    @Test fun `organizer reason is trimmed into audit transition`() = transition(organizer(reason = "  Ajuste manual  "), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED, changed = true, reason = "Ajuste manual")
    @Test fun `self transition never accepts audit reason`() = transition(context(reason = "client authored"), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED, changed = true, reason = null)
    @Test fun `organizer confirmation still respects full capacity`() = transition(organizer(confirmed = 2, reason = "Lista de espera"), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true, reason = "Lista de espera")
    @Test fun `only a newly confirmed transition requests a charge`() { transition(context(current = AttendanceStatus.DECLINED), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true); transition(context(current = AttendanceStatus.CONFIRMED), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, charge = false) }

    private fun transition(
        context: AttendanceDecisionContext,
        intent: AttendanceIntent,
        status: AttendanceStatus,
        changed: Boolean = false,
        allocate: Boolean = false,
        charge: Boolean = false,
        reason: String? = context.reason?.trim(),
    ) {
        val result = assertIs<AttendanceDecision.Transition>(AttendanceTransitionPolicy.decide(context, intent))
        assertEquals(status, result.newStatus)
        assertEquals(changed, result.changed)
        assertEquals(allocate, result.allocateWaitlistSequence)
        assertEquals(charge, result.createGameCharge)
        assertEquals(reason, result.reason)
    }

    private fun denied(context: AttendanceDecisionContext, intent: AttendanceIntent, reason: AttendanceDenial) =
        assertEquals(AttendanceDecision.Denied(reason), AttendanceTransitionPolicy.decide(context, intent))

    private fun context(
        status: GameStatus = GameStatus.PUBLISHED,
        now: Instant = DEADLINE.minusSeconds(1),
        capacity: Int = 2,
        confirmed: Int = 0,
        current: AttendanceStatus? = null,
        reason: String? = null,
    ) = AttendanceDecisionContext(status, DEADLINE, now, capacity, confirmed, current, AttendanceSource.SELF, reason)

    private fun organizer(
        status: GameStatus = GameStatus.PUBLISHED,
        now: Instant = DEADLINE.minusSeconds(1),
        capacity: Int = 2,
        confirmed: Int = 0,
        current: AttendanceStatus? = null,
        reason: String? = "Ajuste do organizador",
    ) = AttendanceDecisionContext(status, DEADLINE, now, capacity, confirmed, current, AttendanceSource.ORGANIZER, reason)

    private companion object { val DEADLINE: Instant = Instant.parse("2026-08-11T22:30:00Z") }
}
