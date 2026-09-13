package br.com.saqz.composeapp.di

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialError
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
    fun couponEligibleOrganizerCanReachCouponEntryWithoutGroupPermission() = runTest {
        val trial = access(TrialStatus.Ineligible, false).copy(canRedeemCoupon = true, offerMode = "COUPON_ONLY")
        assertTrue(TrialGroupCreationEntitlement(FakeTrialGateway(SaqzResult.Success(trial))).canCreateGroup())
    }

    private fun access(status: TrialStatus, canCreate: Boolean) = TrialAccess(
        status, null, null, "2026-09-12T12:00:00Z", false, canCreate, 1, 25, true, null,
    )

    private class FakeTrialGateway(var result: SaqzResult<TrialAccess, TrialError>) : TrialGateway {
        override suspend fun ownerTrial() = result
        override suspend fun groupTrial(groupId: GroupId) = result
    }
}
