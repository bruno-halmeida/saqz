package br.com.saqz.composeapp.di

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialError
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import br.com.saqz.subscriptions.presentation.trial.TrialEntryFailure
import br.com.saqz.subscriptions.presentation.trial.TrialEntryIntent
import br.com.saqz.subscriptions.presentation.trial.TrialEntryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrialGroupCreationEntitlementTest {
    @Test
    fun `trial and paid owners can create only with an authoritative slot`() = runTest {
        for (status in listOf(TrialStatus.Available, TrialStatus.Active, TrialStatus.Subscribed)) {
            val gateway = FakeTrialGateway(SaqzResult.Success(access(status, true)))
            assertTrue(TrialGroupCreationEntitlement(gateway).canCreateGroup(), status.name)
            gateway.result = SaqzResult.Success(access(status, false))
            assertFalse(TrialGroupCreationEntitlement(gateway).canCreateGroup(), status.name)
        }
    }

    @Test
    fun `expired ineligible and failed lookups never authorize creation`() = runTest {
        for (status in listOf(TrialStatus.Expired, TrialStatus.Ineligible)) {
            assertFalse(TrialGroupCreationEntitlement(FakeTrialGateway(SaqzResult.Success(access(status, true)))).canCreateGroup())
        }
        assertFalse(TrialGroupCreationEntitlement(FakeTrialGateway(SaqzResult.Failure(TrialError.NotFound))).canCreateGroup())
    }

    @Test
    fun couponEligibilityDoesNotAuthorizeGroupCreation() = runTest {
        val trial = access(TrialStatus.Ineligible, false).copy(canRedeemCoupon = true, offerMode = "COUPON_ONLY")
        assertFalse(TrialGroupCreationEntitlement(FakeTrialGateway(SaqzResult.Success(trial))).canCreateGroup())
    }

    @Test
    fun failedLookupReachesRetryEntryButDoesNotAuthorizeCreation() = runTest {
        val entitlement = TrialGroupCreationEntitlement(FakeTrialGateway(SaqzResult.Failure(TrialError.NotFound)))
        assertTrue(entitlement.canOpenCreationFlow())
        assertFalse(entitlement.canCreateGroup())
    }

    @Test
    fun creationEntryRespectsKnownDenialAndCouponEligibility() = runTest {
        val gateway = FakeTrialGateway(SaqzResult.Success(access(TrialStatus.Ineligible, false)))
        val entitlement = TrialGroupCreationEntitlement(gateway)
        assertFalse(entitlement.canOpenCreationFlow())
        gateway.result = SaqzResult.Success(access(TrialStatus.Ineligible, false).copy(canRedeemCoupon = true))
        assertTrue(entitlement.canOpenCreationFlow())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun initialFailureReachesGuardedEntryAndRetryHonorsCouponOnlyAndOff() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val gateway = FakeTrialGateway(SaqzResult.Failure(TrialError.NotFound))
            assertTrue(TrialGroupCreationEntitlement(gateway).canOpenCreationFlow())
            val entry = TrialEntryViewModel(gateway)
            assertEquals(TrialEntryFailure.Load, entry.state.value.failure)
            assertFalse(entry.state.value.ready)
            gateway.result = SaqzResult.Success(access(TrialStatus.Ineligible, false).copy(
                offerMode = "COUPON_ONLY", canRedeemCoupon = true,
            ))
            entry.onIntent(TrialEntryIntent.Refresh)
            assertTrue(entry.state.value.access?.canRedeemCoupon == true)
            assertFalse(entry.state.value.canContinue)
            assertFalse(entry.state.value.ready)
            gateway.result = SaqzResult.Success(access(TrialStatus.Ineligible, false).copy(offerMode = "OFF"))
            entry.onIntent(TrialEntryIntent.Refresh)
            assertEquals("OFF", entry.state.value.access?.offerMode)
            assertFalse(entry.state.value.canContinue)
            assertFalse(entry.state.value.ready)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun access(status: TrialStatus, canCreate: Boolean) = TrialAccess(
        status, null, null, "2026-09-12T12:00:00Z", false, canCreate, 1, 25, true, null,
    )

    private class FakeTrialGateway(var result: SaqzResult<TrialAccess, TrialError>) : TrialGateway {
        override suspend fun ownerTrial() = result
        override suspend fun groupTrial(groupId: GroupId) = result
    }
}
