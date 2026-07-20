package br.com.saqz.groups.application.finance.charge

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.finance.charge.*
import java.time.*
import java.util.UUID
import kotlin.test.*

class ChargeTransactionsTest{
    @Test fun `confirmed paid attendance creates charge`(){assertNotNull(service().first.attendance(game(AttendanceBillingOutcome.CONFIRMED),actor))}
    @Test fun `promotion creates charge`(){assertNotNull(service().first.attendance(game(AttendanceBillingOutcome.PROMOTED),actor))}
    @Test fun `waitlisted declined no response and withdrawn create no charge`(){val (service,repo)=service();listOf(AttendanceBillingOutcome.WAITLISTED,AttendanceBillingOutcome.DECLINED,AttendanceBillingOutcome.NO_RESPONSE,AttendanceBillingOutcome.WITHDRAWN).forEach{assertNull(service.attendance(game(it),actor))};assertEquals(0,repo.gameCalls)}
    @Test fun `free game creates no charge`(){val (service,repo)=service();assertNull(service.attendance(game(AttendanceBillingOutcome.CONFIRMED).copy(gameFeeCents=null),actor));assertEquals(0,repo.gameCalls)}
    @Test fun `cancellation delegates atomically`(){val (service,repo)=service();service.cancelGame(group,gameId,actor);assertEquals(Triple(group,gameId,actor),repo.cancel)}
    @Test fun `monthly validates amount due date and selection`(){val (service,_)=service();assertIs<MonthlyGenerationResult.Invalid>(service.generate(monthly(amount=0)));assertIs<MonthlyGenerationResult.Invalid>(service.generate(monthly(due=LocalDate.of(2026,9,1))));assertIs<MonthlyGenerationResult.Invalid>(service.generate(monthly(members=emptySet())))}
    @Test fun `monthly rejects inactive selected member`(){val (service,repo)=service();repo.active=setOf(member);assertEquals(setOf("memberIds"),assertIs<MonthlyGenerationResult.Invalid>(service.generate(monthly(members=setOf(UUID.randomUUID())))).fields)}
    @Test fun `missing group is privacy hidden`(){val (service,repo)=service();repo.active=null;assertSame(MonthlyGenerationResult.Hidden,service.generate(monthly()))}
    @Test fun `valid monthly generation is stable ordered and transactional`(){val second=UUID.randomUUID();val (service,repo)=service();repo.active=setOf(member,second);val result=assertIs<MonthlyGenerationResult.Success>(service.generate(monthly(members=setOf(second,member))));assertEquals(setOf(member,second),result.charges.map{it.memberId}.toSet());assertEquals(1,repo.transactionCalls)}

    private fun service():Pair<ChargeTransactions,FakeRepo>{val repo=FakeRepo();return ChargeTransactions(object:TransactionRunner{override fun<T> inTransaction(block:()->T):T{repo.transactionCalls++;return block()}},repo){now} to repo}
    private fun game(outcome:AttendanceBillingOutcome)=GameChargeInput(group,gameId,member,2500,LocalDate.of(2026,8,10),outcome)
    private fun monthly(amount:Long=3000,due:LocalDate=LocalDate.of(2026,8,10),members:Set<UUID> = setOf(member))=MonthlyGenerationCommand(UUID.randomUUID(),group,actor,YearMonth.of(2026,8),amount,due,members)
    private class FakeRepo:ChargeTransactionRepository{var gameCalls=0;var cancel:Triple<UUID,UUID,UUID>?=null;var active:Set<UUID>?=setOf(member);var transactionCalls=0;override fun createGameCharge(input:GameChargeInput,actorId:UUID,now:Instant):Charge{gameCalls++;return charge(input.memberId,ChargeIdentity.Game(input.gameId),requireNotNull(input.gameFeeCents),input.dueDate)};override fun reconcileGameCancellation(groupId:UUID,gameId:UUID,actorId:UUID,now:Instant){cancel=Triple(groupId,gameId,actorId)};override fun activeMemberIds(groupId:UUID)=active;override fun createMonthlyCharge(command:MonthlyGenerationCommand,memberId:UUID,now:Instant)=charge(memberId,ChargeIdentity.Monthly(command.month),command.amountCents,command.dueDate);private fun charge(member:UUID,id:ChargeIdentity,amount:Long,due:LocalDate)=Charge(UUID.randomUUID(),group,member,id,amount,due)}
    private companion object{val group:UUID=UUID.randomUUID();val gameId:UUID=UUID.randomUUID();val member:UUID=UUID.randomUUID();val actor:UUID=UUID.randomUUID();val now:Instant=Instant.parse("2026-08-01T10:00:00Z")}
}
