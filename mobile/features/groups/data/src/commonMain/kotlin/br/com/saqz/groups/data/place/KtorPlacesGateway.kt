package br.com.saqz.groups.data.place

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.place.PlaceDetails
import br.com.saqz.groups.domain.place.PlaceSuggestion
import br.com.saqz.groups.domain.place.PlacesGateway
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable private data class PlacesAvailabilityDto(val enabled: Boolean)

@Serializable
private data class PlaceSuggestionDto(
    val placeId: String,
    val name: String = "",
    val address: String = "",
    val description: String = "",
)

@Serializable private data class PlaceSuggestionsDto(val suggestions: List<PlaceSuggestionDto> = emptyList())

@Serializable
private data class PlaceDetailsDto(
    val placeId: String,
    val name: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * `GET /api/places/{availability,autocomplete,details}`. Backend sem o endpoint (ou sem a chave
 * do Google) responde erro, que vira falha: o app fica com o campo de texto livre.
 */
class KtorPlacesGateway(private val network: AuthenticatedNetworkClient) : PlacesGateway {
    override suspend fun availability(): SaqzResult<Boolean, DataError> =
        when (val result = network.execute(HttpMethod.Get, "api/places/availability", PlacesAvailabilityDto.serializer())) {
            is NetworkResult.Success -> SaqzResult.Success(result.value.enabled)
            is NetworkResult.Failure -> SaqzResult.Failure(result.error.toDataError())
        }

    override suspend fun autocomplete(query: String, session: String): SaqzResult<List<PlaceSuggestion>, DataError> =
        when (
            val result = network.execute(
                HttpMethod.Get, "api/places/autocomplete", PlaceSuggestionsDto.serializer(),
                NetworkRequest(query = mapOf("q" to query, "session" to session)),
            )
        ) {
            is NetworkResult.Success -> SaqzResult.Success(
                result.value.suggestions
                    .filter { it.placeId.isNotBlank() && it.name.isNotBlank() }
                    .map { PlaceSuggestion(it.placeId, it.name, it.address, it.description) },
            )
            is NetworkResult.Failure -> SaqzResult.Failure(result.error.toDataError())
        }

    override suspend fun details(placeId: String, session: String): SaqzResult<PlaceDetails, DataError> =
        when (
            val result = network.execute(
                HttpMethod.Get, "api/places/details", PlaceDetailsDto.serializer(),
                NetworkRequest(query = mapOf("placeId" to placeId, "session" to session)),
            )
        ) {
            is NetworkResult.Success -> result.value.takeIf { it.name.isNotBlank() }
                ?.let { SaqzResult.Success(PlaceDetails(it.placeId, it.name, it.address, it.latitude, it.longitude)) }
                ?: SaqzResult.Failure(DataError.InvalidResponse)
            is NetworkResult.Failure -> SaqzResult.Failure(result.error.toDataError())
        }
}

private fun NetworkError.toDataError(): DataError = when (this) {
    is NetworkError.ApiProblemError -> problem.status.toDataError()
    is NetworkError.HttpStatus -> status.toDataError()
    NetworkError.Timeout -> DataError.Timeout
    NetworkError.Connectivity -> DataError.Connectivity
    NetworkError.InvalidResponse -> DataError.InvalidResponse
    NetworkError.PayloadTooLarge -> DataError.PayloadTooLarge
    NetworkError.Unknown, NetworkError.Unavailable -> DataError.Unknown
}

private fun Int.toDataError(): DataError = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}
