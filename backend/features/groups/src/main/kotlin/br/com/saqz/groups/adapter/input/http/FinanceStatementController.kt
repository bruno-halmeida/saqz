package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.finance.statement.FinanceStatementDirection
import br.com.saqz.groups.application.finance.statement.FinanceStatementItem
import br.com.saqz.groups.application.finance.statement.FinanceStatementPage
import br.com.saqz.groups.application.finance.statement.FinanceStatementResult
import br.com.saqz.groups.application.finance.statement.FinanceStatementService
import br.com.saqz.groups.application.finance.statement.FinanceStatementSummary
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth
import java.util.UUID

data class FinanceStatementItemResponse(
    val id: UUID,
    val type: String,
    val direction: String,
    val title: String,
    val category: String,
    val paidMethod: String?,
    val occurredAt: java.time.Instant,
    val amountCents: Long,
)

data class FinanceStatementSummaryResponse(
    val totalInCents: Long,
    val totalOutCents: Long,
    val periodBalanceCents: Long,
    val accumulatedBalanceCents: Long,
)

data class FinanceStatementResponse(
    val month: String,
    val items: List<FinanceStatementItemResponse>,
    val summary: FinanceStatementSummaryResponse,
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean,
)

@RestController
class FinanceStatementController(
    private val actors: VerifiedGroupActorResolver,
    private val service: FinanceStatementService,
) {
    @GetMapping("/api/groups/{groupId}/finance/statement")
    fun list(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @RequestParam(required = false) month: String? = null,
        @RequestParam(required = false) direction: String? = null,
        @RequestParam(defaultValue = "20") limit: Int = 20,
        @RequestParam(defaultValue = "0") offset: Int = 0,
    ): FinanceStatementResponse {
        if (limit <= 0) invalid("limit")
        if (offset < 0) invalid("offset")
        val parsedMonth = month?.let(::parseMonth)
        val parsedDirection = direction?.let(::parseDirection)
        return when (val result = service.list(
                actorId = actors.resolve(identity),
                groupId = uuid(groupId),
                month = parsedMonth,
                direction = parsedDirection,
                limit = limit,
                offset = offset,
            )) {
            is FinanceStatementResult.Success -> result.value.response()
            FinanceStatementResult.Hidden -> throw GameNotFoundException()
            FinanceStatementResult.Forbidden -> throw AccessForbiddenException()
        }
    }

    private fun parseMonth(value: String): YearMonth {
        if (!Regex("[0-9]{4}-[0-9]{2}").matches(value)) invalid("month")
        return runCatching { YearMonth.parse(value) }.getOrElse { invalid("month") }
    }

    private fun parseDirection(value: String): FinanceStatementDirection =
        runCatching { FinanceStatementDirection.valueOf(value) }.getOrElse { invalid("direction") }

    private fun uuid(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrElse { throw GameNotFoundException() }

    private fun invalid(field: String): Nothing =
        throw InvalidGroupRequestException(mapOf(field to listOf("is required or invalid")))
}

private fun FinanceStatementPage.response() = FinanceStatementResponse(
    month = month.toString(),
    items = items.map(FinanceStatementItem::response),
    summary = summary.response(),
    limit = limit,
    offset = offset,
    hasMore = hasMore,
)

private fun FinanceStatementItem.response() = FinanceStatementItemResponse(
    id = id,
    type = type.name,
    direction = direction.name,
    title = title,
    category = category,
    paidMethod = paidMethod,
    occurredAt = occurredAt,
    amountCents = amountCents,
)

private fun FinanceStatementSummary.response() = FinanceStatementSummaryResponse(
    totalInCents = totalInCents,
    totalOutCents = totalOutCents,
    periodBalanceCents = periodBalanceCents,
    accumulatedBalanceCents = accumulatedBalanceCents,
)
