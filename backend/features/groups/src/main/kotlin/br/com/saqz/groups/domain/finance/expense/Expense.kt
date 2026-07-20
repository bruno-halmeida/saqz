package br.com.saqz.groups.domain.finance.expense

import java.time.*
import java.util.UUID

enum class ExpenseCategory{VENUE,EQUIPMENT,REFEREE,OTHER}
enum class ExpenseStatus{ACTIVE,VOIDED}
enum class ExpenseAction{CREATED,EDITED,VOIDED}
data class ExpenseDraft(val description:String?,val amountCents:Long?,val expenseDate:LocalDate?,val category:ExpenseCategory?,val customCategory:String?=null,val notes:String?=null)
data class ExpenseSnapshot(val description:String,val amountCents:Long,val expenseDate:LocalDate,val category:ExpenseCategory,val customCategory:String?,val notes:String?)
data class Expense(val id:UUID,val groupId:UUID,val snapshot:ExpenseSnapshot,val status:ExpenseStatus=ExpenseStatus.ACTIVE,val version:Long=1){init{require(version>=1)}}
data class ExpenseEvent(val id:UUID,val expenseId:UUID,val actorId:UUID,val action:ExpenseAction,val snapshot:ExpenseSnapshot,val status:ExpenseStatus,val version:Long,val occurredAt:Instant)
data class ExpenseValidation(val snapshot:ExpenseSnapshot?,val fields:Set<String>)
object ExpenseValidator{
    fun validate(draft:ExpenseDraft):ExpenseValidation{val errors=linkedSetOf<String>();fun clean(value:String?,field:String,min:Int,max:Int):String?{val v=value?.trim()?.ifBlank{null};if(v==null||v.length !in min..max||v.any(Char::isISOControl))errors+=field;return v};val description=clean(draft.description,"description",2,160);val amount=draft.amountCents.also{if(it==null||it !in 1..99_999_999)errors+="amountCents"};val date=draft.expenseDate.also{if(it==null)errors+="expenseDate"};val category=draft.category.also{if(it==null)errors+="category"};val custom=if(category==ExpenseCategory.OTHER)clean(draft.customCategory,"customCategory",2,40) else null;val notes=draft.notes?.trim()?.ifBlank{null}?.also{if(it.length !in 2..500||it.any(Char::isISOControl))errors+="notes"};return ExpenseValidation(if(errors.isEmpty())ExpenseSnapshot(requireNotNull(description),requireNotNull(amount),requireNotNull(date),requireNotNull(category),custom,notes)else null,errors)}
}
