package br.com.saqz.subscriptions.presentation.trial

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.trial.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TrialEntryViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }
    @Test fun publicTrialRequiresContinueAndRechecksAvailability() = runTest {
        val gateway = Fake(); val vm = TrialEntryViewModel(gateway)
        assertEquals(14, vm.state.value.access?.trialDays)
        assertFalse(vm.state.value.ready)
        gateway.access = access(false, false, "OFF")
        vm.onIntent(TrialEntryIntent.Continue)
        assertFalse(vm.state.value.ready)
        assertEquals("OFF", vm.state.value.access?.offerMode)
    }
    @Test fun couponOnlyAppliesCustomDaysBeforeOpeningGroupCreation() = runTest {
        val gateway = Fake().apply { access = access(false, true, "COUPON_ONLY") }
        val vm = TrialEntryViewModel(gateway)
        vm.onIntent(TrialEntryIntent.Continue)
        assertFalse(vm.state.value.ready)
        vm.onIntent(TrialEntryIntent.EditCode(" arena "))
        gateway.applied = SaqzResult.Success(access(true, true, "COUPON_ONLY").copy(trialDays=45, selectedCouponCode="ARENA"))
        vm.onIntent(TrialEntryIntent.Apply)
        assertEquals("ARENA", gateway.code)
        assertEquals(45, vm.state.value.access?.trialDays)
        assertEquals("ARENA", vm.state.value.access?.selectedCouponCode)
        assertFalse(vm.state.value.ready)
        gateway.access = (gateway.applied as SaqzResult.Success).value
        vm.onIntent(TrialEntryIntent.Continue)
        assertTrue(vm.state.value.ready)
    }
    @Test fun offAndIneligibleCannotApplyAndExistingAccessContinues() = runTest {
        val gateway = Fake().apply { access=access(false,false,"OFF") }
        val vm=TrialEntryViewModel(gateway)
        vm.onIntent(TrialEntryIntent.EditCode("ARENA"));vm.onIntent(TrialEntryIntent.Apply)
        assertNull(gateway.code)
        assertFalse(vm.state.value.ready)
        for(status in listOf(TrialStatus.Active,TrialStatus.Subscribed)) {
            gateway.access=access(true,false,"OFF").copy(status=status)
            vm.onIntent(TrialEntryIntent.Refresh)
            assertTrue(vm.state.value.ready)
        }
    }
    @Test fun applyFailurePreservesCodeAndBlocksStaleContinuationUntilReload() = runTest {
        val gateway=Fake();val vm=TrialEntryViewModel(gateway)
        gateway.applied=SaqzResult.Failure(TrialError.CouponUnavailable)
        vm.onIntent(TrialEntryIntent.EditCode("ARENA"));vm.onIntent(TrialEntryIntent.Apply)
        assertEquals("ARENA", vm.state.value.code)
        assertEquals(TrialEntryFailure.Coupon,vm.state.value.failure)
        assertFalse(vm.state.value.ready)
        vm.onIntent(TrialEntryIntent.Continue)
        assertFalse(vm.state.value.ready)
        vm.onIntent(TrialEntryIntent.Refresh)
        assertNull(vm.state.value.failure)
    }
    @Test fun pendingApplyCannotBeDuplicatedOrEditedAndRefreshDiscardsOldResponse() = runTest {
        val gateway=Fake();val vm=TrialEntryViewModel(gateway)
        gateway.pending=CompletableDeferred()
        vm.onIntent(TrialEntryIntent.EditCode("ARENA"));vm.onIntent(TrialEntryIntent.Apply)
        assertTrue(vm.state.value.loading)
        vm.onIntent(TrialEntryIntent.EditCode("OTHER"));vm.onIntent(TrialEntryIntent.Apply)
        assertEquals(1,gateway.applies)
        assertEquals("ARENA",vm.state.value.code)
        gateway.access=access(false,false,"OFF")
        vm.onIntent(TrialEntryIntent.Refresh)
        gateway.pending?.complete(SaqzResult.Success(access(true,true,"ON")))
        assertEquals("OFF",vm.state.value.access?.offerMode)
        assertFalse(vm.state.value.ready)
    }
    @Test fun failedLookupCanBeRetriedWithoutReleasingGroupCreation() = runTest {
        val gateway=Fake().apply { offline=true };val vm=TrialEntryViewModel(gateway)
        assertEquals(TrialEntryFailure.Load,vm.state.value.failure)
        assertFalse(vm.state.value.ready)
        gateway.offline=false;vm.onIntent(TrialEntryIntent.Refresh)
        assertNull(vm.state.value.failure)
        vm.onIntent(TrialEntryIntent.Continue)
        assertTrue(vm.state.value.ready)
    }
    private fun access(create:Boolean, redeem:Boolean, mode:String)=TrialAccess(
        if(create) TrialStatus.Available else TrialStatus.Ineligible,null,null,"2026-09-13T12:00:00Z",false,create,1,25,true,null,
        offerMode=mode,canRedeemCoupon=redeem,
    )
    private inner class Fake:TrialGateway {
        var access=access(true,true,"ON")
        var applied:SaqzResult<TrialAccess,TrialError> = SaqzResult.Success(access)
        var pending:CompletableDeferred<SaqzResult<TrialAccess,TrialError>>?=null
        var code:String?=null
        var applies=0
        var offline=false
        override suspend fun ownerTrial() = if(offline) SaqzResult.Failure(TrialError.Data(DataError.Connectivity)) else SaqzResult.Success(access)
        override suspend fun groupTrial(groupId:GroupId)=ownerTrial()
        override suspend fun applyCoupon(code:String):SaqzResult<TrialAccess,TrialError> { this.code=code;applies++;return pending?.await() ?: applied }
    }
}
