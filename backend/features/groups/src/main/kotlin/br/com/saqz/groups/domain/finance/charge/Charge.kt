package br.com.saqz.groups.domain.finance.charge

import br.com.saqz.groups.domain.GroupRole
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class ChargeStatus { PENDING, PAID, WAIVED, CANCELLED }
enum class PaidMethod { PIX, CASH, OTHER }
sealed interface ChargeIdentity { data class Game(val gameId:UUID):ChargeIdentity;data class Monthly(val month:YearMonth):ChargeIdentity }
data class Charge(val id:UUID,val groupId:UUID,val memberId:UUID,val identity:ChargeIdentity,val amountCents:Long,val dueDate:LocalDate,val status:ChargeStatus=ChargeStatus.PENDING,val version:Long=1,val reviewRequired:Boolean=false,val paidMethod:PaidMethod?=null,val guestDisplayName:String?=null,val withdrew:Boolean=false,val electronicOrder:Boolean=false,val noShow:Boolean=false,
    /** FIN-010/FIN-014: o que o atleta paga (nulo = amountCents), quem deu a baixa (APP = baixa automática) e o meio no app. */
    val payableCents:Long?=null,val settledBy:String?=null,val appMethod:String?=null){init{require(amountCents in 1..99_999_999);require(version>=1)}}
data class ChargeStatusCommand(val target:ChargeStatus,val note:String?=null,val paidMethod:PaidMethod?=null)
data class ChargeEvent(val id:UUID,val chargeId:UUID,val actorId:UUID,val oldStatus:ChargeStatus,val newStatus:ChargeStatus,val note:String?,val occurredAt:Instant)
data class ChargeChange(val charge:Charge,val event:ChargeEvent)
class InvalidChargeTransition:RuntimeException()
class InvalidChargeNote:RuntimeException()

object ChargeTransitions {
    fun apply(current:Charge,command:ChargeStatusCommand,actorId:UUID,now:Instant,eventId:UUID):ChargeChange{
        // Desfazer baixa: recebida ou dispensada volta a pendente, salvo se passou por ordem eletrônica ou foi paga pelo app (FIN-014). Cancelada não volta.
        val undo=command.target==ChargeStatus.PENDING
        if(undo){if(current.status !in setOf(ChargeStatus.PAID,ChargeStatus.WAIVED)||current.electronicOrder||current.settledBy!=null)throw InvalidChargeTransition()}
        else if(current.status!=ChargeStatus.PENDING)throw InvalidChargeTransition()
        val note=command.note?.trim()?.also{if(it.length !in 2..500||it.any(Char::isISOControl))throw InvalidChargeNote()}
        // Só o recebimento tem "como recebeu"; PIX é o default de quem não manda o campo.
        val method=if(command.target==ChargeStatus.PAID)command.paidMethod?:PaidMethod.PIX else null
        val changed=current.copy(status=command.target,version=current.version+1,paidMethod=method)
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
