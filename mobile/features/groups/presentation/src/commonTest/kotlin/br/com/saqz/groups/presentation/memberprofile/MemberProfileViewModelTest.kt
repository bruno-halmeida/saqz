package br.com.saqz.groups.presentation.memberprofile

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteStats
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.GroupUiError
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
        val vm = MemberProfileViewModel("group-1", "selected", gateway)
        assertFalse(vm.state.value.loading)
        assertEquals("Ana", vm.state.value.name)
        assertNull(vm.state.value.phone)
        assertEquals("8", vm.state.value.games)
        assertEquals("75%", vm.state.value.attendance)
        assertEquals("2", vm.state.value.absences)
        assertEquals("selected", gateway.lastStatsUserId)
    }

    @Test
    fun missingMemberDoesNotLoadAnotherProfileAndFailureCanRetry() = runTest {
        val gateway = FakeAthleteGateway()
        val vm = MemberProfileViewModel("group-1", "gone", gateway)
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
