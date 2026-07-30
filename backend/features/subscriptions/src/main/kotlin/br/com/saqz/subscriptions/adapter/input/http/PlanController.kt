package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.subscriptions.application.ListPlans
import br.com.saqz.subscriptions.application.PlanCatalogItem
import br.com.saqz.subscriptions.domain.Plan
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class PlanResponse(
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

@RestController
class PlanController(
    private val listPlans: ListPlans,
) {
    @GetMapping("/plans")
    fun list(): List<PlanResponse> = listPlans.execute().map { it.toResponse() }
}

private fun PlanCatalogItem.toResponse() = PlanResponse(
    id = id,
    name = name,
    monthlyPriceCents = monthlyPriceCents,
    annualPriceCents = annualPriceCents,
    maxGroups = maxGroups,
    maxAthletes = maxAthletes,
    multiAdmin = multiAdmin,
    reports = reports,
    whatsappSla = whatsappSla,
)
