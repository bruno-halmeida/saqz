package br.com.saqz.groups.application.finance.charge

import br.com.saqz.groups.domain.finance.charge.Charge
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class MonthlyDueMembership(val groupId:UUID,val ownerId:UUID,val memberId:UUID,val amountCents:Long,val dueDay:Int)
fun interface MonthlyDueMembershipRepository{fun dueUpTo(dayOfMonth:Int):List<MonthlyDueMembership>}
/**
 * Gera as mensalidades ja vencidas no mes corrente. So a geracao e automatica: a charge nasce
 * PENDING e a cobranca continua manual. O organizador ainda pode disparar o mesmo caminho por
 * POST /charges/monthly.
 */
class MonthlyChargeSchedule(private val repository:MonthlyDueMembershipRepository,private val charges:ChargeTransactions,private val today:()->LocalDate,private val onSkipped:(MonthlyDueMembership,String)->Unit){
    fun run():List<Charge>{
        val date=today();val month=YearMonth.from(date)
        // Varre o mes inteiro ate hoje, nao so o dia. Um dia em que a aplicacao esteve fora do ar
        // — ou a estreia da feature no meio do mes — deixaria um vencimento sem charge para sempre,
        // e ninguem veria o buraco no caixa. Quem ja tem charge do mes cai no indice
        // uq_group_charges_month_member como no-op, entao a varredura repetida nao duplica nada.
        // ponytail: sem cursor persistido de execucao. O indice ja e o estado, e a virada de mes
        // extrema fica com o POST /charges/monthly manual.
        return repository.dueUpTo(date.dayOfMonth).flatMap{membership->
            // ponytail: uma transacao por membro. Um grupo quebrado nao impede a geracao dos outros.
            val result=runCatching{charges.generate(command(membership,month))}
            when(val value=result.getOrNull()){
                is MonthlyGenerationResult.Success->value.charges
                else->{onSkipped(membership,result.exceptionOrNull()?.message ?: value.toString());emptyList()}
            }
        }
    }
    // A charge de quem vencia dia 5 nasce vencida em 5, mesmo gerada dia 20.
    private fun command(membership:MonthlyDueMembership,month:YearMonth)=MonthlyGenerationCommand(UUID.randomUUID(),membership.groupId,membership.ownerId,month,membership.amountCents,month.atDay(membership.dueDay),setOf(membership.memberId))
}
