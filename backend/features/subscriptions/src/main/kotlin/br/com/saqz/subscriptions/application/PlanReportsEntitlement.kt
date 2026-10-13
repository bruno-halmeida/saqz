package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.ReportsEntitlement
import java.util.UUID

/** Relatórios seguem o plano entitulador do dono (`Plan.reports`); sem assinatura vigente, não há relatórios. */
class PlanReportsEntitlement(private val lookup: SubscriptionPlanLookup) : ReportsEntitlement {
    override fun isEnabledFor(ownerId: UUID): Boolean = lookup.findEntitlingPlan(ownerId)?.plan?.reports == true
}
