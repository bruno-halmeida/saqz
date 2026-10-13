package br.com.saqz.groups.adapter.output.places

import br.com.saqz.groups.application.places.PlaceDetails
import br.com.saqz.groups.application.places.PlaceSuggestion
import br.com.saqz.groups.application.places.PlacesProvider
import br.com.saqz.groups.application.places.PlacesUpstreamException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Google Places API (New). A chave vai só no header `X-Goog-Api-Key` — nunca na URL nem em log.
 * Sem retry e sem cache (termos de uso do Google).
 */
class HttpGooglePlacesProvider(
    private val apiKey: String,
    baseUrl: String = DEFAULT_BASE_URL,
    connectTimeout: Duration = Duration.ofSeconds(2),
    private val readTimeout: Duration = Duration.ofSeconds(4),
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build(),
) : PlacesProvider {
    private val base = baseUrl.trimEnd('/')
    private val mapper = ObjectMapper()

    override fun autocomplete(query: String, session: String): List<PlaceSuggestion> {
        val body = mapper.createObjectNode().apply {
            put("input", query)
            put("sessionToken", session)
            put("languageCode", "pt-BR")
            put("regionCode", "br")
            putArray("includedRegionCodes").add("br")
            putObject("locationBias").putObject("rectangle").apply {
                putObject("low").put("latitude", -33.75).put("longitude", -73.99)
                putObject("high").put("latitude", 5.27).put("longitude", -34.79)
            }
        }
        val request = HttpRequest.newBuilder(URI.create("$base/v1/places:autocomplete"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        val root = read(send(request, "autocomplete"), "autocomplete")
        return root.path("suggestions").mapNotNull { suggestion ->
            val prediction = suggestion.path("placePrediction")
            val id = prediction.text("placeId") ?: return@mapNotNull null
            val description = prediction.path("text").text("text") ?: return@mapNotNull null
            val format = prediction.path("structuredFormat")
            PlaceSuggestion(
                placeId = id,
                name = format.path("mainText").text("text") ?: description,
                address = format.path("secondaryText").text("text").orEmpty(),
                description = description,
            )
        }.take(MAX_SUGGESTIONS)
    }

    override fun details(placeId: String, session: String): PlaceDetails {
        val uri = "$base/v1/places/${encode(placeId)}?sessionToken=${encode(session)}&languageCode=pt-BR&regionCode=BR"
        val request = HttpRequest.newBuilder(URI.create(uri))
            .header("X-Goog-FieldMask", "id,displayName,formattedAddress,location")
            .GET()
        val root = read(send(request, "details"), "details")
        val id = root.text("id") ?: placeId
        val address = root.text("formattedAddress").orEmpty()
        val location = root.path("location")
        return PlaceDetails(
            placeId = id,
            name = root.path("displayName").text("text") ?: address,
            address = address,
            latitude = location.path("latitude").takeIf(JsonNode::isNumber)?.doubleValue(),
            longitude = location.path("longitude").takeIf(JsonNode::isNumber)?.doubleValue(),
        )
    }

    private fun send(request: HttpRequest.Builder, operation: String): HttpResponse<String> = try {
        http.send(
            request.timeout(readTimeout).header("X-Goog-Api-Key", apiKey).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
        )
    } catch (failure: IOException) {
        throw PlacesUpstreamException("Google Places $operation fora do ar", failure)
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw PlacesUpstreamException("Google Places $operation interrompido", interrupted)
    }

    private fun read(response: HttpResponse<String>, operation: String): JsonNode {
        if (response.statusCode() !in 200..299) {
            throw PlacesUpstreamException("Google Places $operation respondeu ${response.statusCode()}")
        }
        return try {
            mapper.readTree(response.body()) ?: throw PlacesUpstreamException("Google Places $operation respondeu vazio")
        } catch (failure: IOException) {
            throw PlacesUpstreamException("Google Places $operation respondeu algo ilegível", failure)
        }
    }

    private fun JsonNode.text(field: String): String? = path(field).takeIf(JsonNode::isTextual)?.asText()?.takeUnless(String::isBlank)

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        const val DEFAULT_BASE_URL = "https://places.googleapis.com"
        private const val MAX_SUGGESTIONS = 5
    }
}
