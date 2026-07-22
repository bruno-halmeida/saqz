package br.com.saqz.groups.domain.attendance

import br.com.saqz.domain.DataError
import br.com.saqz.domain.ValidationDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AttendanceTest {
    @Test
    fun `attendance statuses are complete`() = assertEquals(3, AttendanceStatus.entries.size)

    @Test
    fun `confirmed status is represented`() =
        assertEquals(AttendanceStatus.Confirmed, AttendanceStatus.entries[0])

    @Test
    fun `declined status is represented`() =
        assertEquals(AttendanceStatus.Declined, AttendanceStatus.entries[1])

    @Test
    fun `waitlisted status is represented`() =
        assertEquals(AttendanceStatus.Waitlisted, AttendanceStatus.entries[2])

    @Test
    fun `attendance intents are complete`() = assertEquals(2, AttendanceIntent.entries.size)

    @Test
    fun `confirm intent is represented`() =
        assertEquals(AttendanceIntent.Confirm, AttendanceIntent.entries[0])

    @Test
    fun `decline intent is represented`() =
        assertEquals(AttendanceIntent.Decline, AttendanceIntent.entries[1])

    @Test
    fun `entry retains waitlist position`() = assertEquals(4, entry().waitlistPosition)

    @Test
    fun `entry accepts absent waitlist position`() =
        assertNull(entry().copy(waitlistPosition = null).waitlistPosition)

    @Test
    fun `entry retains member version`() = assertEquals(7, entry().version)

    @Test
    fun `detail accepts absent own attendance`() = assertNull(detail().ownAttendance)

    @Test
    fun `detail retains authoritative counts`() = assertEquals(
        listOf(3, 21, 2, 24),
        listOf(detail().confirmedCount, detail().availableSpots, detail().waitlistCount, detail().capacity),
    )

    @Test
    fun `audit accepts absent old status`() = assertNull(audit().oldStatus)

    @Test
    fun `audit accepts absent reason`() = assertNull(audit().reason)

    @Test
    fun `audit retains transition and actor`() = assertEquals(
        listOf("organizer-1", AttendanceStatus.Confirmed),
        listOf(audit().actorId, audit().newStatus),
    )

    @Test
    fun `mutation retains promoted count`() = assertEquals(2, mutation().promotedCount)

    @Test
    fun `mutation accepts absent audit`() = assertNull(mutation().copy(audit = null).audit)

    @Test
    fun `version token preserves quoted etag`() =
        assertEquals("\"9\"", AttendanceVersionToken("\"9\"").value)

    @Test
    fun `versioned mutation retains exact token`() = assertEquals(
        AttendanceVersionToken("\"9\""),
        VersionedAttendanceMutation(mutation(), AttendanceVersionToken("\"9\"")).version,
    )

    @Test
    fun `capacity retains aggregate and model versions`() {
        assertEquals(30, capacity().capacity)
        assertEquals(8L, capacity().version)
    }

    @Test
    fun `capacity retains promotions and detail`() = assertEquals(
        listOf(2, 24),
        listOf(capacity().promotedCount, capacity().detail.capacity),
    )

    @Test
    fun `versioned capacity retains exact token`() = assertEquals(
        "\"10\"",
        VersionedAttendanceCapacity(capacity(), AttendanceVersionToken("\"10\"")).version.value,
    )

    @Test
    fun `self command retains request id and intent`() = assertEquals(
        SelfAttendanceCommand("request-1", AttendanceIntent.Confirm),
        SelfAttendanceCommand("request-1", AttendanceIntent.Confirm),
    )

    @Test
    fun `override command retains target reason and intent`() {
        val command = OverrideAttendanceCommand(
            requestId = "request-2",
            memberId = "member-2",
            intent = AttendanceIntent.Decline,
            reason = "Correção manual",
        )

        assertEquals(
            listOf("request-2", "member-2", AttendanceIntent.Decline, "Correção manual"),
            listOf(command.requestId, command.memberId, command.intent, command.reason),
        )
    }

    @Test
    fun `capacity command retains request id and value`() = assertEquals(
        AttendanceCapacityCommand("request-3", 30),
        AttendanceCapacityCommand("request-3", 30),
    )

    @Test
    fun `validation error retains safe details`() {
        val details = ValidationDetails(
            globalMessages = listOf("Capacidade inválida"),
            fieldMessages = mapOf("capacity" to listOf("deve ser maior")),
        )
        val error = AttendanceError.Validation(DataError.Validation(details))

        assertEquals(details, assertIs<AttendanceError.Validation>(error).error.details)
    }

    @Test
    fun `feature errors are distinct`() = assertEquals(
        6,
        setOf(
            AttendanceError.HiddenResource,
            AttendanceError.DeadlinePassed,
            AttendanceError.Frozen,
            AttendanceError.Conflict,
            AttendanceError.Authentication,
            AttendanceError.Data(DataError.Unknown),
        ).size,
    )

    @Test
    fun `shared data error is retained`() = assertEquals(
        DataError.Connectivity,
        assertIs<AttendanceError.Data>(AttendanceError.Data(DataError.Connectivity)).error,
    )

    private fun entry() = AttendanceEntry(
        memberId = "member-1",
        status = AttendanceStatus.Waitlisted,
        waitlistPosition = 4,
        version = 7,
    )

    private fun detail() = AttendanceDetail(
        confirmedCount = 3,
        availableSpots = 21,
        waitlistCount = 2,
        capacity = 24,
    )

    private fun audit() = AttendanceAudit(
        actorId = "organizer-1",
        source = "ORGANIZER_OVERRIDE",
        newStatus = AttendanceStatus.Confirmed,
        occurredAt = "2026-08-12T22:30:00Z",
    )

    private fun mutation() = AttendanceMutation(
        attendance = entry(),
        audit = audit(),
        promotedCount = 2,
        detail = detail(),
    )

    private fun capacity() = AttendanceCapacity(
        capacity = 30,
        version = 8,
        promotedCount = 2,
        detail = detail(),
    )
}
