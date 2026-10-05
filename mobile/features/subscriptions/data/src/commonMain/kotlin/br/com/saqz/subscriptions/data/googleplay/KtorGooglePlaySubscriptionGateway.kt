package br.com.saqz.subscriptions.data.googleplay

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import br.com.saqz.subscriptions.data.appstore.AppStoreAccountTokenTransport
import br.com.saqz.subscriptions.data.subscription.MySubscriptionTransport
import br.com.saqz.subscriptions.data.subscription.toDataError
import br.com.saqz.subscriptions.data.subscription.toDomain
import br.com.saqz.subscriptions.domain.googleplay.GooglePlaySubmissionError
import br.com.saqz.subscriptions.domain.googleplay.GooglePlaySubscriptionGateway
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class GooglePlayPurchaseRequestTransport(val productId: String, val purchaseToken: String)

class KtorGooglePlaySubscriptionGateway(
    private val network: AuthenticatedNetworkClient,
    private val json: Json = Json { explicitNulls = false; ignoreUnknownKeys = true },
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : GooglePlaySubscriptionGateway {
    // O mesmo endpoint da App Store: o UUID da conta vale para as duas lojas.
    override suspend fun obfuscatedAccountId() = retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
        network.execute(
            HttpMethod.Get,
            "subscriptions/app-store/account-token",
            AppStoreAccountTokenTransport.serializer(),
        )
    }.mapGooglePlay { it.appAccountToken }

    // O token da compra é a chave de idempotência no backend: reenviar é seguro sem `requestId`.
    override suspend fun submitPurchase(productId: String, purchaseToken: String) =
        retryTransport(RetrySafety.IdempotentWrite, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "subscriptions/google-play/purchases",
                MySubscriptionTransport.serializer(),
                NetworkRequest(
                    json.encodeToString(
                        GooglePlayPurchaseRequestTransport.serializer(),
                        GooglePlayPurchaseRequestTransport(productId = productId, purchaseToken = purchaseToken),
                    ),
                ),
            )
        }.mapGooglePlay { it.toDomain() }
}

private inline fun <T, R> NetworkResult<T>.mapGooglePlay(mapper: (T) -> R): SaqzResult<R, GooglePlaySubmissionError> =
    when (this) {
        is NetworkResult.Failure -> SaqzResult.Failure(error.toGooglePlayError())
        is NetworkResult.Success -> SaqzResult.Success(mapper(value))
    }

private fun NetworkError.toGooglePlayError(): GooglePlaySubmissionError = when (this) {
    is NetworkError.ApiProblemError -> when (problem.code) {
        "GOOGLE_PLAY_PURCHASE_OWNED_BY_ANOTHER_ACCOUNT" -> GooglePlaySubmissionError.OwnedByAnotherAccount
        "GOOGLE_PLAY_PURCHASE_INVALID" -> GooglePlaySubmissionError.Invalid
        else -> GooglePlaySubmissionError.Data(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> GooglePlaySubmissionError.Data(status.toDataError())
    NetworkError.Timeout -> GooglePlaySubmissionError.Data(DataError.Timeout)
    NetworkError.Connectivity -> GooglePlaySubmissionError.Data(DataError.Connectivity)
    NetworkError.InvalidResponse -> GooglePlaySubmissionError.Data(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> GooglePlaySubmissionError.Data(DataError.PayloadTooLarge)
    NetworkError.Unavailable -> GooglePlaySubmissionError.Data(DataError.Server)
    NetworkError.Unknown -> GooglePlaySubmissionError.Data(DataError.Unknown)
}
