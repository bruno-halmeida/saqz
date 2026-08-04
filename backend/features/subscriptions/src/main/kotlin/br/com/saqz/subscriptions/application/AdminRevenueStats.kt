package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import java.time.Instant
import java.time.LocalDate

data class ChurnStats(
    val canceled: Long,
    val activeAtStart: Long,
)

data class PlanSplitEntry(
    val plan: Plan,
    val subscribers: Long,
    val mrrCents: Long,
)

data class SubscribedCohortWeek(
    val weekStart: LocalDate,
    val subscribed: Long,
)

/**
 * Números administrativos de receita (adm-web · visão geral). Janelas [from, to);
 * from null significa "desde o início".
 */
interface AdminRevenueStats {
    /** Soma dos pagamentos confirmados no Asaas (eventos processados) no período. */
    fun revenueCents(from: Instant?, to: Instant): Long

    /** canceled no período; activeAtStart = assinaturas vivas no início da janela. */
    fun churn(from: Instant?, to: Instant): ChurnStats

    /** Assinaturas ACTIVE/PAST_DUE por plano, com MRR mensalizado e desconto de cupom ativo. */
    fun planSplit(): List<PlanSplitEntry>

    /** Por semana ISO de cadastro do usuário: quantos têm assinatura criada. */
    fun subscribedCohort(weeksBack: Int, now: Instant): List<SubscribedCohortWeek>
}
