package br.com.saqz.groups.domain.attendance

import br.com.saqz.groups.domain.AthleteMembershipType
import java.time.Instant
import java.util.UUID

data class AutoConfirmationCandidate(
    val memberId: UUID,
    val membershipType: AthleteMembershipType,
    val enabled: Boolean,
    val joinedAt: Instant,
)

data class AutoConfirmationAssignment(
    val memberId: UUID,
    val status: AttendanceStatus,
)

object AutoConfirmationPolicy {
    fun decide(
        candidates: List<AutoConfirmationCandidate>,
        capacity: Int,
        confirmedCount: Int,
    ): List<AutoConfirmationAssignment> {
        require(capacity >= 0)
        require(confirmedCount >= 0)

        var availableSpots = (capacity - confirmedCount).coerceAtLeast(0)
        return candidates
            .asSequence()
            .filter { it.enabled && it.membershipType == AthleteMembershipType.MENSALISTA }
            // Group entry date is stable; UUID breaks ties deterministically.
            .sortedWith(compareBy<AutoConfirmationCandidate> { it.joinedAt }.thenBy { it.memberId.toString() })
            .map { candidate ->
                val status = if (availableSpots > 0) {
                    availableSpots--
                    AttendanceStatus.CONFIRMED
                } else {
                    AttendanceStatus.WAITLISTED
                }
                AutoConfirmationAssignment(candidate.memberId, status)
            }
            .toList()
    }
}
