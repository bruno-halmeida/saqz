package br.com.saqz.groups.data.finance

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.finance.*
import br.com.saqz.network.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal enum class ChargeKindTransport { GAME, MONTHLY }

@Serializable
internal enum class ChargeStatusTransport { PENDING, PAID, WAIVED, CANCELLED }

@Serializable
internal enum class ExpenseStatusTransport { ACTIVE, VOIDED }

@Serializable
internal enum class ExpenseActionTransport { CREATED, EDITED, VOIDED }

@Serializable
internal data class ChargeAuditTransport(
    val actorId: String,
    val oldStatus: ChargeStatusTransport,
    val newStatus: ChargeStatusTransport,
    val note: String? = null,
    val occurredAt: String,
)

@Serializable
internal data class ChargeTransport(
    val id: String,
    val groupId: String,
    val memberId: String,
    val kind: ChargeKindTransport,
    val gameId: String? = null,
    val month: String? = null,
    val amountCents: Long,
    val dueDate: String,
    val status: ChargeStatusTransport,
    val reviewRequired: Boolean = false,
    val version: Long,
    val events: List<ChargeAuditTransport>,
    val paidMethod: String? = null,
)

@Serializable
internal data class ChargeListTransport(
    val charges: List<ChargeTransport>,
    val pendingTotalCents: Long? = null,
    val paidTotalCents: Long? = null,
    val waivedTotalCents: Long? = null,
    val cancelledTotalCents: Long? = null,
)

@Serializable
private data class MonthlyChargeRequest(
    val requestId: String,
    val month: String,
    val amountCents: Long,
    val dueDate: String,
    val memberIds: Set<String>,
)

@Serializable
private data class ChargeStatusRequest(
    val status: ChargeStatusTransport,
    val note: String? = null,
    val paidMethod: String? = null,
)

@Serializable
internal data class ExpenseAuditTransport(
    val actorId: String,
    val action: ExpenseActionTransport,
    val occurredAt: String,
)

@Serializable
internal data class ExpenseTransport(
    val id: String,
    val groupId: String,
    val description: String,
    val amountCents: Long,
    val expenseDate: String,
    val category: String,
    val customCategory: String? = null,
    val notes: String? = null,
    val status: ExpenseStatusTransport,
    val version: Long,
    val events: List<ExpenseAuditTransport>,
    val direction: String = "OUT",
)

@Serializable
internal data class ExpenseListTransport(
    val expenses: List<ExpenseTransport>,
    val activeExpenseTotalCents: Long,
)

@Serializable
private data class ExpenseWriteRequest(
    val requestId: String? = null,
    val description: String,
    val amountCents: Long,
    val expenseDate: String,
    val category: String,
    val customCategory: String? = null,
    val notes: String? = null,
    val direction: String? = null,
)

@Serializable
internal data class FinanceTotalsTransport(
    val pendingChargeCents: Long,
    val paidChargeCents: Long,
    val waivedChargeCents: Long,
    val cancelledChargeCents: Long,
    val activeExpenseCents: Long,
)

@Serializable
private data class FinanceStatementItemTransport(
    val id: String,
    val type: String,
    val direction: String,
    val title: String,
    val category: String,
    val paidMethod: String? = null,
    val occurredAt: String,
    val amountCents: Long,
)

@Serializable
private data class FinanceStatementSummaryTransport(
    val totalInCents: Long,
    val totalOutCents: Long,
    val periodBalanceCents: Long,
    val accumulatedBalanceCents: Long,
)

@Serializable
private data class FinanceStatementPageTransport(
    val month: String,
    val items: List<FinanceStatementItemTransport>,
    val summary: FinanceStatementSummaryTransport,
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean,
)

@Serializable
private data class FinanceOverviewPeriodTransport(
    val month: String? = null,
    val year: Int? = null,
)

@Serializable
private data class FinanceOverviewTotalsTransport(
    val balanceCents: Long,
    val inCents: Long,
    val outCents: Long,
    val pendingCents: Long,
)

@Serializable
private data class FinanceOverviewGroupTransport(
    val id: String,
    val name: String,
    val balanceCents: Long,
    val pendingMonthlyCount: Int,
    val hasBillingConfigured: Boolean,
)

@Serializable
private data class FinanceOverviewTransactionTransport(
    val id: String,
    val groupId: String,
    val groupName: String,
    val kind: String,
    val direction: String? = null,
    val memberName: String? = null,
    val description: String? = null,
    val amountCents: Long,
    val occurredAt: String,
)

@Serializable
private data class FinanceOverviewTransport(
    val period: FinanceOverviewPeriodTransport,
    val totals: FinanceOverviewTotalsTransport,
    val groups: List<FinanceOverviewGroupTransport>,
    val recentTransactions: List<FinanceOverviewTransactionTransport>,
)

class KtorAthleteFinanceGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : AthleteFinanceGateway {
    override suspend fun ownCharges(groupId: GroupId) =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "api/groups/${groupId.value}/charges/me",
                ChargeListTransport.serializer(),
            )
        }.toChargeListResult()
}

class KtorFinanceStatementGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : FinanceStatementGateway {
    override suspend fun statement(groupId: GroupId, query: FinanceStatementQuery) =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "api/groups/${groupId.value}/finance/statement",
                FinanceStatementPageTransport.serializer(),
                NetworkRequest(query = query.toNetworkQuery()),
            )
        }.toFinanceStatementResult()
}

class KtorFinanceOverviewGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : FinanceOverviewGateway {
    override suspend fun overview(query: FinanceOverviewQuery) =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "api/me/finance/overview",
                FinanceOverviewTransport.serializer(),
                NetworkRequest(query = query.toNetworkQuery()),
            )
        }.toFinanceOverviewResult()
}

class KtorOrganizerFinanceGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : OrganizerFinanceGateway {
    private val json = Json { explicitNulls = false }

    override suspend fun charges(groupId: GroupId) = read(
        "api/groups/${groupId.value}/charges",
        ChargeListTransport.serializer(),
    ).toChargeListResult()

    override suspend fun generateMonthly(groupId: GroupId, command: MonthlyChargeCommand) =
        retryTransport(command.requestId.safety(), delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "api/groups/${groupId.value}/charges/monthly",
                ChargeListTransport.serializer(),
                NetworkRequest(json.encodeToString(command.toRequest())),
            )
        }.toChargeListResult()

    override suspend fun updateChargeStatus(
        groupId: GroupId,
        chargeId: String,
        version: FinanceVersionToken,
        command: ChargeStatusCommand,
    ) = network.execute(
        HttpMethod.Post,
        "api/groups/${groupId.value}/charges/$chargeId/status",
        ChargeTransport.serializer(),
        NetworkRequest(
            json.encodeToString(command.toRequest()),
            mapOf(HttpHeaders.IfMatch to version.value),
        ),
    ).toVersionedChargeResult()

    override suspend fun expenses(groupId: GroupId) = read(
        "api/groups/${groupId.value}/expenses",
        ExpenseListTransport.serializer(),
    ).toExpenseListResult()

    override suspend fun createExpense(groupId: GroupId, command: ExpenseWriteCommand) =
        mutateExpense(
            method = HttpMethod.Post,
            route = "api/groups/${groupId.value}/expenses",
            request = NetworkRequest(json.encodeToString(command.toRequest())),
            safety = command.requestId.safety(),
        )

    override suspend fun editExpense(
        groupId: GroupId,
        expenseId: String,
        version: FinanceVersionToken,
        command: ExpenseWriteCommand,
    ) = mutateExpense(
        method = HttpMethod.Put,
        route = "api/groups/${groupId.value}/expenses/$expenseId",
        request = NetworkRequest(
            json.encodeToString(command.toRequest()),
            mapOf(HttpHeaders.IfMatch to version.value),
        ),
        safety = command.requestId.safety(),
    )

    override suspend fun voidExpense(
        groupId: GroupId,
        expenseId: String,
        version: FinanceVersionToken,
    ) = network.execute(
        HttpMethod.Post,
        "api/groups/${groupId.value}/expenses/$expenseId/void",
        ExpenseTransport.serializer(),
        NetworkRequest(headers = mapOf(HttpHeaders.IfMatch to version.value)),
    ).toVersionedExpenseResult()

    override suspend fun totals(groupId: GroupId) = read(
        "api/groups/${groupId.value}/finance/totals",
        FinanceTotalsTransport.serializer(),
    ).toFinanceTotalsResult()

    private suspend fun <T> read(
        route: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): NetworkResult<T> = retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
        network.execute(HttpMethod.Get, route, serializer)
    }

    private suspend fun mutateExpense(
        method: HttpMethod,
        route: String,
        request: NetworkRequest,
        safety: RetrySafety,
    ) = retryTransport(safety, delayMillis = retryDelay) {
        network.execute(method, route, ExpenseTransport.serializer(), request)
    }.toVersionedExpenseResult()
}

private fun String?.safety() =
    if (isNullOrBlank()) RetrySafety.Never else RetrySafety.IdempotentWrite

private fun MonthlyChargeCommand.toRequest() = MonthlyChargeRequest(
    requestId = requestId,
    month = month,
    amountCents = amountCents,
    dueDate = dueDate,
    memberIds = memberIds,
)

private fun ExpenseWriteCommand.toRequest() = ExpenseWriteRequest(
    requestId = requestId,
    description = description,
    amountCents = amountCents,
    expenseDate = expenseDate,
    category = category.toTransport(),
    customCategory = customCategory,
    notes = notes,
    direction = direction?.toTransport(),
)

private fun ChargeStatusCommand.toRequest() = ChargeStatusRequest(
    status = status.toTransport(),
    note = note,
    paidMethod = paidMethod?.toTransport(),
)

private fun FinanceStatementQuery.toNetworkQuery() = buildMap {
    month?.let { put("month", it) }
    direction?.let { put("direction", it.toTransport()) }
    put("limit", limit.toString())
    put("offset", offset.toString())
}

private fun FinanceOverviewQuery.toNetworkQuery() = buildMap {
    month?.let { put("month", it) }
    year?.let { put("year", it.toString()) }
}

private fun NetworkResult<ChargeListTransport>.toChargeListResult() = mapFinance { it.toDomain() }

private fun NetworkResult<ExpenseListTransport>.toExpenseListResult() = mapFinance { it.toDomain() }

private fun NetworkResult<FinanceTotalsTransport>.toFinanceTotalsResult() = mapFinance { it.toDomain() }

private fun NetworkResult<FinanceStatementPageTransport>.toFinanceStatementResult() =
    mapFinance { it.toDomain() }

private fun NetworkResult<FinanceOverviewTransport>.toFinanceOverviewResult() =
    mapFinance { it.toDomain() }

private fun NetworkResult<ChargeTransport>.toVersionedChargeResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toFinanceError())
    is NetworkResult.Success -> metadata.header(HttpHeaders.ETag)
        ?.let { SaqzResult.Success(VersionedCharge(value.toDomain(), FinanceVersionToken(it))) }
        ?: invalidResponse()
}

private fun NetworkResult<ExpenseTransport>.toVersionedExpenseResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toFinanceError())
    is NetworkResult.Success -> metadata.header(HttpHeaders.ETag)
        ?.let { SaqzResult.Success(VersionedExpense(value.toDomain(), FinanceVersionToken(it))) }
        ?: invalidResponse()
}

private inline fun <T, R> NetworkResult<T>.mapFinance(mapper: (T) -> R): SaqzResult<R, FinanceError> =
    when (this) {
        is NetworkResult.Failure -> SaqzResult.Failure(error.toFinanceError())
        is NetworkResult.Success -> SaqzResult.Success(mapper(value))
    }

private fun ChargeListTransport.toDomain() = ChargeList(
    charges = charges.map(ChargeTransport::toDomain),
    totals = if (
        pendingTotalCents != null && paidTotalCents != null &&
        waivedTotalCents != null && cancelledTotalCents != null
    ) {
        ChargeTotals(pendingTotalCents, paidTotalCents, waivedTotalCents, cancelledTotalCents)
    } else {
        null
    },
)

private fun ChargeTransport.toDomain() = Charge(
    id, GroupId(groupId), memberId, kind.toDomain(), gameId, month, amountCents,
    dueDate, status.toDomain(), reviewRequired, version, events.map(ChargeAuditTransport::toDomain),
    paidMethod.toPaidMethod(),
)

private fun ChargeAuditTransport.toDomain() =
    ChargeAudit(actorId, oldStatus.toDomain(), newStatus.toDomain(), note, occurredAt)

private fun ExpenseListTransport.toDomain() =
    ExpenseList(expenses.map(ExpenseTransport::toDomain), activeExpenseTotalCents)

private fun ExpenseTransport.toDomain() = Expense(
    id, GroupId(groupId), description, amountCents, expenseDate, category.toExpenseCategory(),
    customCategory, notes, status.toDomain(), version, events.map(ExpenseAuditTransport::toDomain),
    direction.toFinanceDirection(),
)

private fun FinanceStatementPageTransport.toDomain() = FinanceStatementPage(
    month = month,
    items = items.map(FinanceStatementItemTransport::toDomain),
    summary = summary.toDomain(),
    limit = limit,
    offset = offset,
    hasMore = hasMore,
)

private fun FinanceStatementItemTransport.toDomain() = FinanceStatementItem(
    id = id,
    type = type,
    direction = direction.toFinanceDirection(),
    title = title,
    category = category,
    paidMethod = paidMethod.toPaidMethod(),
    occurredAt = occurredAt,
    amountCents = amountCents,
)

private fun FinanceStatementSummaryTransport.toDomain() = FinanceStatementSummary(
    totalInCents = totalInCents,
    totalOutCents = totalOutCents,
    periodBalanceCents = periodBalanceCents,
    accumulatedBalanceCents = accumulatedBalanceCents,
)

private fun FinanceOverviewTransport.toDomain() = FinanceOverview(
    period = FinanceOverviewPeriod(period.month, period.year),
    totals = FinanceOverviewTotals(
        balanceCents = totals.balanceCents,
        inCents = totals.inCents,
        outCents = totals.outCents,
        pendingCents = totals.pendingCents,
    ),
    groups = groups.map {
        FinanceOverviewGroup(
            id = it.id,
            name = it.name,
            balanceCents = it.balanceCents,
            status = FinanceOverviewGroupStatus(
                pendingMonthlyCount = it.pendingMonthlyCount,
                hasBillingConfigured = it.hasBillingConfigured,
            ),
        )
    },
    recentTransactions = recentTransactions.map {
        FinanceOverviewTransaction(
            id = it.id,
            groupId = it.groupId,
            groupName = it.groupName,
            kind = it.kind,
            direction = it.direction?.toFinanceDirection(),
            memberName = it.memberName,
            description = it.description,
            amountCents = it.amountCents,
            occurredAt = it.occurredAt,
        )
    },
)

private fun ExpenseAuditTransport.toDomain() = ExpenseAudit(actorId, action.toDomain(), occurredAt)

private fun FinanceTotalsTransport.toDomain() = FinanceTotals(
    pendingChargeCents, paidChargeCents, waivedChargeCents, cancelledChargeCents, activeExpenseCents,
)

private fun ChargeKindTransport.toDomain() = when (this) {
    ChargeKindTransport.GAME -> ChargeKind.Game
    ChargeKindTransport.MONTHLY -> ChargeKind.Monthly
}

private fun ChargeStatusTransport.toDomain() = when (this) {
    ChargeStatusTransport.PENDING -> ChargeStatus.Pending
    ChargeStatusTransport.PAID -> ChargeStatus.Paid
    ChargeStatusTransport.WAIVED -> ChargeStatus.Waived
    ChargeStatusTransport.CANCELLED -> ChargeStatus.Cancelled
}

private fun ChargeStatus.toTransport() = when (this) {
    ChargeStatus.Pending -> ChargeStatusTransport.PENDING
    ChargeStatus.Paid -> ChargeStatusTransport.PAID
    ChargeStatus.Waived -> ChargeStatusTransport.WAIVED
    ChargeStatus.Cancelled -> ChargeStatusTransport.CANCELLED
}

private fun String.toFinanceDirection() = when (uppercase()) {
    "IN" -> FinanceDirection.In
    else -> FinanceDirection.Out
}

private fun String.toExpenseCategory() = when (uppercase()) {
    "VENUE" -> ExpenseCategory.Venue
    "EQUIPMENT" -> ExpenseCategory.Equipment
    "REFEREE" -> ExpenseCategory.Referee
    "RACHA" -> ExpenseCategory.Racha
    else -> ExpenseCategory.Other
}

private fun ExpenseCategory.toTransport() = when (this) {
    ExpenseCategory.Venue -> "VENUE"
    ExpenseCategory.Equipment -> "EQUIPMENT"
    ExpenseCategory.Referee -> "REFEREE"
    ExpenseCategory.Other -> "OTHER"
    ExpenseCategory.Racha -> "RACHA"
}

private fun FinanceDirection.toTransport() = when (this) {
    FinanceDirection.In -> "IN"
    FinanceDirection.Out -> "OUT"
}

private fun PaidMethod.toTransport() = when (this) {
    PaidMethod.Pix -> "PIX"
    PaidMethod.Cash -> "CASH"
    PaidMethod.Other -> "OTHER"
}

private fun String?.toPaidMethod(): PaidMethod? = when (this?.uppercase()) {
    null -> null
    "PIX" -> PaidMethod.Pix
    "CASH" -> PaidMethod.Cash
    else -> PaidMethod.Other
}

private fun ExpenseStatusTransport.toDomain() = when (this) {
    ExpenseStatusTransport.ACTIVE -> ExpenseStatus.Active
    ExpenseStatusTransport.VOIDED -> ExpenseStatus.Voided
}

private fun ExpenseActionTransport.toDomain() = when (this) {
    ExpenseActionTransport.CREATED -> ExpenseAction.Created
    ExpenseActionTransport.EDITED -> ExpenseAction.Edited
    ExpenseActionTransport.VOIDED -> ExpenseAction.Voided
}

internal fun NetworkError.toFinanceError(): FinanceError = when (this) {
    is NetworkError.ApiProblemError -> when (problem.code) {
        "VALIDATION_FAILED" -> FinanceError.Validation(
            DataError.Validation(
                ValidationDetails(
                    globalMessages = emptyList(),
                    fieldMessages = problem.fieldErrors.orEmpty(),
                ),
            ),
        )
        "GROUP_NOT_FOUND", "GAME_NOT_FOUND" -> FinanceError.HiddenResource
        "ACCESS_FORBIDDEN" -> FinanceError.Forbidden
        "VERSION_CONFLICT" -> FinanceError.Conflict
        "PRECONDITION_REQUIRED" -> FinanceError.PreconditionRequired
        "INVALID_GAME_TRANSITION" -> FinanceError.InvalidLifecycle
        "AUTHENTICATION_REQUIRED" -> FinanceError.Authentication
        else -> FinanceError.Data(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> FinanceError.Data(status.toDataError())
    NetworkError.Timeout -> FinanceError.Data(DataError.Timeout)
    NetworkError.Connectivity -> FinanceError.Data(DataError.Connectivity)
    NetworkError.InvalidResponse -> FinanceError.Data(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> FinanceError.Data(DataError.PayloadTooLarge)
    NetworkError.Unavailable -> FinanceError.Data(DataError.Server)
    NetworkError.Unknown -> FinanceError.Data(DataError.Unknown)
}

private fun Int.toDataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}

private fun <T> invalidResponse(): SaqzResult<T, FinanceError> =
    SaqzResult.Failure(FinanceError.Data(DataError.InvalidResponse))
