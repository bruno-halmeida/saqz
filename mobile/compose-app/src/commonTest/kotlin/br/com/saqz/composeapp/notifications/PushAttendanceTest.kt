package br.com.saqz.composeapp.notifications

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.AttendanceCapacityCommand
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceMutation
import br.com.saqz.groups.domain.attendance.AttendancePromotionCommand
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken
import br.com.saqz.groups.domain.attendance.AutoConfirmationCommand
import br.com.saqz.groups.domain.attendance.OverrideAttendanceCommand
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.TokenResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalEncodingApi::class)
class PushAttendanceTest {
    private val loggedIn = object : IdTokenProvider {
        override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
            completion(TokenResult.Available("h." + Base64.UrlSafe.encode("""{"sub":"user-a"}""".encodeToByteArray()).trimEnd('=') + ".s"))
    }
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

    @Test
    fun declineReachesTheGatewayWithAFreshRequestIdAndTheOutcomeComesBack() = runTest {
        val gateway = RespondOnly { answered(AttendanceStatus.Declined) }
        val outcomes = mutableListOf<PushAttendanceOutcome>()
        PushAttendance(gateway, loggedIn, this).respond("g1", "game1", "user-a", confirm = false) { outcomes += it }
        advanceUntilIdle()
        assertEquals(listOf(PushAttendanceOutcome.Declined), outcomes)
        assertEquals(listOf(GroupId("g1") to "game1"), gateway.calls.map { it.first })
        assertEquals(AttendanceIntent.Decline, gateway.calls.single().second.intent)
        assertEquals(36, gateway.calls.single().second.requestId.length)
    }

    @Test
    fun aRequestSlowerThanTheReceiverWindowIsReportedAsNoResponseInsteadOfHanging() = runTest {
        val gateway = RespondOnly { delay(60_000); answered(AttendanceStatus.Confirmed) }
        val outcomes = mutableListOf<PushAttendanceOutcome>()
        PushAttendance(gateway, loggedIn, this).respond("g1", "game1", "user-a", confirm = true) { outcomes += it }
        advanceUntilIdle()
        assertEquals(listOf(PushAttendanceOutcome.NoResponse), outcomes)
    }

    @Test
    fun aPushForAnotherAccountOnThisDeviceIsRefusedWithoutTouchingTheGateway() = runTest {
        val gateway = RespondOnly { answered(AttendanceStatus.Confirmed) }
        val outcomes = mutableListOf<PushAttendanceOutcome>()
        PushAttendance(gateway, loggedIn, this).respond("g1", "game1", "user-b", confirm = true) { outcomes += it }
        advanceUntilIdle()
        assertEquals(listOf(PushAttendanceOutcome.Failed), outcomes)
        assertEquals(emptyList(), gateway.calls)
    }

    private class RespondOnly(
        private val answer: suspend () -> SaqzResult<VersionedAttendanceMutation, AttendanceError>,
    ) : AttendanceGateway {
        val calls = mutableListOf<Pair<Pair<GroupId, String>, SelfAttendanceCommand>>()
        override suspend fun respond(groupId: GroupId, gameId: String, command: SelfAttendanceCommand) =
            answer().also { calls += (groupId to gameId) to command }
        override suspend fun read(groupId: GroupId, gameId: String) = unused()
        override suspend fun roster(groupId: GroupId, gameId: String) = unused()
        override suspend fun promote(groupId: GroupId, gameId: String, command: AttendancePromotionCommand) = unused()
        override suspend fun override(groupId: GroupId, gameId: String, command: OverrideAttendanceCommand) = unused()
        override suspend fun capacity(groupId: GroupId, gameId: String, version: AttendanceVersionToken, command: AttendanceCapacityCommand) =
            unused()
        override suspend fun updateAutoConfirmation(groupId: GroupId, command: AutoConfirmationCommand) = unused()
        private fun unused(): Nothing = error("not part of the push path")
    }
}
