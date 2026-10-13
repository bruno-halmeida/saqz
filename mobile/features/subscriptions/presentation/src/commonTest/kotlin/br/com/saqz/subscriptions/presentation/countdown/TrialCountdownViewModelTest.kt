package br.com.saqz.subscriptions.presentation.countdown

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialError
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import br.com.saqz.subscriptions.domain.trial.TrialUsage
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrialCountdownViewModelTest {
    private val usage = TrialUsage(games = 3, confirmedAthletes = 12, cashCents = 45_000)
    private val gateway = FakeGateway()

    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun opensInTheLastThreeDaysWithTheUsageAndTellsTheServer() = runTest {
        for (days in 0..3) {
            val fake = FakeGateway().apply { access = active(days) }
            val vm = TrialCountdownViewModel(fake)

            vm.onIntent(TrialCountdownIntent.Check)

            assertTrue(vm.state.value.visible, "faltando $days dias")
            assertEquals(days, vm.state.value.daysLeft)
            assertEquals(usage, vm.state.value.usage)
            assertEquals(1, fake.seen)
        }
    }

    @Test
    fun staysClosedBeforeTheLastThreeDays() = runTest {
        gateway.access = active(4)
        val vm = TrialCountdownViewModel(gateway)

        vm.onIntent(TrialCountdownIntent.Check)

        assertFalse(vm.state.value.visible)
        assertEquals(0, gateway.seen)
    }

    @Test
    fun staysClosedWhenTheServerSaysItAlreadyOpenedToday() = runTest {
        gateway.access = active(2).copy(countdownSeenToday = true)
        val vm = TrialCountdownViewModel(gateway)

        vm.onIntent(TrialCountdownIntent.Check)

        assertFalse(vm.state.value.visible)
        assertEquals(0, gateway.seen)
    }

    @Test
    fun onlyAnActiveTrialOfTheOwnerOpensIt() = runTest {
        val vm = TrialCountdownViewModel(gateway)
        for (access in listOf(
            active(2).copy(status = TrialStatus.Expired),
            active(2).copy(status = TrialStatus.Subscribed),
            active(2).copy(isOwner = false),
            active(2).copy(daysLeft = null),
        )) {
            gateway.access = access
            vm.onIntent(TrialCountdownIntent.Check)
            assertFalse(vm.state.value.visible)
        }
        assertEquals(0, gateway.seen)
    }

    @Test
    fun aFailedLookupNeverOpensIt() = runTest {
        gateway.offline = true
        val vm = TrialCountdownViewModel(gateway)

        vm.onIntent(TrialCountdownIntent.Check)

        assertFalse(vm.state.value.visible)
    }

    @Test
    fun notNowClosesAndTheSameDayDoesNotOpenItAgainEvenIfTheServerCallFailed() = runTest {
        gateway.access = active(2)
        gateway.seenFails = true
        val vm = TrialCountdownViewModel(gateway)
        vm.onIntent(TrialCountdownIntent.Check)

        vm.onIntent(TrialCountdownIntent.Dismiss)
        vm.onIntent(TrialCountdownIntent.Check)

        assertFalse(vm.state.value.visible)
        assertEquals(1, gateway.seen)
    }

    @Test
    fun theNextDayOpensItAgain() = runTest {
        gateway.access = active(3)
        val vm = TrialCountdownViewModel(gateway)
        vm.onIntent(TrialCountdownIntent.Check)
        vm.onIntent(TrialCountdownIntent.Dismiss)

        gateway.access = active(2)
        vm.onIntent(TrialCountdownIntent.Check)

        assertTrue(vm.state.value.visible)
        assertEquals(2, vm.state.value.daysLeft)
        assertEquals(2, gateway.seen)
    }

    private fun active(daysLeft: Int) = TrialAccess(
        status = TrialStatus.Active, startedAt = "2026-10-01T15:00:00Z", endsAt = "2026-10-15T15:00:00Z",
        serverTime = "2026-10-12T13:00:00Z", readOnly = false, canCreateGroup = false, maxGroups = 3, maxAthletes = null,
        isOwner = true, appUrl = null, daysLeft = daysLeft, usage = usage,
    )

    private class FakeGateway : TrialGateway {
        var access: TrialAccess? = null
        var offline = false
        var seenFails = false
        var seen = 0

        override suspend fun ownerTrial(): SaqzResult<TrialAccess, TrialError> =
            if (offline) SaqzResult.Failure(TrialError.Data(DataError.Connectivity)) else SaqzResult.Success(checkNotNull(access))

        override suspend fun groupTrial(groupId: GroupId) = ownerTrial()

        override suspend fun markCountdownSeen(): SaqzResult<Unit, TrialError> {
            seen++
            return if (seenFails) SaqzResult.Failure(TrialError.Data(DataError.Connectivity)) else SaqzResult.Success(Unit)
        }
    }
}
