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
    @Test fun webPreauthorizationSkipsOfferAndRefreshStillHonorsRevocation() = runTest {
        val gateway = Fake().apply { access = access(true, true, "ON").copy(preauthorized = true) }
        val vm = TrialEntryViewModel(gateway)
        assertTrue(vm.state.value.ready)
        gateway.access = access(false, false, "OFF").copy(preauthorized = true)
        vm.onIntent(TrialEntryIntent.Refresh)
        assertFalse(vm.state.value.ready)
    }
    @Test fun publicTrialRequiresContinueAndRechecksAvailability() = runTest {
        val gateway = Fake(); val vm = TrialEntryViewModel(gateway)
        assertEquals(14, vm.state.value.access?.trialDays)
        assertFalse(vm.state.value.ready)
        gateway.access = access(false, false, "OFF")
        vm.onIntent(TrialEntryIntent.Continue)
        assertFalse(vm.state.value.ready)
        assertEquals("OFF", vm.state.value.access?.offerMode)
    }
    @Test fun couponOnlyRequiresWebAuthorizationBeforeOpeningGroupCreation() = runTest {
        val gateway = Fake().apply { access = access(false, true, "COUPON_ONLY") }
        val vm = TrialEntryViewModel(gateway)
        vm.onIntent(TrialEntryIntent.Continue)
        assertFalse(vm.state.value.ready)
        gateway.access = access(true, true, "COUPON_ONLY").copy(trialDays = 45, preauthorized = true)
        vm.onIntent(TrialEntryIntent.Refresh)
        assertTrue(vm.state.value.ready)
        assertEquals(45, vm.state.value.access?.trialDays)
    }
    @Test fun changedDurationRequiresAcceptingTheUpdatedOffer() = runTest {
        val gateway = Fake(); val vm = TrialEntryViewModel(gateway)
        gateway.access = gateway.access.copy(trialDays = 7)
        vm.onIntent(TrialEntryIntent.Continue)
        assertFalse(vm.state.value.ready)
        assertEquals(7, vm.state.value.access?.trialDays)
        vm.onIntent(TrialEntryIntent.Continue)
        assertTrue(vm.state.value.ready)
    }
    @Test fun lateAcceptanceCannotOverrideANewerDeniedLookup() = runTest {
        val gateway = Fake(); val vm = TrialEntryViewModel(gateway)
        val pending = CompletableDeferred<SaqzResult<TrialAccess, TrialError>>()
        gateway.pending = pending
        vm.onIntent(TrialEntryIntent.Continue)
        assertTrue(vm.state.value.loading)
        vm.onIntent(TrialEntryIntent.Continue)
        gateway.pending = null
        gateway.access = access(false, false, "OFF")
        vm.onIntent(TrialEntryIntent.Refresh)
        pending.complete(SaqzResult.Success(access(true, true, "ON")))
        assertFalse(vm.state.value.ready)
        assertEquals("OFF", vm.state.value.access?.offerMode)
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
        if(create) TrialStatus.Available else TrialStatus.Ineligible,null,null,"2026-09-13T12:00:00Z",false,create,3,null,true,null,
        offerMode=mode,canRedeemCoupon=redeem,
    )
    private inner class Fake:TrialGateway {
        var access=access(true,true,"ON")
        var pending:CompletableDeferred<SaqzResult<TrialAccess,TrialError>>?=null
        var offline=false
        override suspend fun ownerTrial() = pending?.await() ?: if(offline) SaqzResult.Failure(TrialError.Data(DataError.Connectivity)) else SaqzResult.Success(access)
        override suspend fun groupTrial(groupId:GroupId)=ownerTrial()
    }
}
