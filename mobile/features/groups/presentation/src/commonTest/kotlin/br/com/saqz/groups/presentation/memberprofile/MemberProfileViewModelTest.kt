package br.com.saqz.groups.presentation.memberprofile

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteStats
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeModerationGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.blockedPerson
import br.com.saqz.groups.presentation.fakeBlocks
import br.com.saqz.groups.presentation.moderation.BlockedPeopleRepository
import br.com.saqz.groups.presentation.sampleRosterEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MemberProfileViewModelTest {
    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun cleanup() = Dispatchers.resetMain()

    @Test
    fun selectedMemberAndStatsAreLoadedWithoutInventingPrivatePhone() = runTest {
        val gateway = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("other"), sampleRosterEntry("selected").copy(displayName = "Ana"))),
            statsResult = SaqzResult.Success(AthleteStats(8, 75, 2)),
        )
        val vm = MemberProfileViewModel("group-1", "selected", gateway, fakeBlocks())
        assertFalse(vm.state.value.loading)
        assertEquals("Ana", vm.state.value.name)
        assertNull(vm.state.value.phone)
        assertEquals("8", vm.state.value.games)
        assertEquals("75%", vm.state.value.attendance)
        assertEquals("2", vm.state.value.absences)
        assertEquals("selected", gateway.lastStatsUserId)
    }

    @Test
    fun restrictedStatsDoNotHideTheAuthorizedProfileAndOtherFailuresCanRetry() = runTest {
        val gateway = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("selected").copy(displayName = "Ana"))),
            statsResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Forbidden)),
        )
        val vm = MemberProfileViewModel("group-1", "selected", gateway, fakeBlocks())
        assertEquals("Ana", vm.state.value.name)
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.games)
        assertFalse(vm.state.value.statsFailed)
        gateway.statsResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Connectivity))
        vm.onIntent(MemberProfileIntent.Retry)
        assertEquals("Ana", vm.state.value.name)
        assertNull(vm.state.value.error)
        assertEquals(true, vm.state.value.statsFailed)
        gateway.statsResult = SaqzResult.Success(AthleteStats(8, 75, 2))
        vm.onIntent(MemberProfileIntent.Retry)
        assertEquals("8", vm.state.value.games)
        assertFalse(vm.state.value.statsFailed)
    }

    @Test
    fun otherMemberOffersReportAndBlockFollowingTheBlockedList() = runTest {
        val gateway = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("selected").copy(displayName = "Ana"))),
        )
        val moderation = FakeModerationGateway(blockedResult = SaqzResult.Success(listOf(blockedPerson("selected"))))
        val blocks = BlockedPeopleRepository(moderation)
        val vm = MemberProfileViewModel("group-1", "selected", gateway, blocks)

        assertTrue(vm.state.value.moderationVisible)
        assertTrue(vm.state.value.blocked)
        blocks.unblock("selected")
        assertFalse(vm.state.value.blocked)
        assertEquals(listOf("selected"), moderation.unblocks)
    }

    @Test
    fun ownProfileHidesReportAndBlock() = runTest {
        val gateway = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("me").copy(displayName = "Bruno"))),
        )
        val vm = MemberProfileViewModel("group-1", "me", gateway, fakeBlocks())

        assertEquals("Bruno", vm.state.value.name)
        assertTrue(vm.state.value.isSelf)
        assertFalse(vm.state.value.moderationVisible)
    }

    @Test
    fun missingMemberDoesNotLoadAnotherProfileAndFailureCanRetry() = runTest {
        val gateway = FakeAthleteGateway()
        val vm = MemberProfileViewModel("group-1", "gone", gateway, fakeBlocks())
        assertEquals(GroupUiError.NotFound, vm.state.value.error)
        assertNull(gateway.lastStatsUserId)
        gateway.rosterResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Forbidden))
        vm.onIntent(MemberProfileIntent.Retry)
        assertEquals(GroupUiError.AccessDenied, vm.state.value.error)
        gateway.rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("gone").copy(phone = "+5511999999999")))
        vm.onIntent(MemberProfileIntent.Retry)
        assertNull(vm.state.value.error)
        assertEquals("+5511999999999", vm.state.value.phone)
        assertNull(vm.state.value.attendance)
    }
}
