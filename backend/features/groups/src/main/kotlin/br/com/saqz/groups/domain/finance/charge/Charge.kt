package br.com.saqz.groups.domain.finance.charge

import br.com.saqz.groups.domain.GroupRole
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class ChargeStatus { PENDING, PAID, WAIVED, CANCELLED }
sealed interface ChargeIdentity { data class Game(val gameId:UUID):ChargeIdentity;data class Monthly(val month:YearMonth):ChargeIdentity }
data class Charge(val id:UUID,val groupId:UUID,val memberId:UUID,val identity:ChargeIdentity,val amountCents:Long,val dueDate:LocalDate,val status:ChargeStatus=ChargeStatus.PENDING,val version:Long=1){init{require(amountCents in 1..99_999_999);require(version>=1)}}
data class ChargeStatusCommand(val target:ChargeStatus,val note:String?=null)
data class ChargeEvent(val id:UUID,val chargeId:UUID,val actorId:UUID,val oldStatus:ChargeStatus,val newStatus:ChargeStatus,val note:String?,val occurredAt:Instant)
data class ChargeChange(val charge:Charge,val event:ChargeEvent)
class InvalidChargeTransition:RuntimeException()
class InvalidChargeNote:RuntimeException()

object ChargeTransitions {
    fun apply(current:Charge,command:ChargeStatusCommand,actorId:UUID,now:Instant,eventId:UUID):ChargeChange{
        if(current.status!=ChargeStatus.PENDING||command.target==ChargeStatus.PENDING)throw InvalidChargeTransition()
        val note=command.note?.trim()?.also{if(it.length !in 2..500||it.any(Char::isISOControl))throw InvalidChargeNote()}
        val changed=current.copy(status=command.target,version=current.version+1)
        return ChargeChange(changed,ChargeEvent(eventId,current.id,actorId,current.status,command.target,note,now))
    }
}

data class ChargeTotals(val pendingCents:Long,val paidCents:Long,val waivedCents:Long,val cancelledCents:Long)
data class ChargeProjection(val charges:List<Charge>,val totals:ChargeTotals?)
class ChargeResourceHidden:RuntimeException()
object ChargeVisibility {
    fun project(actorId:UUID,role:GroupRole?,charges:List<Charge>):ChargeProjection=when(role){
        GroupRole.OWNER,GroupRole.ADMIN->ChargeProjection(charges,ChargeTotals(ChargeStatus.entries.associateWith{status->charges.filter{it.status==status}.sumOf{it.amountCents}}.let{it.getValue(ChargeStatus.PENDING)},charges.filter{it.status==ChargeStatus.PAID}.sumOf{it.amountCents},charges.filter{it.status==ChargeStatus.WAIVED}.sumOf{it.amountCents},charges.filter{it.status==ChargeStatus.CANCELLED}.sumOf{it.amountCents}))
        GroupRole.ATHLETE->ChargeProjection(charges.filter{it.memberId==actorId},null)
        null->throw ChargeResourceHidden()
    }
}
