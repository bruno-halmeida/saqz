package br.com.saqz.groups.application.places

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

class PlacesRateLimitedException(val retryAfterSeconds: Int) : RuntimeException()

/** Janela fixa de um minuto por usuário, em memória; entradas de janelas passadas são descartadas. */
class PlacesRateLimiter(private val clock: Clock, private val limitPerMinute: Int = 60) {
    private class Window(val minute: Long, var count: Int)

    private val windows = HashMap<String, Window>()
    private var lastSweepMinute = Long.MIN_VALUE

    @Synchronized
    fun acquire(userKey: String) {
        val nowMillis = clock.millis()
        val minute = Math.floorDiv(nowMillis, 60_000L)
        if (minute != lastSweepMinute) {
            windows.values.removeIf { it.minute < minute }
            lastSweepMinute = minute
        }
        val window = windows[userKey]?.takeIf { it.minute == minute } ?: Window(minute, 0).also { windows[userKey] = it }
        if (window.count >= limitPerMinute) {
            val retry = (((minute + 1) * 60_000L - nowMillis + 999) / 1000).toInt().coerceIn(1, 60)
            throw PlacesRateLimitedException(retry)
        }
        window.count++
    }

    internal fun trackedUsers(): Int = synchronized(this) { windows.size }
}

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
