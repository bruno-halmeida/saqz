package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.places.PlaceDetails
import br.com.saqz.groups.application.places.PlaceSuggestion
import br.com.saqz.groups.application.places.PlacesLookup
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class PlacesAvailabilityResponse(val enabled: Boolean)

data class PlaceSuggestionsResponse(val suggestions: List<PlaceSuggestionResponse>)

data class PlaceSuggestionResponse(val placeId: String, val name: String, val address: String, val description: String)

data class PlaceDetailsResponse(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
)

/**
 * Busca de endereço de quadra pelo Google Places, atrás do backend (a chave nunca vai ao app).
 * Sempre registrado: `availability` avisa o app; as demais rotas respondem 503 sem chave.
 */
@RestController
class PlacesController(private val lookup: PlacesLookup) {
    @GetMapping("/api/places/availability")
    fun availability() = PlacesAvailabilityResponse(lookup.enabled)

    @GetMapping("/api/places/autocomplete")
    fun autocomplete(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestParam(name = "q", required = false) q: String?,
        @RequestParam(name = "session", required = false) session: String?,
    ): PlaceSuggestionsResponse {
        val errors = LinkedHashMap<String, List<String>>()
        val query = q?.trim().orEmpty()
        if (query.codePointCount(0, query.length) < MIN_QUERY || query.length > MAX_QUERY) {
            errors["q"] = listOf("must be between $MIN_QUERY and $MAX_QUERY characters")
        }
        checkSession(session, errors)
        if (errors.isNotEmpty()) throw InvalidGroupRequestException(errors)
        return PlaceSuggestionsResponse(lookup.autocomplete(identity.subject, query, session!!).map { it.toResponse() })
    }

    @GetMapping("/api/places/details")
    fun details(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestParam(name = "placeId", required = false) placeId: String?,
        @RequestParam(name = "session", required = false) session: String?,
    ): PlaceDetailsResponse {
        val errors = LinkedHashMap<String, List<String>>()
        if (placeId == null || !PLACE_ID.matches(placeId)) errors["placeId"] = listOf("is invalid")
        checkSession(session, errors)
        if (errors.isNotEmpty()) throw InvalidGroupRequestException(errors)
        return lookup.details(identity.subject, placeId!!, session!!).toResponse()
    }

    private fun checkSession(session: String?, errors: MutableMap<String, List<String>>) {
        if (session.isNullOrBlank() || session.length > MAX_SESSION || session.any { it.isISOControl() }) {
            errors["session"] = listOf("must be between 1 and $MAX_SESSION characters")
        }
    }

    private fun PlaceSuggestion.toResponse() = PlaceSuggestionResponse(placeId, name, address, description)

    private fun PlaceDetails.toResponse() = PlaceDetailsResponse(placeId, name, address, latitude, longitude)

    private companion object {
        const val MIN_QUERY = 3
        const val MAX_QUERY = 200
        const val MAX_SESSION = 64
        val PLACE_ID = Regex("[A-Za-z0-9_-]{1,256}")
    }
}
