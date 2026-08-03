package br.com.saqz.groups.presentation.details

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupGameConfig
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleAttendanceDetail
import br.com.saqz.groups.presentation.sampleAttendanceRoster
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation
import br.com.saqz.groups.presentation.sampleVersionedGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val GROUP_ID = "group-1"

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `success loads the group header and profile details`() = runTest {
        val viewModel = viewModel()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Vôlei do CERET", viewModel.state.value.header?.name)
        assertEquals("Misto · Intermediário", viewModel.state.value.header?.subtitle)
        assertEquals("CERET", viewModel.state.value.venue?.name)
        assertTrue(viewModel.state.value.header?.summaryChips?.isNotEmpty() == true)
        assertFalse(viewModel.state.value.isOwner)
    }

    @Test
    fun `empty profile still renders a usable header`() = runTest {
        val empty = sampleVersionedGroup(sampleGroup(profile = null))
        val viewModel = viewModel(
            groupGateway = FakeGroupGateway(readResult = SaqzResult.Success(empty)),
        )

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Vôlei do CERET", viewModel.state.value.header?.name)
        assertTrue(viewModel.state.value.header?.summaryChips.isNullOrEmpty())
        assertEquals(null, viewModel.state.value.venue)
    }

    @Test
    fun `detail read usa o snapshot uma unica vez e preserva owner`() = runTest {
        val gateway = FakeGroupGateway(
            readResult = SaqzResult.Success(
                sampleVersionedGroup(
                    sampleGroup(role = br.com.saqz.groups.domain.group.GroupRole.OWNER),
                ),
            ),
        )
        val viewModel = viewModel(groupGateway = gateway)

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.isOwner)
        assertEquals(1, gateway.readCalls)
    }

    @Test
    fun `gateway failure is visible and typed`() = runTest {
        val viewModel = viewModel(
            groupGateway = FakeGroupGateway(
                readResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Forbidden)),
            ),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }

    @Test
    fun `navigation effects remain available`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupDetailsIntent.ManageMembers)
        assertEquals(GroupDetailsEffect.OpenMembers(GROUP_ID), viewModel.effects.first())

        viewModel.onIntent(GroupDetailsIntent.OpenSchedule)
        assertEquals(GroupDetailsEffect.OpenSchedule(GROUP_ID), viewModel.effects.first())
    }

    @Test
    fun `next game loads response card and eligible auto confirmation`() = runTest {
        val group = sampleGroup(role = GroupRole.ATHLETE).copy(
            gameConfig = GroupGameConfig(autoConfirmEnabled = true),
        )
        val vm = viewModel(
            groupGateway = FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup(group))),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = FakeAttendanceGateway(
                detailResult = SaqzResult.Success(
                    sampleAttendanceDetail().copy(
                        ownAttendance = AttendanceEntry("me", AttendanceStatus.Confirmed, version = 1),
                        autoConfirmEnabled = true,
                    ),
                ),
            ),
            athleteGateway = monthlyAthleteGateway(),
        )

        assertEquals("game-1", vm.state.value.nextGame?.gameId)
        assertEquals(GroupDetailsResponseStatus.Confirmed, vm.state.value.memberResponse?.status)
        assertTrue(vm.state.value.autoConfirmationVisible, "switch should be visible")
        assertTrue(vm.state.value.autoConfirmationEnabled, "persisted switch value should load")
        assertEquals(8, vm.state.value.attendance?.going)
        assertEquals(4, vm.state.value.nextGame?.availableSpots)
    }

    @Test
    fun `response reconciles group counters and next game vacancies`() = runTest {
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(sampleAttendanceDetail()),
            respondResult = SaqzResult.Success(
                sampleVersionedAttendanceMutation().copy(
                    value = sampleVersionedAttendanceMutation().value.copy(
                        attendance = AttendanceEntry("me", AttendanceStatus.Confirmed, version = 2),
                        detail = sampleAttendanceDetail().copy(
                            confirmedCount = 9,
                            availableSpots = 3,
                            waitlistCount = 1,
                            declinedCount = 3,
                            pendingCount = 0,
                        ),
                    ),
                ),
            ),
        )
        attendance.rosterResults = mutableListOf(
            SaqzResult.Success(sampleAttendanceRoster()),
            SaqzResult.Success(AttendanceRoster(
                confirmed = listOf(AttendanceRosterMember("promoted", "Promovido")),
                waitlisted = listOf(AttendanceRosterMember("wait-2", "Duda", 1)),
            )),
        )
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )

        vm.onIntent(GroupDetailsIntent.Respond(br.com.saqz.groups.domain.attendance.AttendanceIntent.Confirm))

        assertEquals(9, vm.state.value.attendance?.going)
        assertEquals(3, vm.state.value.attendance?.notGoing)
        assertEquals(0, vm.state.value.attendance?.pending)
        assertEquals(3, vm.state.value.attendance?.availableSpots)
        assertEquals(3, vm.state.value.nextGame?.availableSpots)
        assertEquals(listOf("Promovido"), vm.state.value.nextGame?.confirmedNames)
    }

    @Test
    fun `decline reconciles fifo promotion in group card`() = runTest {
        val promotedRoster = AttendanceRoster(
            confirmed = listOf(AttendanceRosterMember("wait-1", "Caio")),
            waitlisted = listOf(AttendanceRosterMember("wait-2", "Duda", 1)),
        )
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(
                sampleAttendanceDetail().copy(
                    ownAttendance = AttendanceEntry("me", AttendanceStatus.Confirmed, version = 1),
                    confirmedCount = 9,
                    availableSpots = 3,
                    waitlistCount = 2,
                    declinedCount = 2,
                    pendingCount = 0,
                ),
            ),
            respondResult = SaqzResult.Success(
                sampleVersionedAttendanceMutation().copy(
                    value = sampleVersionedAttendanceMutation().value.copy(
                        attendance = AttendanceEntry("me", AttendanceStatus.Declined, version = 2),
                        detail = sampleAttendanceDetail().copy(
                            confirmedCount = 9,
                            availableSpots = 3,
                            waitlistCount = 1,
                            declinedCount = 3,
                            pendingCount = 0,
                        ),
                    ),
                ),
            ),
        )
        attendance.rosterResults = mutableListOf(
            SaqzResult.Success(sampleAttendanceRoster()),
            SaqzResult.Success(promotedRoster),
        )
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )

        vm.onIntent(GroupDetailsIntent.Respond(br.com.saqz.groups.domain.attendance.AttendanceIntent.Decline))

        assertEquals(GroupDetailsResponseStatus.Declined, vm.state.value.memberResponse?.status)
        assertEquals(9, vm.state.value.attendance?.going)
        assertEquals(3, vm.state.value.attendance?.notGoing)
        assertEquals(0, vm.state.value.attendance?.pending)
        assertEquals(3, vm.state.value.nextGame?.availableSpots)
        assertEquals(listOf("Caio"), vm.state.value.nextGame?.confirmedNames)
        assertEquals(2, attendance.rosterCalls)
    }

    @Test
    fun `response failure rolls back optimistic selection`() = runTest {
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(
                sampleAttendanceDetail().copy(
                    ownAttendance = AttendanceEntry("me", AttendanceStatus.Confirmed, version = 1),
                ),
            ),
            respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
        )
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )

        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Decline))

        assertEquals(GroupDetailsResponseStatus.Confirmed, vm.state.value.memberResponse?.status)
        assertFalse(vm.state.value.responding)
        assertTrue(vm.state.value.responseFailed)
    }

    @Test
    fun `auto confirmation failure rolls back optimistic switch`() = runTest {
        val group = sampleGroup(role = GroupRole.ATHLETE).copy(
            gameConfig = GroupGameConfig(autoConfirmEnabled = true),
        )
        val attendance = FakeAttendanceGateway(
            autoConfirmationResult = SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
        )
        val vm = viewModel(
            groupGateway = FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup(group))),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )

        vm.onIntent(GroupDetailsIntent.ToggleAutoConfirmation(true))

        assertFalse(vm.state.value.autoConfirmationEnabled)
        assertTrue(vm.state.value.autoConfirmationFailed)
    }

    @Test
    fun `roster failure after response keeps authoritative counts and offers retry`() = runTest {
        val attendance = FakeAttendanceGateway(
            respondResult = SaqzResult.Success(sampleVersionedAttendanceMutation()),
        )
        attendance.rosterResults = mutableListOf(
            SaqzResult.Success(sampleAttendanceRoster()),
            SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
            SaqzResult.Success(AttendanceRoster(confirmed = listOf(AttendanceRosterMember("promoted", "Promovido")), waitlisted = emptyList())),
        )
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )

        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(9, vm.state.value.attendance?.going)
        assertTrue(vm.state.value.rosterStale)
        assertFalse(vm.state.value.responseFailed)

        vm.onIntent(GroupDetailsIntent.RetryRoster)

        assertFalse(vm.state.value.rosterStale)
        assertEquals(listOf("Promovido"), vm.state.value.nextGame?.confirmedNames)
        assertEquals(3, attendance.rosterCalls)
    }

    @Test
    fun `own profile failure blocks response until retry loads membership`() = runTest {
        val athlete = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Connectivity)),
        )
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            athleteGateway = athlete,
        )

        assertTrue(vm.state.value.loadFailed)
        assertEquals(GroupUiError.Network, vm.state.value.error)
        assertEquals(null, vm.state.value.memberResponse)

        athlete.ownProfileResult = monthlyAthleteGateway().ownProfileResult
        vm.onIntent(GroupDetailsIntent.Retry)

        assertFalse(vm.state.value.loadFailed)
        assertEquals(AthleteMembershipType.MENSALISTA, vm.state.value.membershipType)
    }

    private fun viewModel(
        groupGateway: FakeGroupGateway = FakeGroupGateway(),
        gameGateway: FakeGameGateway = FakeGameGateway(),
        attendanceGateway: FakeAttendanceGateway = FakeAttendanceGateway(),
        athleteGateway: FakeAthleteGateway = FakeAthleteGateway(),
    ) = GroupDetailsViewModel(GROUP_ID, groupGateway, gameGateway, attendanceGateway, athleteGateway)

    private fun athleteGroupGateway() = FakeGroupGateway(
        readResult = SaqzResult.Success(
            sampleVersionedGroup(sampleGroup(role = GroupRole.ATHLETE)),
        ),
    )

    private fun monthlyAthleteGateway() = FakeAthleteGateway(
        ownProfileResult = SaqzResult.Success(
            OwnAthleteProfile(
                userId = "me",
                displayName = "Member",
                phone = null,
                memberships = listOf(
                    OwnAthleteMembership(
                        groupId = br.com.saqz.domain.GroupId(GROUP_ID),
                        groupName = "Vôlei do CERET",
                        role = GroupRole.ATHLETE,
                        position = null,
                        membershipType = AthleteMembershipType.MENSALISTA,
                        active = true,
                    ),
                ),
            ),
        ),
    )
}
