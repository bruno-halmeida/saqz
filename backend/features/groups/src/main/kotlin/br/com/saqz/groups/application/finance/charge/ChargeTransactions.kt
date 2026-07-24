package br.com.saqz.groups.application.finance.charge

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.finance.charge.Charge
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class AttendanceBillingOutcome { CONFIRMED, PROMOTED, WAITLISTED, DECLINED, NO_RESPONSE, WITHDRAWN }
data class GameChargeInput(val groupId:UUID,val gameId:UUID,val memberId:UUID,val gameFeeCents:Long?,val dueDate:LocalDate,val outcome:AttendanceBillingOutcome)
data class MonthlyGenerationCommand(val requestId:UUID,val groupId:UUID,val actorId:UUID,val month:YearMonth,val amountCents:Long,val dueDate:LocalDate,val selectedMemberIds:Set<UUID>)
data class GroupMembers(val all:Set<UUID>,val active:Set<UUID>)
sealed interface MonthlyGenerationResult{data class Success(val charges:List<Charge>,val excludedInactiveCount:Int=0):MonthlyGenerationResult;data class Invalid(val fields:Set<String>):MonthlyGenerationResult;data object Hidden:MonthlyGenerationResult}
interface ChargeTransactionRepository{
    fun createGameCharge(input:GameChargeInput,actorId:UUID,now:Instant):Charge
    fun reconcileGameCancellation(groupId:UUID,gameId:UUID,actorId:UUID,now:Instant)
    fun members(groupId:UUID):GroupMembers?
    fun createMonthlyCharge(command:MonthlyGenerationCommand,memberId:UUID,now:Instant):Charge
}
class ChargeTransactions(private val transaction:TransactionRunner,private val repository:ChargeTransactionRepository,private val now:()->Instant){
    fun attendance(input:GameChargeInput,actorId:UUID):Charge?=transaction.inTransaction{
        if(input.outcome !in setOf(AttendanceBillingOutcome.CONFIRMED,AttendanceBillingOutcome.PROMOTED)||input.gameFeeCents==null)return@inTransaction null
        repository.createGameCharge(input,actorId,now())
    }
    fun cancelGame(groupId:UUID,gameId:UUID,actorId:UUID)=transaction.inTransaction{repository.reconcileGameCancellation(groupId,gameId,actorId,now())}
    fun generate(command:MonthlyGenerationCommand):MonthlyGenerationResult=transaction.inTransaction{
        val fields=buildSet{if(command.amountCents !in 1..99_999_999)add("amountCents");if(command.dueDate.month!=command.month.month||command.dueDate.year!=command.month.year)add("dueDate");if(command.selectedMemberIds.isEmpty())add("memberIds")}
        if(fields.isNotEmpty())return@inTransaction MonthlyGenerationResult.Invalid(fields)
        val members=repository.members(command.groupId)?:return@inTransaction MonthlyGenerationResult.Hidden
        if(!members.all.containsAll(command.selectedMemberIds))return@inTransaction MonthlyGenerationResult.Invalid(setOf("memberIds"))
        val (active,inactive)=command.selectedMemberIds.partition{it in members.active}
        MonthlyGenerationResult.Success(active.sortedBy(UUID::toString).map{repository.createMonthlyCharge(command,it,now())},inactive.size)
    }
}
