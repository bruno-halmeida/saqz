package br.com.saqz.groups.application.finance.charge

import br.com.saqz.groups.domain.finance.charge.Charge
import br.com.saqz.groups.domain.group.MonthlyLateJoinPolicy
import br.com.saqz.sharedkernel.job.JobLog
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class MonthlyDueMembership(val groupId:UUID,val ownerId:UUID,val memberId:UUID,val amountCents:Long,val dueDay:Int,val lateJoinPolicy:MonthlyLateJoinPolicy=MonthlyLateJoinPolicy.GROUP_DUE_DATE,val mensalistaSince:LocalDate?=null)
fun interface MonthlyDueMembershipRepository{fun dueUpTo(dayOfMonth:Int):List<MonthlyDueMembership>}
/**
 * Gera as mensalidades ja vencidas no mes corrente. So a geracao e automatica: a charge nasce
 * PENDING e a cobranca continua manual. O organizador ainda pode disparar o mesmo caminho por
 * POST /charges/monthly.
 *
 * A politica do grupo (MonthlyLateJoinPolicy) so mexe no mes em que a pessoa virou mensalista, e
 * so quando isso foi depois do dia de vencimento dela. O POST /charges/monthly manual NAO passa
 * por ela: e escolha explicita do gestor, com o vencimento que ele informar.
 *
 * JOIN_DATE nunca gera uma charge ja atrasada: o job roda de madrugada, entao quem vira mensalista
 * as 15h do dia X recebe a charge no dia X+1 (ou depois, com o job fora do ar), e um vencimento em X
 * apareceria em atraso na Inicio. O vencimento e o mais tarde entre o dia em que a pessoa entrou e
 * o dia da geracao: "vence no dia em que foi gerada".
 */
class MonthlyChargeSchedule(private val repository:MonthlyDueMembershipRepository,private val charges:ChargeTransactions,private val today:()->LocalDate,private val onSkipped:(MonthlyDueMembership,String)->Unit){
    fun run():List<Charge>{
        var candidates=0;var skipped=0
        // Cron das 03h10: uma linha job_run a cada execucao, mesmo sem candidato; um job_item por mensalidade gerada.
        return JobLog.cron("monthly_charges",{
            val date=today();val month=YearMonth.from(date)
            // Varre o mes inteiro ate hoje, nao so o dia. Um dia em que a aplicacao esteve fora do ar
            // — ou a estreia da feature no meio do mes — deixaria um vencimento sem charge para sempre,
            // e ninguem veria o buraco no caixa. Quem ja tem charge do mes cai no indice
            // uq_group_charges_month_member como no-op, entao a varredura repetida nao duplica nada.
            // ponytail: sem cursor persistido de execucao. O indice ja e o estado, e a virada de mes
            // extrema fica com o POST /charges/monthly manual.
            val due=repository.dueUpTo(date.dayOfMonth)
            candidates=due.size
            due.flatMap{membership->
                val dueDate=firstDueDate(membership,month,date) ?: return@flatMap emptyList()
                // ponytail: uma transacao por membro. Um grupo quebrado nao impede a geracao dos outros.
                val result=runCatching{charges.generate(command(membership,month,dueDate))}
                when(val value=result.getOrNull()){
                    is MonthlyGenerationResult.Success->value.charges.also{generated->generated.forEach{JobLog.item("monthly_charges","charge_generated","groupId" to it.groupId,"memberId" to it.memberId,"chargeId" to it.id)}}
                    else->{
                        skipped++
                        JobLog.itemFailed("monthly_charges","charge_skipped",result.exceptionOrNull(),"groupId" to membership.groupId,"memberId" to membership.memberId)
                        onSkipped(membership,result.exceptionOrNull()?.message ?: value.toString());emptyList()
                    }
                }
            }
        },{mapOf("candidates" to candidates,"generated" to it.size,"skipped" to skipped)})
    }
    // A charge de quem vencia dia 5 nasce vencida em 5, mesmo gerada dia 20 — salvo quem virou
    // mensalista neste mes depois do dia 5, que segue a politica do grupo. Nulo = nao cobra neste mes.
    private fun firstDueDate(membership:MonthlyDueMembership,month:YearMonth,generatedOn:LocalDate):LocalDate?{
        val groupDueDate=month.atDay(membership.dueDay)
        val since=membership.mensalistaSince ?: return groupDueDate
        val joinedAfterDueDay=YearMonth.from(since)==month&&since.isAfter(groupDueDate)
        if(!joinedAfterDueDay) return groupDueDate
        return when(membership.lateJoinPolicy){
            MonthlyLateJoinPolicy.GROUP_DUE_DATE->groupDueDate
            // Nunca no passado: vence no dia da geracao, que e o dia em que entrou so se o job rodou nele.
            MonthlyLateJoinPolicy.JOIN_DATE->maxOf(since,generatedOn)
            MonthlyLateJoinPolicy.NEXT_MONTH->null
        }
    }
    private fun command(membership:MonthlyDueMembership,month:YearMonth,dueDate:LocalDate)=MonthlyGenerationCommand(UUID.randomUUID(),membership.groupId,membership.ownerId,month,membership.amountCents,dueDate,setOf(membership.memberId))
}
