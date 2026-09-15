package br.com.saqz.groups.presentation.attendancelink

import br.com.saqz.domain.*
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.attendance.share.*
import br.com.saqz.groups.domain.membership.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AttendanceLinkViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun close() { Dispatchers.resetMain() }

    @Test fun linkConfirmsTheAuthenticatedUserAndShowsWaitlistInsteadOfFalseConfirmation() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(code, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Confirmed, vm.state.value.phase)
        assertEquals(destination, vm.state.value.destination)
        assertEquals(listOf(destination), gateway.targets)
        assertEquals(AttendanceIntent.Confirm, gateway.commands.single().intent)
        gateway.status = AttendanceStatus.Waitlisted
        val waitlisted = AttendanceLinkViewModel(code, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Waitlisted, waitlisted.state.value.phase)
        assertEquals(gateway.commands.first().requestId, gateway.commands.last().requestId)
        assertEquals("00000000-0000-0000-0000-000000000000", gateway.commands.first().requestId)
    }

    @Test fun expiredLinkCannotSubmitAttendanceAndDeadlineFailureIsNotSuccess() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(code, SharingFake(expired = true), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Invalid, vm.state.value.phase)
        assertTrue(gateway.commands.isEmpty())
        gateway.error = AttendanceError.DeadlinePassed
        val closed = AttendanceLinkViewModel(code, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Invalid, closed.state.value.phase)
        assertNull(closed.state.value.destination)
    }

    @Test fun retryPreservesRequestIdentityAndSuccessfulStateDoesNotResubmit() = runTest {
        val gateway = AttendanceFake().apply { error = AttendanceError.Data(DataError.Connectivity) }
        val vm = AttendanceLinkViewModel(code, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Failed, vm.state.value.phase)
        gateway.error = null
        vm.retry()
        assertEquals(AttendanceLinkPhase.Confirmed, vm.state.value.phase)
        assertEquals(1, gateway.commands.map { it.requestId }.distinct().size)
        vm.retry()
        assertEquals(2, gateway.commands.size)
    }

    @Test fun newcomerJoinsGroupAndRegistersBeforeAnyAttendanceWrite() = runTest {
        val gateway = AttendanceFake()
        val invite = InviteFake(InviteRedeemStatus.JOINED)
        val vm = AttendanceLinkViewModel(code, SharingFake(expired = true, firstOnly = true), gateway, invite)
        assertEquals(listOf(InviteCode(code)), invite.codes)
        assertEquals(AttendanceLinkPhase.Registration, vm.state.value.phase)
        assertEquals(AttendanceLinkEffect.Register("group"), vm.effects.first())
        assertTrue(gateway.commands.isEmpty())
        val incomplete = AttendanceLinkViewModel(code, SharingFake(registration = true), gateway, InviteFake())
        assertEquals(AttendanceLinkEffect.Register("group"), incomplete.effects.first())
        assertTrue(gateway.commands.isEmpty())
    }

    @Test fun approvalRequiredDoesNotSkipMembershipAndRegistrationGates() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(code, SharingFake(expired = true), gateway, InviteFake(InviteRedeemStatus.PENDING))
        assertEquals(AttendanceLinkPhase.Pending, vm.state.value.phase)
        assertTrue(gateway.commands.isEmpty())
    }

    private class InviteFake(private val status: InviteRedeemStatus? = null) : InviteGateway {
        val codes = mutableListOf<InviteCode>()
        override suspend fun preview(code: InviteCode) = error("unused")
        override suspend fun redeem(code: InviteCode): SaqzResult<InviteRedeem, InviteError> {
            codes += code
            return if (status == null) SaqzResult.Failure(InviteError.InvalidOrExpired)
            else SaqzResult.Success(InviteRedeem(status, GroupId("group"), "ATHLETE"))
        }
    }

    private class SharingFake(
        private val expired: Boolean = false, private val registration: Boolean = false, private val firstOnly: Boolean = false,
    ) : AttendanceSharingGateway {
        private var resolutions = 0
        override suspend fun resolveLink(code: AttendanceLinkCode): SaqzResult<AttendanceLinkDestination, AttendanceSharingError> {
            resolutions++
            return if (expired && (!firstOnly || resolutions == 1)) SaqzResult.Failure(AttendanceSharingError.InvalidOrExpired)
            else SaqzResult.Success(destination.copy(registrationRequired = registration))
        }
        override suspend fun rotateLink(groupId: GroupId, gameId: String) = error("unused")
        override suspend fun readSnapshot(groupId: GroupId, gameId: String) = error("unused")
    }
    private class AttendanceFake : AttendanceGateway {
        val commands = mutableListOf<SelfAttendanceCommand>()
        val targets = mutableListOf<AttendanceLinkDestination>()
        var status = AttendanceStatus.Confirmed
        var error: AttendanceError? = null
        override suspend fun respond(groupId: GroupId, gameId: String, command: SelfAttendanceCommand):
            SaqzResult<VersionedAttendanceMutation, AttendanceError> {
            commands += command
            targets += AttendanceLinkDestination(groupId, gameId)
            error?.let { return SaqzResult.Failure(it) }
            return SaqzResult.Success(VersionedAttendanceMutation(
                AttendanceMutation(AttendanceEntry("logged-user", status, version = 1), promotedCount = 0,
                    detail = AttendanceDetail(confirmedCount = 1, availableSpots = 0, waitlistCount = 0, capacity = 1)),
                AttendanceVersionToken("1")))
        }
        override suspend fun read(groupId: GroupId, gameId: String) = error("unused")
        override suspend fun roster(groupId: GroupId, gameId: String) = error("unused")
        override suspend fun promote(groupId: GroupId, gameId: String, command: AttendancePromotionCommand) = error("unused")
        override suspend fun override(groupId: GroupId, gameId: String, command: OverrideAttendanceCommand) = error("unused")
        override suspend fun capacity(groupId: GroupId, gameId: String, version: AttendanceVersionToken, command: AttendanceCapacityCommand) =
            error("unused")
        override suspend fun updateAutoConfirmation(groupId: GroupId, command: AutoConfirmationCommand) = error("unused")
    }
    private companion object {
        val code = "A".repeat(43)
        val destination = AttendanceLinkDestination(GroupId("group"), "game")
    }
}
