package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan

data class PlanCatalogItem(
    val id: Plan,
    val name: String,
    val monthlyPriceCents: Long,
    val annualPriceCents: Long,
    val maxGroups: Int?,
    val maxAthletes: Int?,
    val multiAdmin: Boolean,
    val reports: Boolean,
    val whatsappSla: Boolean,
)

class ListPlans {
    fun execute(): List<PlanCatalogItem> = Plan.entries.map { plan ->
        PlanCatalogItem(
            id = plan,
            name = plan.name,
            monthlyPriceCents = plan.monthlyPriceCents,
            annualPriceCents = plan.annualPriceCents,
            maxGroups = plan.maxGroups,
            maxAthletes = plan.maxAthletes,
            multiAdmin = plan.multiAdmin,
            reports = plan.reports,
            whatsappSla = plan.whatsappSla,
        )
    }
}
