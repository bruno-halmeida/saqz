package br.com.saqz.groups.presentation.finance.charges

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.finance.*
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `athlete loads only own charges without totals members or draft`()=runTest(mainDispatcher){val f=fixture(role=GroupRole.ATHLETE);runCurrent();assertEquals(1,f.athlete.calls);assertEquals(0,f.organizer.chargeCalls);assertNull(f.vm.state.value.totals);assertTrue(f.vm.state.value.members.isEmpty());assertNull(f.vm.state.value.monthlyDraft);assertEquals(0,f.store.reads)}
    @Test fun `athlete cannot dispatch monthly status or retry commands`()=runTest(mainDispatcher){val f=fixture(role=GroupRole.ATHLETE);runCurrent();f.vm.onIntent(FinanceIntent.UpdateMonthly("2026-08","70,00","2026-08-10",setOf(MEMBER)));f.vm.onIntent(FinanceIntent.UpdateStatus(CHARGE,ChargeStatus.Paid));f.vm.onIntent(FinanceIntent.GenerateMonthly);f.vm.onIntent(FinanceIntent.Retry);runCurrent();assertTrue(f.organizer.monthlyCalls.isEmpty());assertTrue(f.organizer.statusCalls.isEmpty());assertEquals(0,f.store.writes.size)}
    @Test fun `owner loads full charges totals and memberships without own route`()=runTest(mainDispatcher){val f=fixture();runCurrent();val state=f.vm.state.value;assertEquals(0,f.athlete.calls);assertEquals(1,f.organizer.chargeCalls);assertEquals(2,state.members.size);assertEquals(ChargeTotalsState(2500,5000,1000,7000),state.totals);assertEquals(2,state.charges.size)}
    @Test fun `admin has organizer charge capabilities`()=runTest(mainDispatcher){val f=fixture(role=GroupRole.ADMIN);runCurrent();assertTrue(f.vm.state.value.organizer);assertEquals(1,f.organizer.chargeCalls);assertEquals(1,f.memberships.calls)}
    @Test fun `matching versioned monthly draft restores exact key and selection`()=runTest(mainDispatcher){val restored=draft(reviewed=true);val f=fixture(restored=restored);runCurrent();assertEquals(restored,f.vm.state.value.monthlyDraft)}
    @Test fun `foreign or unsupported draft never restores or dispatches`()=runTest(mainDispatcher){val f=fixture(restored=draft().copy(schemaVersion=99,groupId="other"));runCurrent();assertNull(f.vm.state.value.monthlyDraft);assertTrue(f.organizer.monthlyCalls.isEmpty())}
    @Test fun `monthly update filters inactive identifiers and persists stable key`()=runTest(mainDispatcher){val f=fixture();runCurrent();f.vm.onIntent(FinanceIntent.UpdateMonthly("2026-08","70,00","2026-08-10",setOf(MEMBER,"removed")));val value=f.vm.state.value.monthlyDraft!!;assertEquals(KEY,value.commandKey);assertEquals(setOf(MEMBER),value.selectedMemberIds);assertEquals(value,f.store.writes.single())}
    @Test fun `monthly edits retain one command key and reset review`()=runTest(mainDispatcher){val f=fixture();runCurrent();f.vm.onIntent(update());f.vm.onIntent(FinanceIntent.ReviewMonthly);f.vm.onIntent(update(amount="80,00"));assertEquals(listOf(KEY,KEY,KEY),f.store.writes.map{it.commandKey});assertFalse(f.vm.state.value.monthlyDraft!!.reviewed)}
    @Test fun `monthly review reports month amount date and member errors`()=runTest(mainDispatcher){val f=fixture();runCurrent();f.vm.onIntent(FinanceIntent.UpdateMonthly("2026-13","0","bad",emptySet()));f.vm.onIntent(FinanceIntent.ReviewMonthly);assertEquals(setOf("month","amountBrl","dueDate","memberIds"),f.vm.state.value.fieldErrors.keys);assertEquals(FinanceError.VALIDATION,f.vm.state.value.error)}
    @Test fun `valid monthly review persists reviewed draft without generating`()=runTest(mainDispatcher){val f=fixture();runCurrent();f.vm.onIntent(update());f.vm.onIntent(FinanceIntent.ReviewMonthly);assertTrue(f.vm.state.value.monthlyDraft!!.reviewed);assertEquals(2,f.store.writes.size);assertTrue(f.organizer.monthlyCalls.isEmpty())}
    @Test fun `monthly generation requires reviewed draft`()=runTest(mainDispatcher){val f=fixture();runCurrent();f.vm.onIntent(update());f.vm.onIntent(FinanceIntent.GenerateMonthly);runCurrent();assertTrue(f.organizer.monthlyCalls.isEmpty())}
    @Test fun `double generate is one logical stable command`()=runTest(mainDispatcher){val f=fixture();runCurrent();review(f);f.organizer.gate=CompletableDeferred();f.vm.onIntent(FinanceIntent.GenerateMonthly);f.vm.onIntent(FinanceIntent.GenerateMonthly);runCurrent();assertEquals(1,f.organizer.monthlyCalls.size);f.organizer.gate!!.complete(Unit);runCurrent()}
    @Test fun `generation sends exact cents members and key then clears matching draft`()=runTest(mainDispatcher){val f=fixture();runCurrent();review(f);val effect=async{f.vm.effects.first()};f.vm.onIntent(FinanceIntent.GenerateMonthly);runCurrent();assertEquals(MonthlyChargeCommand(KEY,"2026-08",7000,"2026-08-10",setOf(MEMBER,MEMBER2)),f.organizer.monthlyCalls.single());assertEquals(listOf(GROUP to KEY),f.store.clears);assertNull(f.vm.state.value.monthlyDraft);assertEquals("Cobranças registradas manualmente.",f.vm.state.value.lastManualOutcome);assertEquals(FinanceEffect.MonthlyGenerated(1),effect.await())}
    @Test fun `generation refreshes authoritative full charges and totals`()=runTest(mainDispatcher){val f=fixture();runCurrent();review(f);f.vm.onIntent(FinanceIntent.GenerateMonthly);runCurrent();assertEquals(2,f.organizer.chargeCalls);assertEquals(2,f.vm.state.value.charges.size);assertEquals(2500,f.vm.state.value.totals!!.pendingCents)}
    @Test fun `status command derives and preserves quoted charge version ETag`()=runTest(mainDispatcher){val f=fixture();runCurrent();f.vm.onIntent(FinanceIntent.UpdateStatus(CHARGE,ChargeStatus.Waived,"  Ajuste manual  "));runCurrent();assertEquals(StatusCall(CHARGE,FinanceVersionToken("\"4\""),ChargeStatusCommand(ChargeStatus.Waived,"Ajuste manual")),f.organizer.statusCalls.single())}
    @Test fun `status success uses authoritative audit without editing amount actor or time`()=runTest(mainDispatcher){val f=fixture();runCurrent();val effect=async{f.vm.effects.first()};f.vm.onIntent(FinanceIntent.UpdateStatus(CHARGE,ChargeStatus.Paid));runCurrent();val updated=f.vm.state.value.charges.first{it.id==CHARGE};assertEquals(2500,updated.amountCents);assertEquals("actor",updated.audit.single().actorId);assertEquals("2026-08-12T10:00:00Z",updated.audit.single().occurredAt);assertEquals("Status registrado manualmente no histórico.",f.vm.state.value.lastManualOutcome);assertEquals(FinanceEffect.StatusRecorded(CHARGE,ChargeStatus.Paid),effect.await())}
    @Test fun `status conflict preserves exact ETag and command for retry`()=runTest(mainDispatcher){val f=fixture();runCurrent();f.organizer.statusResult=SaqzResult.Failure(br.com.saqz.groups.domain.finance.FinanceError.Conflict);f.vm.onIntent(FinanceIntent.UpdateStatus(CHARGE,ChargeStatus.Paid));runCurrent();assertEquals(FinanceError.CONFLICT,f.vm.state.value.error);assertTrue(f.vm.state.value.reloadAvailable);f.organizer.statusResult=SaqzResult.Success(VersionedCharge(paidCharge(),FinanceVersionToken("\"5\"")));f.vm.onIntent(FinanceIntent.Retry);runCurrent();assertEquals(listOf("\"4\"","\"4\""),f.organizer.statusCalls.map{it.version.value})}
    @Test fun `server validation fields survive status failure`()=runTest(mainDispatcher){val f=fixture();runCurrent();f.organizer.statusResult=SaqzResult.Failure(br.com.saqz.groups.domain.finance.FinanceError.Validation(DataError.Validation(ValidationDetails(emptyList(),mapOf("note" to listOf("is invalid"))))));f.vm.onIntent(FinanceIntent.UpdateStatus(CHARGE,ChargeStatus.Waived,"x"));runCurrent();assertEquals(mapOf("note" to listOf("is invalid")),f.vm.state.value.fieldErrors)}
    @Test fun `draft write failure is typed without changing stable draft`()=runTest(mainDispatcher){val f=fixture();runCurrent();f.store.failWrites=true;f.vm.onIntent(update());assertEquals(FinanceError.DRAFT_UNAVAILABLE,f.vm.state.value.error);assertEquals(KEY,f.vm.state.value.monthlyDraft!!.commandKey)}
    @Test fun `state copy always identifies manual tracking and avoids settlement claim`()=runTest(mainDispatcher){val f=fixture(role=GroupRole.ATHLETE);runCurrent();val copy=f.vm.state.value.manualTrackingNotice;assertEquals("Controle manual: o app apenas registra cobranças e seus status.",copy);assertFalse(copy.contains("liquidado",true));assertFalse(copy.contains("saldo",true))}

    private fun fixture(role:GroupRole=GroupRole.OWNER,restored:MonthlyChargeDraft?=null):Fixture{val athlete=FakeAthlete();val organizer=FakeOrganizer();val members=FakeMemberships();val store=FakeStore(restored);val capability=if(role==GroupRole.ATHLETE)ChargeFinanceCapability.Athlete else ChargeFinanceCapability.Organizer(organizer);val vm=FinanceViewModel(GROUP,role,athlete,capability,members,store,FinanceCommandKeyFactory{KEY});return Fixture(vm,athlete,organizer,members,store)}
    private fun update(amount:String="70,00")=FinanceIntent.UpdateMonthly("2026-08",amount,"2026-08-10",setOf(MEMBER,MEMBER2))
    private fun draft(reviewed:Boolean=false)=MonthlyChargeDraft(groupId=GROUP,commandKey=KEY,month="2026-08",amountBrl="70,00",dueDate="2026-08-10",selectedMemberIds=setOf(MEMBER,MEMBER2),reviewed=reviewed)
    private fun review(f:Fixture){f.vm.onIntent(update());f.vm.onIntent(FinanceIntent.ReviewMonthly)}
    private fun charges()=ChargeList(listOf(charge(),charge("charge-2",MEMBER2)),ChargeTotals(2500,5000,1000,7000))
    private fun charge(id:String=CHARGE,member:String=MEMBER,status:ChargeStatus=ChargeStatus.Pending,version:Long=4,audit:List<ChargeAudit> = emptyList())=Charge(id,GroupId(GROUP),member,ChargeKind.Game,"game",null,2500,"2026-08-12",status,false,version,audit)
    private fun paidCharge()=charge(status=ChargeStatus.Paid,version=5,audit=listOf(ChargeAudit("actor",ChargeStatus.Pending,ChargeStatus.Paid,null,"2026-08-12T10:00:00Z")))
    private data class Fixture(val vm:FinanceViewModel,val athlete:FakeAthlete,val organizer:FakeOrganizer,val memberships:FakeMemberships,val store:FakeStore)
    private data class StatusCall(val chargeId:String,val version:FinanceVersionToken,val command:ChargeStatusCommand)
    private class FakeAthlete:AthleteFinanceGateway{var calls=0;override suspend fun ownCharges(groupId:GroupId):SaqzResult<ChargeList,br.com.saqz.groups.domain.finance.FinanceError>{calls++;return SaqzResult.Success(ChargeList(listOf(chargeStatic())))}}
    private class FakeOrganizer : OrganizerFinanceGateway {
        var chargeCalls = 0
        val monthlyCalls = mutableListOf<MonthlyChargeCommand>()
        val statusCalls = mutableListOf<StatusCall>()
        var gate: CompletableDeferred<Unit>? = null
        var statusResult: SaqzResult<VersionedCharge, br.com.saqz.groups.domain.finance.FinanceError> =
            SaqzResult.Success(VersionedCharge(paidStatic(), FinanceVersionToken("\"5\"")))

        override suspend fun charges(groupId: GroupId): SaqzResult<ChargeList, br.com.saqz.groups.domain.finance.FinanceError> {
            chargeCalls++
            return SaqzResult.Success(chargesStatic())
        }
        override suspend fun generateMonthly(groupId: GroupId, command: MonthlyChargeCommand): SaqzResult<ChargeList, br.com.saqz.groups.domain.finance.FinanceError> {
            monthlyCalls += command
            gate?.await()
            return SaqzResult.Success(ChargeList(listOf(chargeStatic("generated"))))
        }
        override suspend fun updateChargeStatus(groupId: GroupId, chargeId: String, version: FinanceVersionToken, command: ChargeStatusCommand): SaqzResult<VersionedCharge, br.com.saqz.groups.domain.finance.FinanceError> {
            statusCalls += StatusCall(chargeId, version, command)
            return statusResult
        }
        override suspend fun expenses(groupId: GroupId): SaqzResult<ExpenseList, br.com.saqz.groups.domain.finance.FinanceError> = error("not used")
        override suspend fun createExpense(groupId: GroupId, command: ExpenseWriteCommand): SaqzResult<VersionedExpense, br.com.saqz.groups.domain.finance.FinanceError> = error("not used")
        override suspend fun editExpense(groupId: GroupId, expenseId: String, version: FinanceVersionToken, command: ExpenseWriteCommand): SaqzResult<VersionedExpense, br.com.saqz.groups.domain.finance.FinanceError> = error("not used")
        override suspend fun voidExpense(groupId: GroupId, expenseId: String, version: FinanceVersionToken): SaqzResult<VersionedExpense, br.com.saqz.groups.domain.finance.FinanceError> = error("not used")
        override suspend fun totals(groupId: GroupId): SaqzResult<FinanceTotals, br.com.saqz.groups.domain.finance.FinanceError> = error("not used")
    }
    private class FakeMemberships : GroupMembershipGateway {
        var calls = 0
        override suspend fun listMemberships(groupId: GroupId): SaqzResult<List<GroupMembership>, GroupMembershipError> {
            calls++
            return SaqzResult.Success(listOf(GroupMembership(MEMBER,"Ana",GroupRole.ATHLETE),GroupMembership(MEMBER2,"Bia",GroupRole.ATHLETE)))
        }
        override suspend fun changeRole(command: ChangeMembershipRoleCommand): SaqzResult<GroupMembership, GroupMembershipError> = error("not used")
        override suspend fun rotateInvite(groupId: GroupId): SaqzResult<GroupInviteUrl, GroupMembershipError> = error("not used")
        override suspend fun expireInvite(groupId: GroupId) = error("not used")
        override suspend fun redeem(code: InviteCode): SaqzResult<RedeemedMembership, GroupMembershipError> = error("not used")
    }
    private class FakeStore(private val restored:MonthlyChargeDraft?):MonthlyChargeDraftStorePort{var reads=0;val writes=mutableListOf<MonthlyChargeDraft>();val clears=mutableListOf<Pair<String,String>>();var failWrites=false;override fun read(groupId:String,done:(MonthlyDraftReadResult)->Unit){reads++;done(MonthlyDraftReadResult.Success(restored))};override fun write(draft:MonthlyChargeDraft,done:(MonthlyDraftWriteResult)->Unit){writes+=draft;done(if(failWrites)MonthlyDraftWriteResult.Failure else MonthlyDraftWriteResult.Success)};override fun clear(groupId:String,commandKey:String,done:(MonthlyDraftWriteResult)->Unit){clears+=groupId to commandKey;done(MonthlyDraftWriteResult.Success)}}
    private companion object{const val GROUP="group";const val MEMBER="member-1";const val MEMBER2="member-2";const val CHARGE="charge-1";const val KEY="finance-key";fun chargeStatic(id:String=CHARGE)=Charge(id,GroupId(GROUP),MEMBER,ChargeKind.Game,"game",null,2500,"2026-08-12",ChargeStatus.Pending,false,4,emptyList());fun paidStatic()=chargeStatic().copy(status=ChargeStatus.Paid,version=5,audit=listOf(ChargeAudit("actor",ChargeStatus.Pending,ChargeStatus.Paid,null,"2026-08-12T10:00:00Z")));fun chargesStatic()=ChargeList(listOf(chargeStatic(),chargeStatic("charge-2").copy(memberId=MEMBER2)),ChargeTotals(2500,5000,1000,7000))}
}
