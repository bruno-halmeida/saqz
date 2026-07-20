package br.com.saqz.groups.application.finance.expense

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.finance.expense.*
import java.time.Instant
import java.util.UUID

data class ExpenseWithEvents(val expense:Expense,val events:List<ExpenseEvent>)
data class ExpenseList(val expenses:List<ExpenseWithEvents>,val activeTotalCents:Long)
sealed interface ExpenseResult{data class Saved(val value:ExpenseWithEvents):ExpenseResult;data class Listed(val value:ExpenseList):ExpenseResult;data class Invalid(val fields:Set<String>):ExpenseResult;data object Hidden:ExpenseResult;data object Forbidden:ExpenseResult;data object Conflict:ExpenseResult;data object InvalidLifecycle:ExpenseResult}
interface ExpenseRepository{fun role(actor:UUID,group:UUID):GroupRole?;fun list(group:UUID):List<ExpenseWithEvents>;fun find(group:UUID,id:UUID):ExpenseWithEvents?;fun create(expense:Expense,event:ExpenseEvent):Boolean;fun update(expense:Expense,event:ExpenseEvent,expectedVersion:Long):Boolean}
class ExpenseService(private val tx:TransactionRunner,private val repository:ExpenseRepository,private val ids:()->UUID,private val now:()->Instant){
    fun list(actor:UUID,group:UUID):ExpenseResult=authorized(actor,group){val rows=repository.list(group);ExpenseResult.Listed(ExpenseList(rows,rows.filter{it.expense.status==ExpenseStatus.ACTIVE}.sumOf{it.expense.snapshot.amountCents}))}
    fun create(actor:UUID,group:UUID,draft:ExpenseDraft):ExpenseResult=tx.inTransaction{authorized(actor,group){val valid=ExpenseValidator.validate(draft);if(valid.fields.isNotEmpty())return@authorized ExpenseResult.Invalid(valid.fields);val expense=Expense(ids(),group,requireNotNull(valid.snapshot));val event=expense.event(actor,ExpenseAction.CREATED,ids(),now());if(!repository.create(expense,event))ExpenseResult.Conflict else ExpenseResult.Saved(ExpenseWithEvents(expense,listOf(event)))}}
    fun edit(actor:UUID,group:UUID,id:UUID,version:Long,draft:ExpenseDraft):ExpenseResult=tx.inTransaction{authorized(actor,group){val current=repository.find(group,id)?:return@authorized ExpenseResult.Hidden;if(current.expense.version!=version)return@authorized ExpenseResult.Conflict;if(current.expense.status!=ExpenseStatus.ACTIVE)return@authorized ExpenseResult.InvalidLifecycle;val valid=ExpenseValidator.validate(draft);if(valid.fields.isNotEmpty())return@authorized ExpenseResult.Invalid(valid.fields);val changed=current.expense.copy(snapshot=requireNotNull(valid.snapshot),version=version+1);val event=changed.event(actor,ExpenseAction.EDITED,ids(),now());if(!repository.update(changed,event,version))ExpenseResult.Conflict else ExpenseResult.Saved(ExpenseWithEvents(changed,current.events+event))}}
    fun void(actor:UUID,group:UUID,id:UUID,version:Long):ExpenseResult=tx.inTransaction{authorized(actor,group){val current=repository.find(group,id)?:return@authorized ExpenseResult.Hidden;if(current.expense.version!=version)return@authorized ExpenseResult.Conflict;if(current.expense.status!=ExpenseStatus.ACTIVE)return@authorized ExpenseResult.InvalidLifecycle;val changed=current.expense.copy(status=ExpenseStatus.VOIDED,version=version+1);val event=changed.event(actor,ExpenseAction.VOIDED,ids(),now());if(!repository.update(changed,event,version))ExpenseResult.Conflict else ExpenseResult.Saved(ExpenseWithEvents(changed,current.events+event))}}
    private fun authorized(actor:UUID,group:UUID,block:()->ExpenseResult):ExpenseResult=when(repository.role(actor,group)){null->ExpenseResult.Hidden;GroupRole.ATHLETE->ExpenseResult.Forbidden;GroupRole.OWNER,GroupRole.ADMIN->block()}
}
private fun Expense.event(actor:UUID,action:ExpenseAction,id:UUID,time:Instant)=ExpenseEvent(id,this.id,actor,action,snapshot,status,version,time)
