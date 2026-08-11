package br.com.saqz.subscriptions.data.subscription

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.subscriptions.domain.subscription.*
import br.com.saqz.network.*
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
internal enum class PlanTransport { TITULAR, ORGANIZADOR, ILIMITADO }

@Serializable
internal enum class SubscriptionCycleTransport { MONTHLY, ANNUAL }

@Serializable
internal enum class SubscriptionStatusTransport { ACTIVE, PAST_DUE, CANCELED }

@Serializable
internal data class SubscriptionUsageTransport(
    val groupsUsed: Int,
    val groupsLimit: Int? = null,
)

@Serializable
internal data class MySubscriptionTransport(
    val status: SubscriptionStatusTransport,
    val entitled: Boolean,
    val plan: PlanTransport,
    val cycle: SubscriptionCycleTransport,
    val currentPeriodEnd: String,
    val usage: SubscriptionUsageTransport,
    val canceledAt: String? = null,
)

@Serializable
internal data class CanceledSubscriptionTransport(
    val status: SubscriptionStatusTransport,
    val canceledAt: String,
    val currentPeriodEnd: String,
)

@Serializable
internal data class ReceiptTransport(
    val asaasEventId: String,
    val asaasPaymentId: String? = null,
    val valueCents: Long? = null,
    val confirmedAt: String? = null,
    val processedAt: String,
)

@Serializable
internal data class ReceiptListTransport(val receipts: List<ReceiptTransport>)

class KtorSubscriptionGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : SubscriptionGateway {
    override suspend fun mySubscription() = retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
        network.execute(HttpMethod.Get, "subscriptions/me", MySubscriptionTransport.serializer())
    }.mapSubscription { it.toDomain() }

    override suspend fun cancel() = network.execute(
        HttpMethod.Post,
        "subscriptions/me/cancel",
        CanceledSubscriptionTransport.serializer(),
    ).mapSubscription { it.toDomain() }

    override suspend fun receipts(limit: Int, offset: Int) =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "subscriptions/me/receipts",
                ReceiptListTransport.serializer(),
                NetworkRequest(query = mapOf("limit" to limit.toString(), "offset" to offset.toString())),
            )
        }.mapSubscription { it.receipts.map(ReceiptTransport::toDomain) }
}

private inline fun <T, R> NetworkResult<T>.mapSubscription(mapper: (T) -> R): SaqzResult<R, SubscriptionError> =
    when (this) {
        is NetworkResult.Failure -> SaqzResult.Failure(error.toSubscriptionError())
        is NetworkResult.Success -> SaqzResult.Success(mapper(value))
    }

private fun MySubscriptionTransport.toDomain() = MySubscription(
    status = status.toDomain(),
    entitled = entitled,
    plan = plan.toDomain(),
    cycle = cycle.toDomain(),
    currentPeriodEnd = currentPeriodEnd,
    usage = usage.toDomain(),
    canceledAt = canceledAt,
)

private fun SubscriptionUsageTransport.toDomain() = SubscriptionUsage(groupsUsed, groupsLimit)

private fun CanceledSubscriptionTransport.toDomain() =
    CanceledSubscription(status.toDomain(), canceledAt, currentPeriodEnd)

private fun ReceiptTransport.toDomain() = Receipt(asaasEventId, asaasPaymentId, valueCents, confirmedAt, processedAt)

private fun PlanTransport.toDomain() = when (this) {
    PlanTransport.TITULAR -> Plan.Titular
    PlanTransport.ORGANIZADOR -> Plan.Organizador
    PlanTransport.ILIMITADO -> Plan.Ilimitado
}

private fun SubscriptionCycleTransport.toDomain() = when (this) {
    SubscriptionCycleTransport.MONTHLY -> SubscriptionCycle.Monthly
    SubscriptionCycleTransport.ANNUAL -> SubscriptionCycle.Annual
}

private fun SubscriptionStatusTransport.toDomain() = when (this) {
    SubscriptionStatusTransport.ACTIVE -> SubscriptionStatus.Active
    SubscriptionStatusTransport.PAST_DUE -> SubscriptionStatus.PastDue
    SubscriptionStatusTransport.CANCELED -> SubscriptionStatus.Canceled
}

internal fun NetworkError.toSubscriptionError(): SubscriptionError = when (this) {
    is NetworkError.ApiProblemError -> when {
        problem.code == "VALIDATION_FAILED" -> SubscriptionError.Validation(
            DataError.Validation(
                ValidationDetails(
                    globalMessages = emptyList(),
                    fieldMessages = problem.fieldErrors.orEmpty(),
                ),
            ),
        )
        problem.code == "SUBSCRIPTION_NOT_FOUND" -> SubscriptionError.NotFound
        problem.code == "SUBSCRIPTION_CONFLICT" -> SubscriptionError.Conflict
        else -> SubscriptionError.Data(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> SubscriptionError.Data(status.toDataError())
    NetworkError.Timeout -> SubscriptionError.Data(DataError.Timeout)
    NetworkError.Connectivity -> SubscriptionError.Data(DataError.Connectivity)
    NetworkError.InvalidResponse -> SubscriptionError.Data(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> SubscriptionError.Data(DataError.PayloadTooLarge)
    NetworkError.Unavailable -> SubscriptionError.Data(DataError.Server)
    NetworkError.Unknown -> SubscriptionError.Data(DataError.Unknown)
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
