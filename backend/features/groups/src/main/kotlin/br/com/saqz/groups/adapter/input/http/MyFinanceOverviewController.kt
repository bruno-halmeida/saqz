package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.finance.overview.FinanceOverviewQuery
import br.com.saqz.groups.application.finance.overview.FinanceOverviewQueryResult
import br.com.saqz.groups.application.finance.overview.FinanceOverviewReadModel
import br.com.saqz.groups.application.finance.overview.FinanceOverviewTransaction
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class FinanceOverviewPeriodResponse(
    val month: String?,
    val year: Int?,
)

data class FinanceOverviewTotalsResponse(
    val balanceCents: Long,
    val inCents: Long,
    val outCents: Long,
    val pendingCents: Long,
)

data class FinanceOverviewGroupResponse(
    val id: UUID,
    val name: String,
    val balanceCents: Long,
    val pendingMonthlyCount: Int,
    val hasBillingConfigured: Boolean,
)

data class FinanceOverviewTransactionResponse(
    val id: UUID,
    val groupId: UUID,
    val groupName: String,
    val kind: String,
    val direction: String?,
    val memberName: String?,
    val description: String?,
    val amountCents: Long,
    val occurredAt: Instant,
)

data class FinanceOverviewResponse(
    val period: FinanceOverviewPeriodResponse,
    val totals: FinanceOverviewTotalsResponse,
    val groups: List<FinanceOverviewGroupResponse>,
    val recentTransactions: List<FinanceOverviewTransactionResponse>,
)

@RestController
class MyFinanceOverviewController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val query: FinanceOverviewQuery,
) {
    @GetMapping("/api/me/finance/overview")
    fun overview(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestParam("month", required = false) month: String?,
        @RequestParam("year", required = false) year: String?,
    ): FinanceOverviewResponse {
        val actor = actorResolver.resolve(identity)
        return when (val result = query.execute(actor, month, year)) {
            is FinanceOverviewQueryResult.Success -> result.value.toResponse()
            is FinanceOverviewQueryResult.Invalid -> throw InvalidGroupRequestException(result.fieldErrors)
        }
    }
}

private fun FinanceOverviewReadModel.toResponse() = FinanceOverviewResponse(
    period = FinanceOverviewPeriodResponse(period.month, period.year),
    totals = FinanceOverviewTotalsResponse(
        balanceCents = totals.balanceCents,
        inCents = totals.inCents,
        outCents = totals.outCents,
        pendingCents = totals.pendingCents,
    ),
    groups = groups.map {
        FinanceOverviewGroupResponse(
            id = it.id,
            name = it.name,
            balanceCents = it.balanceCents,
            pendingMonthlyCount = it.pendingMonthlyCount,
            hasBillingConfigured = it.hasBillingConfigured,
        )
    },
    recentTransactions = recentTransactions.map(FinanceOverviewTransaction::toResponse),
)

private fun FinanceOverviewTransaction.toResponse() = FinanceOverviewTransactionResponse(
    id = id,
    groupId = groupId,
    groupName = groupName,
    kind = kind.name,
    direction = direction?.name,
    memberName = memberName,
    description = description,
    amountCents = amountCents,
    occurredAt = occurredAt,
)
