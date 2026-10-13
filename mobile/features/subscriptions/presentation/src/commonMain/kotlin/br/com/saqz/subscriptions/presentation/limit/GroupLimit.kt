package br.com.saqz.subscriptions.presentation.limit

import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus

/**
 * O limite de grupos que a pessoa acabou de bater: [groupsUsed] é quanto ela já tem,
 * [groupsLimit] o teto do plano (ou do teste) e [planName] o nome dele como a tela o diz.
 * Escalares de propósito: vira argumento de rota.
 */
data class PaywallGroupLimit(val groupsUsed: Int, val groupsLimit: Int, val planName: String) {
    /** Um plano comporta o uso atual se o teto dele (nulo é sem teto) não for menor que [groupsUsed]. */
    fun fits(planMaxGroups: Int?): Boolean = planMaxGroups == null || planMaxGroups >= groupsUsed
}

/**
 * Descobre se o "+" de Grupos foi parar na assinatura por causa do limite de grupos. Nulo é
 * "não foi o limite" (sem plano, teste vencido, falha de rede): a assinatura abre como sempre.
 * Quem decide o acesso continua sendo o backend; isto só escolhe o texto.
 */
class GroupLimitResolver(
    private val subscriptions: SubscriptionGateway,
    private val trial: TrialGateway,
) {
    suspend fun resolve(): PaywallGroupLimit? {
        val subscription = (subscriptions.mySubscription() as? SaqzResult.Success)?.value
        if (subscription != null && subscription.entitled) {
            val limit = subscription.usage.groupsLimit ?: return null
            val used = subscription.usage.groupsUsed
            return if (used >= limit) PaywallGroupLimit(used, limit, subscription.plan.name) else null
        }
        val access = (trial.ownerTrial() as? SaqzResult.Success)?.value ?: return null
        val inTrial = access.status == TrialStatus.Active || access.status == TrialStatus.Available
        // Teste ativo que não deixa criar mais: o teto do teste foi atingido.
        return if (inTrial && access.isOwner && !access.canCreateGroup && access.maxGroups > 0) {
            PaywallGroupLimit(access.maxGroups, access.maxGroups, Plan.Organizador.name)
        } else {
            null
        }
    }
}
