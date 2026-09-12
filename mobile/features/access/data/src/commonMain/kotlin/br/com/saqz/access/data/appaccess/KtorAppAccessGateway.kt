package br.com.saqz.access.data.appaccess

import br.com.saqz.access.domain.appaccess.AppAccessError
import br.com.saqz.access.domain.appaccess.AppAccessGateway
import br.com.saqz.access.domain.appaccess.AppAccessSession
import br.com.saqz.access.domain.appaccess.OnboardingError
import br.com.saqz.access.domain.appaccess.OnboardingGateway
import br.com.saqz.access.domain.appaccess.OnboardingStatus
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class AppAccessRedeemRequestTransport(val code: String) {
    override fun toString(): String = "AppAccessRedeemRequestTransport(code=<redacted>)"
}

@Serializable
internal data class AppAccessSessionTransport(
    val customToken: String,
    val ownerUserId: String,
    val displayName: String,
    val onboardingCompleted: Boolean,
) {
    override fun toString(): String =
        "AppAccessSessionTransport(customToken=<redacted>, ownerUserId=$ownerUserId, " +
            "displayName=$displayName, onboardingCompleted=$onboardingCompleted)"
}

@Serializable
internal data class OnboardingStatusTransport(val onboardingCompleted: Boolean)

class KtorAppAccessGateway(
    private val network: NetworkClient,
    private val json: Json = Json { explicitNulls = false; ignoreUnknownKeys = true },
) : AppAccessGateway {
    override suspend fun redeem(code: String): SaqzResult<AppAccessSession, AppAccessError> {
        if (code.isBlank()) return SaqzResult.Failure(AppAccessError.CodeInvalid)
        // This endpoint atomically burns the code. RetrySafety.Never is intentional: a lost
        // response must lead to link renewal, never to a second consume attempt.
        return retryTransport(RetrySafety.Never) {
            network.execute(
                HttpMethod.Post,
                "api/session/app-link/redeem",
                AppAccessSessionTransport.serializer(),
                request = NetworkRequest(
                    json.encodeToString(
                        AppAccessRedeemRequestTransport.serializer(),
                        AppAccessRedeemRequestTransport(code),
                    ),
                ),
            )
        }.mapAppAccess { response ->
            if (response.customToken.isBlank() || response.ownerUserId.isBlank()) {
                throw InvalidAppAccessPayload
            }
            response.toDomain()
        }
    }
}

class KtorOnboardingGateway(
    private val network: AuthenticatedNetworkClient,
) : OnboardingGateway {
    override suspend fun status() = network.execute(
        HttpMethod.Get,
        "api/session/onboarding",
        OnboardingStatusTransport.serializer(),
    ).mapOnboarding { OnboardingStatus(it.onboardingCompleted) }

    override suspend fun complete() = network.execute(
        HttpMethod.Put,
        "api/session/onboarding",
        OnboardingStatusTransport.serializer(),
    ).mapOnboarding { OnboardingStatus(it.onboardingCompleted) }
}

private inline fun <T, R> NetworkResult<T>.mapAppAccess(mapper: (T) -> R): SaqzResult<R, AppAccessError> = when (this) {
    is NetworkResult.Success -> runCatching { mapper(value) }
        .fold(
            onSuccess = { SaqzResult.Success(it) },
            onFailure = { SaqzResult.Failure(AppAccessError.Data(DataError.InvalidResponse)) },
        )
    is NetworkResult.Failure -> SaqzResult.Failure(error.toAppAccessError())
}

private inline fun <T, R> NetworkResult<T>.mapOnboarding(mapper: (T) -> R): SaqzResult<R, OnboardingError> = when (this) {
    is NetworkResult.Success -> SaqzResult.Success(mapper(value))
    is NetworkResult.Failure -> SaqzResult.Failure(error.toOnboardingError())
}

private fun AppAccessSessionTransport.toDomain() = AppAccessSession(
    customToken = customToken,
    ownerUserId = ownerUserId,
    displayName = displayName,
    onboardingCompleted = onboardingCompleted,
)

private fun NetworkError.toAppAccessError() = when (this) {
    is NetworkError.ApiProblemError -> when (problem.code) {
        "APP_ONBOARDING_CODE_INVALID" -> AppAccessError.CodeInvalid
        "IDENTITY_PROVIDER_UNAVAILABLE" -> AppAccessError.ProviderUnavailable
        else -> AppAccessError.Data(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> AppAccessError.Data(status.toDataError())
    NetworkError.Timeout -> AppAccessError.Data(DataError.Timeout)
    NetworkError.Connectivity -> AppAccessError.Data(DataError.Connectivity)
    NetworkError.InvalidResponse -> AppAccessError.Data(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> AppAccessError.Data(DataError.PayloadTooLarge)
    NetworkError.Unavailable -> AppAccessError.ProviderUnavailable
    NetworkError.Unknown -> AppAccessError.Data(DataError.Unknown)
}

private fun NetworkError.toOnboardingError() = when (this) {
    is NetworkError.ApiProblemError -> when (problem.code) {
        "ACCOUNT_NOT_FOUND" -> OnboardingError.AccountNotFound
        "ACCOUNT_SUSPENDED" -> OnboardingError.AccountSuspended
        else -> OnboardingError.Data(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> OnboardingError.Data(status.toDataError())
    NetworkError.Timeout -> OnboardingError.Data(DataError.Timeout)
    NetworkError.Connectivity -> OnboardingError.Data(DataError.Connectivity)
    NetworkError.InvalidResponse -> OnboardingError.Data(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> OnboardingError.Data(DataError.PayloadTooLarge)
    NetworkError.Unavailable -> OnboardingError.Data(DataError.Server)
    NetworkError.Unknown -> OnboardingError.Data(DataError.Unknown)
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

private object InvalidAppAccessPayload : IllegalArgumentException()
