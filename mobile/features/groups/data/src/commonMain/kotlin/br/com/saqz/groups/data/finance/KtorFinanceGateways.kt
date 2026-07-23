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
internal enum class ExpenseCategoryTransport { VENUE, EQUIPMENT, REFEREE, OTHER }

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
    val category: ExpenseCategoryTransport,
    val customCategory: String? = null,
    val notes: String? = null,
    val status: ExpenseStatusTransport,
    val version: Long,
    val events: List<ExpenseAuditTransport>,
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
    val category: ExpenseCategoryTransport,
    val customCategory: String? = null,
    val notes: String? = null,
)

@Serializable
internal data class FinanceTotalsTransport(
    val pendingChargeCents: Long,
    val paidChargeCents: Long,
    val waivedChargeCents: Long,
    val cancelledChargeCents: Long,
    val activeExpenseCents: Long,
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

private fun ChargeStatusCommand.toRequest() = ChargeStatusRequest(status.toTransport(), note)

private fun ExpenseWriteCommand.toRequest() = ExpenseWriteRequest(
    requestId = requestId,
    description = description,
    amountCents = amountCents,
    expenseDate = expenseDate,
    category = category.toTransport(),
    customCategory = customCategory,
    notes = notes,
)

private fun NetworkResult<ChargeListTransport>.toChargeListResult() = mapFinance { it.toDomain() }

private fun NetworkResult<ExpenseListTransport>.toExpenseListResult() = mapFinance { it.toDomain() }

private fun NetworkResult<FinanceTotalsTransport>.toFinanceTotalsResult() = mapFinance { it.toDomain() }

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
)

private fun ChargeAuditTransport.toDomain() =
    ChargeAudit(actorId, oldStatus.toDomain(), newStatus.toDomain(), note, occurredAt)

private fun ExpenseListTransport.toDomain() =
    ExpenseList(expenses.map(ExpenseTransport::toDomain), activeExpenseTotalCents)

private fun ExpenseTransport.toDomain() = Expense(
    id, GroupId(groupId), description, amountCents, expenseDate, category.toDomain(),
    customCategory, notes, status.toDomain(), version, events.map(ExpenseAuditTransport::toDomain),
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

private fun ExpenseCategoryTransport.toDomain() = when (this) {
    ExpenseCategoryTransport.VENUE -> ExpenseCategory.Venue
    ExpenseCategoryTransport.EQUIPMENT -> ExpenseCategory.Equipment
    ExpenseCategoryTransport.REFEREE -> ExpenseCategory.Referee
    ExpenseCategoryTransport.OTHER -> ExpenseCategory.Other
}

private fun ExpenseCategory.toTransport() = when (this) {
    ExpenseCategory.Venue -> ExpenseCategoryTransport.VENUE
    ExpenseCategory.Equipment -> ExpenseCategoryTransport.EQUIPMENT
    ExpenseCategory.Referee -> ExpenseCategoryTransport.REFEREE
    ExpenseCategory.Other -> ExpenseCategoryTransport.OTHER
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
