package br.com.saqz.groups.domain.place

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult

/** Uma sugestão da busca de endereço: [name] é o texto principal e [address] o complemento. */
data class PlaceSuggestion(
    val placeId: String,
    val name: String,
    val address: String,
    val description: String,
)

/** O lugar escolhido, já com o endereço completo. Coordenadas podem faltar. */
data class PlaceDetails(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * Busca de endereço pelo Google Places, sempre pelo backend (a chave nunca vai ao app).
 * `session` é o token da sessão de busca: o mesmo valor em todas as sugestões e no [details]
 * que a encerra.
 */
interface PlacesGateway {
    /** Se a busca está ligada neste servidor; quem decide é o backend, nunca o app. */
    suspend fun availability(): SaqzResult<Boolean, DataError>

    suspend fun autocomplete(query: String, session: String): SaqzResult<List<PlaceSuggestion>, DataError>

    suspend fun details(placeId: String, session: String): SaqzResult<PlaceDetails, DataError>
}
