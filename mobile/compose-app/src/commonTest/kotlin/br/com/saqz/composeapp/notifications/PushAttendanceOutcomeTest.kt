package br.com.saqz.composeapp.notifications

import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceMutation
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import kotlin.test.Test
import kotlin.test.assertEquals

class PushAttendanceOutcomeTest {
    private fun answered(status: AttendanceStatus) = SaqzResult.Success(
        VersionedAttendanceMutation(
            AttendanceMutation(
                attendance = AttendanceEntry("me", status, version = 1),
                promotedCount = 0,
                detail = AttendanceDetail(confirmedCount = 0, availableSpots = 0, waitlistCount = 0, capacity = 0),
            ),
            AttendanceVersionToken("v1"),
        ),
    )

    @Test
    fun statusBecomesOutcomeAndClosedGamesCollapseToClosed() {
        assertEquals(PushAttendanceOutcome.Confirmed, answered(AttendanceStatus.Confirmed).toOutcome())
        assertEquals(PushAttendanceOutcome.Waitlisted, answered(AttendanceStatus.Waitlisted).toOutcome())
        assertEquals(PushAttendanceOutcome.Declined, answered(AttendanceStatus.Declined).toOutcome())
        assertEquals(PushAttendanceOutcome.Closed, SaqzResult.Failure(AttendanceError.DeadlinePassed).toOutcome())
        assertEquals(PushAttendanceOutcome.Closed, SaqzResult.Failure(AttendanceError.Frozen).toOutcome())
        assertEquals(PushAttendanceOutcome.Failed, SaqzResult.Failure(AttendanceError.Authentication).toOutcome())
    }
}
