package br.com.saqz.subscriptions.data.trial

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialError
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import br.com.saqz.network.NetworkRequest

@Serializable
internal enum class TrialStatusTransport {
    AVAILABLE,
    ACTIVE,
    EXPIRED,
    INELIGIBLE,
    SUBSCRIBED,
}

@Serializable
internal data class TrialAccessTransport(
    val status: TrialStatusTransport,
    val startedAt: String? = null,
    val endsAt: String? = null,
    val serverTime: String,
    val readOnly: Boolean,
    val canCreateGroup: Boolean,
    val maxGroups: Int,
    val maxAthletes: Int,
    val isOwner: Boolean,
    val appUrl: String? = null,
    val offerMode: String = "ON",
    val canRedeemCoupon: Boolean = false,
    val selectedCouponCode: String? = null,
    val trialDays: Int = 14,
)

@Serializable
internal data class ApplyTrialCouponTransport(val code: String)

class KtorTrialGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : TrialGateway {
    override suspend fun applyCoupon(code: String) = retryTransport(RetrySafety.Never, delayMillis = retryDelay) {
        network.execute(HttpMethod.Post, "subscriptions/trial/coupon", TrialAccessTransport.serializer(),
            NetworkRequest(Json.encodeToString(ApplyTrialCouponTransport.serializer(), ApplyTrialCouponTransport(code))))
    }.mapTrial { it.toDomain() }

    override suspend fun ownerTrial() = fetch("subscriptions/trial")

    override suspend fun groupTrial(groupId: GroupId) = fetch("api/groups/${groupId.value}/trial")

    private suspend fun fetch(path: String): SaqzResult<TrialAccess, TrialError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(HttpMethod.Get, path, TrialAccessTransport.serializer())
        }.mapTrial { it.toDomain() }
}

private inline fun <T, R> NetworkResult<T>.mapTrial(mapper: (T) -> R): SaqzResult<R, TrialError> = when (this) {
    is NetworkResult.Success -> SaqzResult.Success(mapper(value))
    is NetworkResult.Failure -> SaqzResult.Failure(error.toTrialError())
}

private fun TrialAccessTransport.toDomain() = TrialAccess(
    status = status.toDomain(),
    startedAt = startedAt,
    endsAt = endsAt,
    serverTime = serverTime,
    readOnly = readOnly,
    canCreateGroup = canCreateGroup,
    maxGroups = maxGroups,
    maxAthletes = maxAthletes,
    isOwner = isOwner,
    appUrl = appUrl,
    offerMode = offerMode,
    canRedeemCoupon = canRedeemCoupon,
    selectedCouponCode = selectedCouponCode,
    trialDays = trialDays,
)

private fun TrialStatusTransport.toDomain() = when (this) {
    TrialStatusTransport.AVAILABLE -> TrialStatus.Available
    TrialStatusTransport.ACTIVE -> TrialStatus.Active
    TrialStatusTransport.EXPIRED -> TrialStatus.Expired
    TrialStatusTransport.INELIGIBLE -> TrialStatus.Ineligible
    TrialStatusTransport.SUBSCRIBED -> TrialStatus.Subscribed
}

private fun NetworkError.toTrialError(): TrialError = when (this) {
    is NetworkError.ApiProblemError -> when (problem.status) {
        400 -> TrialError.CouponUnavailable
        409 -> TrialError.OfferUnavailable
        404 -> TrialError.NotFound
        else -> TrialError.Data(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> when (status) {
        400 -> TrialError.CouponUnavailable
        409 -> TrialError.OfferUnavailable
        404 -> TrialError.NotFound
        else -> TrialError.Data(status.toDataError())
    }
    NetworkError.Timeout -> TrialError.Data(DataError.Timeout)
    NetworkError.Connectivity -> TrialError.Data(DataError.Connectivity)
    NetworkError.InvalidResponse -> TrialError.Data(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> TrialError.Data(DataError.PayloadTooLarge)
    NetworkError.Unavailable -> TrialError.Data(DataError.Server)
    NetworkError.Unknown -> TrialError.Data(DataError.Unknown)
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
