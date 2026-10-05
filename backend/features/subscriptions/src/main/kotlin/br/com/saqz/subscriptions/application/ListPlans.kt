package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.GooglePlayProduct
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle

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
    val appStoreMonthlyProductId: String,
    val appStoreAnnualProductId: String,
    val googlePlayProductId: String,
    val googlePlayMonthlyBasePlanId: String,
    val googlePlayAnnualBasePlanId: String,
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
            appStoreMonthlyProductId = AppStoreProduct.of(plan, SubscriptionCycle.MONTHLY).productId,
            appStoreAnnualProductId = AppStoreProduct.of(plan, SubscriptionCycle.ANNUAL).productId,
            googlePlayProductId = GooglePlayProduct.of(plan, SubscriptionCycle.MONTHLY).productId,
            googlePlayMonthlyBasePlanId = GooglePlayProduct.of(plan, SubscriptionCycle.MONTHLY).basePlanId,
            googlePlayAnnualBasePlanId = GooglePlayProduct.of(plan, SubscriptionCycle.ANNUAL).basePlanId,
        )
    }
}
