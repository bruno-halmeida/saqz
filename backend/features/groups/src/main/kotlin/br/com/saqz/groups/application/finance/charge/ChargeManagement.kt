package br.com.saqz.groups.application.finance.charge

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.finance.charge.*
import java.time.Instant
import java.util.UUID

data class ChargeWithEvents(val charge:Charge,val events:List<ChargeEvent>)
sealed interface ChargeListResult{data class Success(val charges:List<ChargeWithEvents>,val totals:ChargeTotals?):ChargeListResult;data object Hidden:ChargeListResult;data object Forbidden:ChargeListResult}
sealed interface ChargeStatusResult{data class Success(val value:ChargeWithEvents):ChargeStatusResult;data object Hidden:ChargeStatusResult;data object Forbidden:ChargeStatusResult;data object Conflict:ChargeStatusResult;data object InvalidTransition:ChargeStatusResult;data object InvalidNote:ChargeStatusResult}
interface ChargeManagementRepository{
    fun role(actorId:UUID,groupId:UUID):GroupRole?
    fun list(groupId:UUID,memberId:UUID?=null):List<ChargeWithEvents>
    fun find(groupId:UUID,chargeId:UUID):ChargeWithEvents?
    fun update(change:ChargeChange,expectedVersion:Long):Boolean
}
class ChargeManagement(private val transaction:TransactionRunner,private val repository:ChargeManagementRepository,private val now:()->Instant,private val eventIds:()->UUID){
    fun listOrganizer(actor:UUID,group:UUID):ChargeListResult{val role=repository.role(actor,group)?:return ChargeListResult.Hidden;if(role !in setOf(GroupRole.OWNER,GroupRole.ADMIN))return ChargeListResult.Forbidden;val rows=repository.list(group);return ChargeListResult.Success(rows,ChargeVisibility.project(actor,role,rows.map{it.charge}).totals)}
    fun listOwn(actor:UUID,group:UUID):ChargeListResult{val role=repository.role(actor,group)?:return ChargeListResult.Hidden;val rows=repository.list(group,actor);return ChargeListResult.Success(rows,null)}
    fun status(actor:UUID,group:UUID,chargeId:UUID,version:Long,command:ChargeStatusCommand):ChargeStatusResult=transaction.inTransaction{
        val role=repository.role(actor,group)?:return@inTransaction ChargeStatusResult.Hidden;if(role !in setOf(GroupRole.OWNER,GroupRole.ADMIN))return@inTransaction ChargeStatusResult.Forbidden
        val current=repository.find(group,chargeId)?:return@inTransaction ChargeStatusResult.Hidden;if(current.charge.version!=version)return@inTransaction ChargeStatusResult.Conflict
        val change=try{ChargeTransitions.apply(current.charge,command,actor,now(),eventIds())}catch(_:InvalidChargeTransition){return@inTransaction ChargeStatusResult.InvalidTransition}catch(_:InvalidChargeNote){return@inTransaction ChargeStatusResult.InvalidNote}
        if(!repository.update(change,version))return@inTransaction ChargeStatusResult.Conflict
        ChargeStatusResult.Success(ChargeWithEvents(change.charge,current.events+change.event))
    }
}
