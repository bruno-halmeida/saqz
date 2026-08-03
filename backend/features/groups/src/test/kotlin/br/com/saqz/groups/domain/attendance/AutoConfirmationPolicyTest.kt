package br.com.saqz.groups.domain.attendance

import br.com.saqz.groups.domain.AthleteMembershipType
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class AutoConfirmationPolicyTest {
    @Test
    fun `only enabled mensalistas are assigned and avulsos are ignored`() {
        val mensalista = candidate("00000000-0000-0000-0000-000000000001", AthleteMembershipType.MENSALISTA, true)
        val optOut = candidate("00000000-0000-0000-0000-000000000002", AthleteMembershipType.MENSALISTA, false)
        val avulso = candidate("00000000-0000-0000-0000-000000000003", AthleteMembershipType.AVULSO, true)

        assertEquals(
            listOf(AutoConfirmationAssignment(mensalista.memberId, AttendanceStatus.CONFIRMED)),
            AutoConfirmationPolicy.decide(listOf(avulso, optOut, mensalista), capacity = 2, confirmedCount = 0),
        )
    }

    @Test
    fun `assignments fill capacity then put excess in the mensalista waitlist tier`() {
        val first = candidate("00000000-0000-0000-0000-000000000001", enabled = true, joinedAt = "2026-08-01T10:00:00Z")
        val second = candidate("00000000-0000-0000-0000-000000000002", enabled = true, joinedAt = "2026-08-02T10:00:00Z")
        val third = candidate("00000000-0000-0000-0000-000000000003", enabled = true, joinedAt = "2026-08-03T10:00:00Z")

        assertEquals(
            listOf(
                AutoConfirmationAssignment(first.memberId, AttendanceStatus.CONFIRMED),
                AutoConfirmationAssignment(second.memberId, AttendanceStatus.WAITLISTED),
                AutoConfirmationAssignment(third.memberId, AttendanceStatus.WAITLISTED),
            ),
            AutoConfirmationPolicy.decide(listOf(third, second, first), capacity = 2, confirmedCount = 1),
        )
    }

    @Test
    fun `group entry date and uuid tie breaker make assignments deterministic`() {
        val laterId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val earlierId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val candidates = listOf(
            AutoConfirmationCandidate(laterId, AthleteMembershipType.MENSALISTA, true, Instant.parse("2026-08-01T10:00:00Z")),
            AutoConfirmationCandidate(earlierId, AthleteMembershipType.MENSALISTA, true, Instant.parse("2026-08-01T10:00:00Z")),
        )

        assertEquals(
            listOf(earlierId, laterId),
            AutoConfirmationPolicy.decide(candidates, capacity = 2, confirmedCount = 0).map { it.memberId },
        )
    }

    private fun candidate(
        id: String,
        type: AthleteMembershipType = AthleteMembershipType.MENSALISTA,
        enabled: Boolean,
        joinedAt: String = "2026-08-01T10:00:00Z",
    ) = AutoConfirmationCandidate(UUID.fromString(id), type, enabled, Instant.parse(joinedAt))
}
