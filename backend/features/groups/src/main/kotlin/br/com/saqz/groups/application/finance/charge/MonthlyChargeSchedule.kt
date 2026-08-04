package br.com.saqz.groups.application.finance.charge

import br.com.saqz.groups.domain.finance.charge.Charge
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class MonthlyDueMembership(val groupId:UUID,val ownerId:UUID,val memberId:UUID,val amountCents:Long)
fun interface MonthlyDueMembershipRepository{fun dueOn(dayOfMonth:Int):List<MonthlyDueMembership>}
/**
 * Gera as mensalidades do dia. So a geracao e automatica: a charge nasce PENDING e a cobranca
 * continua manual. O organizador ainda pode disparar o mesmo caminho por POST /charges/monthly.
 */
class MonthlyChargeSchedule(private val repository:MonthlyDueMembershipRepository,private val charges:ChargeTransactions,private val today:()->LocalDate,private val onSkipped:(MonthlyDueMembership,String)->Unit){
    fun run():List<Charge>{
        val date=today()
        // ponytail: uma transacao por membro. Um grupo quebrado nao impede a geracao dos outros,
        // e a repeticao no mesmo mes cai no indice uq_group_charges_month_member como no-op.
        return repository.dueOn(date.dayOfMonth).flatMap{membership->
            val result=runCatching{charges.generate(command(membership,date))}
            when(val value=result.getOrNull()){
                is MonthlyGenerationResult.Success->value.charges
                else->{onSkipped(membership,result.exceptionOrNull()?.message ?: value.toString());emptyList()}
            }
        }
    }
    private fun command(membership:MonthlyDueMembership,date:LocalDate)=MonthlyGenerationCommand(UUID.randomUUID(),membership.groupId,membership.ownerId,YearMonth.from(date),membership.amountCents,date,setOf(membership.memberId))
}
