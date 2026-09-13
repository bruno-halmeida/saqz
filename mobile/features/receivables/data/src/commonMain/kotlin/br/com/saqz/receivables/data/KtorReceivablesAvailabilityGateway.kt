package br.com.saqz.receivables.data

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import br.com.saqz.receivables.domain.ReceivablesAvailability
import br.com.saqz.receivables.domain.ReceivablesAvailabilityGateway
import br.com.saqz.receivables.domain.ReceivablesError
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

class KtorReceivablesAvailabilityGateway(
    private val network: AuthenticatedNetworkClient,
) : ReceivablesAvailabilityGateway {
    override suspend fun get(): SaqzResult<ReceivablesAvailability, ReceivablesError> {
        return when (val result = retryTransport(RetrySafety.Read) {
            network.execute(
                HttpMethod.Get,
                "api/receivables/availability",
                ReceivablesAvailabilityTransport.serializer(),
            )
        }) {
            is NetworkResult.Success -> result.value.toDomain()
            is NetworkResult.Failure -> SaqzResult.Failure(ReceivablesError.Data(result.error.toDataError()))
        }
    }
}

@Serializable
internal data class ReceivablesAvailabilityTransport(
    val backendEnabled: JsonPrimitive,
    val mobileEnabled: JsonPrimitive,
    val maintenanceAvailable: JsonPrimitive,
)

private fun ReceivablesAvailabilityTransport.toDomain(): SaqzResult<ReceivablesAvailability, ReceivablesError> {
    // Kotlin serialization accepts quoted booleans; rollout must require actual JSON booleans.
    val backend = backendEnabled.strictBoolean()
    val mobile = mobileEnabled.strictBoolean()
    val maintenance = maintenanceAvailable.strictBoolean()
    if (backend == null || mobile == null || maintenance != true) {
        return SaqzResult.Failure(ReceivablesError.Data(DataError.InvalidResponse))
    }
    return if (mobile && !backend) {
        SaqzResult.Failure(ReceivablesError.Data(DataError.InvalidResponse))
    } else {
        SaqzResult.Success(ReceivablesAvailability(backend, mobile))
    }
}

private fun JsonPrimitive.strictBoolean(): Boolean? = if (isString) null else booleanOrNull

private fun NetworkError.toDataError(): DataError = when (this) {
    is NetworkError.ApiProblemError -> problem.status.toDataError()
    is NetworkError.HttpStatus -> status.toDataError()
    NetworkError.Timeout -> DataError.Timeout
    NetworkError.Connectivity -> DataError.Connectivity
    NetworkError.InvalidResponse -> DataError.InvalidResponse
    NetworkError.PayloadTooLarge -> DataError.PayloadTooLarge
    NetworkError.Unavailable -> DataError.Unauthenticated
    NetworkError.Unknown -> DataError.Unknown
}

private fun Int.toDataError(): DataError = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}
