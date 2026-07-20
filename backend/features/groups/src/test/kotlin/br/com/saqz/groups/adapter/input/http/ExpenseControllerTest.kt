package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.finance.charge.*
import br.com.saqz.groups.application.finance.expense.*
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.finance.charge.*
import br.com.saqz.groups.domain.finance.expense.*
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.*
import java.time.*
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ExpenseControllerTest{
    private val actor=UUID.randomUUID();private val group=UUID.randomUUID();private lateinit var repo:MemoryRepo;private lateinit var controller:ExpenseController
    @BeforeEach fun setup(){repo=MemoryRepo();val tx=object:TransactionRunner{override fun<T> inTransaction(block:()->T)=block()};controller=ExpenseController(VerifiedGroupActorResolver{actor},ExpenseService(tx,repo,{UUID.randomUUID()}){NOW},ChargeManagement(tx,ChargeRepo(),{NOW},{UUID.randomUUID()}))}
    @Test fun `owner lists exact local date integer cents and active total`(){repo.role=GroupRole.OWNER;repo.seed();val response=controller.list(ID,"$group");assertEquals(1,response.expenses.size);assertEquals(5000,response.activeExpenseTotalCents);assertEquals(LocalDate.of(2026,8,1),response.expenses.single().expenseDate)}
    @Test fun `admin can list expenses`(){repo.role=GroupRole.ADMIN;repo.seed();assertEquals(1,controller.list(ID,"$group").expenses.size)}
    @Test fun `athlete cannot read expenses or totals`(){repo.role=GroupRole.ATHLETE;assertFailsWith<AccessForbiddenException>{controller.list(ID,"$group")};assertFailsWith<AccessForbiddenException>{controller.totals(ID,"$group")}}
    @Test fun `nonmember expense access is hidden`(){repo.role=null;assertFailsWith<GameNotFoundException>{controller.list(ID,"$group")}}
    @Test fun `create returns 201 etag and bounded audit history`(){val response=controller.create(ID,"$group",request());assertEquals(201,response.statusCode.value());assertEquals("\"1\"",response.headers.eTag);assertEquals("CREATED",response.body!!.events.single().action)}
    @Test fun `create requires stable request id`(){assertFailsWith<InvalidGroupRequestException>{controller.create(ID,"$group",request().copy(requestId=null))}}
    @Test fun `create retry returns equivalent original without duplicate event`(){val id=UUID.randomUUID();val first=controller.create(ID,"$group",request(id)).body!!;val replay=controller.create(ID,"$group",request(id).copy(amountCents=9000)).body!!;assertEquals(first.id,replay.id);assertEquals(5000,replay.amountCents);assertEquals(1,replay.events.size)}
    @Test fun `validation exposes exact invalid fields`(){val e=assertFailsWith<InvalidGroupRequestException>{controller.create(ID,"$group",request().copy(description="x",amountCents=0,notes="x"))};assertEquals(setOf("description","amountCents","notes"),e.fieldErrors.keys)}
    @Test fun `unknown category is validation not server error`(){val e=assertFailsWith<InvalidGroupRequestException>{controller.create(ID,"$group",request().copy(category="SERVICE"))};assertEquals(setOf("category"),e.fieldErrors.keys)}
    @Test fun `edit uses etag and appends actor action time only history projection`(){val created=controller.create(ID,"$group",request()).body!!;val edited=controller.edit(ID,"$group","${created.id}","\"1\"",request().copy(requestId=null,amountCents=7000));assertEquals("\"2\"",edited.headers.eTag);assertEquals(listOf("CREATED","EDITED"),edited.body!!.events.map{it.action});assertEquals(actor,edited.body!!.events.last().actorId)}
    @Test fun `stale edit preserves authoritative row`(){val created=controller.create(ID,"$group",request()).body!!;controller.edit(ID,"$group","${created.id}","\"1\"",request().copy(amountCents=7000));assertFailsWith<VersionConflictException>{controller.edit(ID,"$group","${created.id}","\"1\"",request().copy(amountCents=9000))};assertEquals(7000,repo.rows.getValue(created.id).expense.snapshot.amountCents)}
    @Test fun `void returns versioned terminal expense and audit`(){val created=controller.create(ID,"$group",request()).body!!;val response=controller.void(ID,"$group","${created.id}","\"1\"");assertEquals("VOIDED",response.body!!.status);assertEquals("\"2\"",response.headers.eTag);assertEquals("VOIDED",response.body!!.events.last().action)}
    @Test fun `voided expense cannot edit or void again`(){val created=controller.create(ID,"$group",request()).body!!;controller.void(ID,"$group","${created.id}","\"1\"");assertFailsWith<InvalidGameTransitionException>{controller.void(ID,"$group","${created.id}","\"2\"")};assertFailsWith<InvalidGameTransitionException>{controller.edit(ID,"$group","${created.id}","\"2\"",request())}}
    @Test fun `mutations require quoted if match`(){val created=controller.create(ID,"$group",request()).body!!;assertFailsWith<PreconditionRequiredException>{controller.void(ID,"$group","${created.id}",null)};assertFailsWith<InvalidGroupRequestException>{controller.void(ID,"$group","${created.id}","1")}}
    @Test fun `malformed and unknown expense ids are hidden`(){assertFailsWith<GameNotFoundException>{controller.edit(ID,"$group","bad","\"1\"",request())};assertFailsWith<GameNotFoundException>{controller.void(ID,"$group","${UUID.randomUUID()}","\"1\"")}}
    @Test fun `totals expose defined manual buckets without net balance`(){repo.seed(amount=4000);repo.charges+=charge(100,ChargeStatus.PENDING);repo.charges+=charge(200,ChargeStatus.PAID);repo.charges+=charge(300,ChargeStatus.WAIVED);repo.charges+=charge(400,ChargeStatus.CANCELLED);val totals=controller.totals(ID,"$group");assertEquals(FinanceTotalsResponse(100,200,300,400,4000),totals)}
    @Test fun `expense dto and totals contain no settlement or reimbursement field`(){val names=(ExpenseRequest::class.java.declaredFields+ExpenseResponse::class.java.declaredFields+FinanceTotalsResponse::class.java.declaredFields).map{it.name.lowercase()};assertFalse(names.any{it.contains("settle")||it.contains("reimburse")||it.contains("balance")||it.contains("transfer")})}
    @Test fun `event response exposes only actor action and time`(){assertEquals(setOf("actorId","action","occurredAt"),ExpenseEventResponse::class.java.declaredFields.filterNot{it.isSynthetic}.map{it.name}.toSet())}

    private fun request(id:UUID=UUID.randomUUID())=ExpenseRequest(id,"Aluguel",5000,LocalDate.of(2026,8,1),"VENUE",null,"Quadra")
    private fun charge(amount:Long,status:ChargeStatus)=Charge(UUID.randomUUID(),group,UUID.randomUUID(),ChargeIdentity.Monthly(YearMonth.of(2026,8)),amount,LocalDate.of(2026,8,10),status)
    private inner class MemoryRepo:ExpenseRepository{var role:GroupRole?=GroupRole.OWNER;val rows=linkedMapOf<UUID,ExpenseWithEvents>();val charges=mutableListOf<Charge>();override fun role(actor:UUID,group:UUID)=if(group==this@ExpenseControllerTest.group)role else null;override fun list(group:UUID)=rows.values.toList();override fun find(group:UUID,id:UUID)=rows[id];override fun create(expense:Expense,event:ExpenseEvent):Boolean{if(rows.containsKey(expense.id))return false;rows[expense.id]=ExpenseWithEvents(expense,listOf(event));return true};override fun update(expense:Expense,event:ExpenseEvent,expectedVersion:Long):Boolean{val old=rows[expense.id]?:return false;if(old.expense.version!=expectedVersion)return false;rows[expense.id]=ExpenseWithEvents(expense,old.events+event);return true};fun seed(amount:Long=5000):Expense{val e=Expense(UUID.randomUUID(),group,ExpenseSnapshot("Aluguel",amount,LocalDate.of(2026,8,1),ExpenseCategory.VENUE,null,"Quadra"));rows[e.id]=ExpenseWithEvents(e,emptyList());return e}}
    private inner class ChargeRepo:ChargeManagementRepository{override fun role(actorId:UUID,groupId:UUID)=repo.role(actorId,groupId);override fun list(groupId:UUID,memberId:UUID?)=repo.charges.map{ChargeWithEvents(it,emptyList())};override fun find(groupId:UUID,chargeId:UUID)=repo.charges.firstOrNull{it.id==chargeId}?.let{ChargeWithEvents(it,emptyList())};override fun update(change:ChargeChange,expectedVersion:Long)=false}
    private companion object{val ID=RequestIdentity("subject",emailVerified=true,displayName="Player");val NOW=Instant.parse("2026-08-01T10:00:00Z")}
}
