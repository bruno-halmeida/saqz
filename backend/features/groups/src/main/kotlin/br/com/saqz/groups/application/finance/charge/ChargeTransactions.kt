package br.com.saqz.groups.application.finance.charge

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.finance.charge.Charge
import br.com.saqz.groups.domain.finance.charge.PaidMethod
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess

enum class AttendanceBillingOutcome { CONFIRMED, PROMOTED, WAITLISTED, DECLINED, NO_RESPONSE, WITHDRAWN }
/** [advancePayment]: grupo com pagamento antecipado (DEB-150); a diária nasce também na espera ([AttendanceBillingOutcome.WAITLISTED]). */
data class GameChargeInput(val groupId:UUID,val gameId:UUID,val memberId:UUID,val gameFeeCents:Long?,val dueDate:LocalDate,val outcome:AttendanceBillingOutcome,val guestSeq:Int=0,val advancePayment:Boolean=false)
data class MonthlyGenerationCommand(val requestId:UUID,val groupId:UUID,val actorId:UUID,val month:YearMonth,val amountCents:Long,val dueDate:LocalDate,val selectedMemberIds:Set<UUID>)
// exempt: mensalistas com isenção da mensalidade (DEB-115); a geração os pula sem erro.
data class GroupMembers(val all:Set<UUID>,val active:Set<UUID>,val exempt:Set<UUID> = emptySet())
sealed interface MonthlyGenerationResult{data class Success(val charges:List<Charge>,val excludedInactiveCount:Int=0):MonthlyGenerationResult;data class Invalid(val fields:Set<String>):MonthlyGenerationResult;data object Hidden:MonthlyGenerationResult}
interface ChargeTransactionRepository{
    fun createGameCharge(input:GameChargeInput,actorId:UUID,now:Instant):Charge
    fun reconcileGameCancellation(groupId:UUID,gameId:UUID,actorId:UUID,now:Instant)
    /** Diária (kind GAME) é só do avulso; convidado cobra do anfitrião, seja qual for o vínculo dele. Vínculo lido na hora da entrada no jogo. */
    fun billsGameCharge(groupId:UUID,memberId:UUID,guestSeq:Int):Boolean=true
    fun members(groupId:UUID):GroupMembers?
    fun createMonthlyCharge(command:MonthlyGenerationCommand,memberId:UUID,now:Instant):Charge
    fun cancelGuestCharge(groupId:UUID,gameId:UUID,memberId:UUID,guestSeq:Int,actorId:UUID,now:Instant){}
    /** Dá baixa manual numa diária pendente (Recebi na mesma transação); a recebida, dispensada ou paga pelo app volta como está. */
    fun settleGameCharge(charge:Charge,method:PaidMethod,actorId:UUID,at:Instant):Charge=charge
    fun findGameCharge(groupId:UUID,gameId:UUID,memberId:UUID,guestSeq:Int):Charge?=null
}
class ChargeTransactions(private val transaction:TransactionRunner,private val repository:ChargeTransactionRepository,private val now:()->Instant,private val writeAccess:GroupWriteAccess){
    constructor(transaction:TransactionRunner,repository:ChargeTransactionRepository,now:()->Instant):this(transaction,repository,now,GroupWriteAccess.Unrestricted)
    fun attendance(input:GameChargeInput,actorId:UUID):Charge?=transaction.inTransaction{
        writeAccess.requireWrite(input.groupId)
        val billed=input.outcome in setOf(AttendanceBillingOutcome.CONFIRMED,AttendanceBillingOutcome.PROMOTED)||(input.advancePayment&&input.outcome==AttendanceBillingOutcome.WAITLISTED)
        if(!billed||input.gameFeeCents==null)return@inTransaction null
        if(!repository.billsGameCharge(input.groupId,input.memberId,input.guestSeq))return@inTransaction null
        repository.createGameCharge(input,actorId,now())
    }
    /**
     * Diária retroativa (FIN-016): a mesma inserção de sempre (uma por pessoa e jogo, valor do atleta com recebimento
     * ativo), sem conferir presença nem vínculo, que quem chama já conferiu. Com [paid], nasce recebida em [paidAt].
     */
    fun retroactive(input:GameChargeInput,actorId:UUID,paid:PaidMethod?,paidAt:Instant):Charge=transaction.inTransaction{
        writeAccess.requireWrite(input.groupId)
        val charge=repository.createGameCharge(input,actorId,now())
        if(paid==null)charge else repository.settleGameCharge(charge,paid,actorId,paidAt)
    }
    /** Pagamento antecipado (PROM-010): dá baixa na diária do jogo (Colocar, já pagou), na transação de quem chama. Sem diária, nada. */
    fun settleGame(groupId:UUID,gameId:UUID,memberId:UUID,guestSeq:Int,method:PaidMethod,actorId:UUID):Charge?=transaction.inTransaction{
        writeAccess.requireWrite(groupId)
        repository.findGameCharge(groupId,gameId,memberId,guestSeq)?.let{repository.settleGameCharge(it,method,actorId,now())}
    }
    fun cancelGame(groupId:UUID,gameId:UUID,actorId:UUID)=transaction.inTransaction{writeAccess.requireWrite(groupId);repository.reconcileGameCancellation(groupId,gameId,actorId,now())}
    fun cancelGuest(groupId:UUID,gameId:UUID,memberId:UUID,guestSeq:Int,actorId:UUID)=transaction.inTransaction{
        require(guestSeq>0);writeAccess.requireWrite(groupId);repository.cancelGuestCharge(groupId,gameId,memberId,guestSeq,actorId,now())
    }
    fun generate(command:MonthlyGenerationCommand):MonthlyGenerationResult=transaction.inTransaction{
        writeAccess.requireWrite(command.groupId)
        val fields=buildSet{if(command.amountCents !in 1..99_999_999)add("amountCents");if(command.dueDate.month!=command.month.month||command.dueDate.year!=command.month.year)add("dueDate");if(command.selectedMemberIds.isEmpty())add("memberIds")}
        if(fields.isNotEmpty())return@inTransaction MonthlyGenerationResult.Invalid(fields)
        val members=repository.members(command.groupId)?:return@inTransaction MonthlyGenerationResult.Hidden
        if(!members.all.containsAll(command.selectedMemberIds))return@inTransaction MonthlyGenerationResult.Invalid(setOf("memberIds"))
        val (active,inactive)=command.selectedMemberIds.filterNot{it in members.exempt}.partition{it in members.active}
        MonthlyGenerationResult.Success(active.sortedBy(UUID::toString).map{repository.createMonthlyCharge(command,it,now())},inactive.size)
    }
}
