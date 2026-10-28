package br.com.saqz.access.data.appupdate

import br.com.saqz.access.domain.appupdate.AppUpdateError
import br.com.saqz.access.domain.appupdate.AppUpdateGateway
import br.com.saqz.access.domain.appupdate.AppUpdateVersions
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

/** Só o pedaço de `GET /api/features` que a DEB-117 lê; flags e plataforma ficam para a DEB-120. */
@Serializable
internal data class AppFeaturesTransport(val appVersion: AppVersionTransport? = null)

@Serializable
internal data class AppVersionTransport(val minimum: String? = null, val recommended: String? = null)

/**
 * Anônimo de propósito: o pop-up de atualização vale antes do login, então a chamada usa o
 * `NetworkClient` cru, sem bearer. Plataforma e versão já vão nos cabeçalhos do `AppClient`.
 */
class KtorAppUpdateGateway(
    private val network: NetworkClient,
) : AppUpdateGateway {
    override suspend fun versions(): SaqzResult<AppUpdateVersions, AppUpdateError> =
        when (
            val result = retryTransport(RetrySafety.Read) {
                network.execute(HttpMethod.Get, "api/features", AppFeaturesTransport.serializer())
            }
        ) {
            is NetworkResult.Success -> SaqzResult.Success(result.value.toDomain())
            is NetworkResult.Failure -> SaqzResult.Failure(AppUpdateError.Data(result.error.toDataError()))
        }
}

private fun AppFeaturesTransport.toDomain() =
    AppUpdateVersions(minimum = appVersion?.minimum, recommended = appVersion?.recommended)

private fun NetworkError.toDataError(): DataError = when (this) {
    NetworkError.Timeout -> DataError.Timeout
    NetworkError.Connectivity -> DataError.Connectivity
    NetworkError.InvalidResponse -> DataError.InvalidResponse
    NetworkError.PayloadTooLarge -> DataError.PayloadTooLarge
    is NetworkError.HttpStatus -> if (status in 500..599) DataError.Server else DataError.Unknown
    is NetworkError.ApiProblemError -> if (problem.status in 500..599) DataError.Server else DataError.Unknown
    NetworkError.Unavailable,
    NetworkError.Unknown,
    -> DataError.Unknown
}
