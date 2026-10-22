package br.com.saqz.groups.application.places

import br.com.saqz.groups.application.ratelimit.PerUserRateLimiter
import br.com.saqz.groups.application.ratelimit.UserRateLimitedException
import java.time.Clock

data class PlaceSuggestion(val placeId: String, val name: String, val address: String, val description: String)

data class PlaceDetails(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
)

/** Porta de saída para o provedor de lugares (Google Places API). Nada é guardado em cache. */
interface PlacesProvider {
    /** @throws PlacesUpstreamException quando o provedor falha, estoura o tempo ou responde algo inválido. */
    fun autocomplete(query: String, session: String): List<PlaceSuggestion>

    /** @throws PlacesUpstreamException quando o provedor falha, estoura o tempo ou responde algo inválido. */
    fun details(placeId: String, session: String): PlaceDetails
}

/** Sem chave configurada: o recurso fica desligado neste servidor. */
class PlacesUnavailableException : RuntimeException("places unavailable")

class PlacesUpstreamException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

typealias PlacesRateLimitedException = UserRateLimitedException

/** Sessenta chamadas por minuto por usuário (ver [PerUserRateLimiter]). */
class PlacesRateLimiter(clock: Clock, limitPerMinute: Int = 60) : PerUserRateLimiter(clock, limitPerMinute)

class PlacesLookup(private val provider: PlacesProvider?, private val limiter: PlacesRateLimiter) {
    val enabled: Boolean get() = provider != null

    fun autocomplete(userKey: String, query: String, session: String): List<PlaceSuggestion> =
        guarded(userKey) { it.autocomplete(query, session) }

    fun details(userKey: String, placeId: String, session: String): PlaceDetails =
        guarded(userKey) { it.details(placeId, session) }

    private fun <T> guarded(userKey: String, call: (PlacesProvider) -> T): T {
        val active = provider ?: throw PlacesUnavailableException()
        limiter.acquire(userKey)
        return call(active)
    }
}
