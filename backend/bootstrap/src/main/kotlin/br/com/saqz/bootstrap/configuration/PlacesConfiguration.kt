package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.input.http.PlacesController
import br.com.saqz.groups.adapter.output.places.HttpGooglePlacesProvider
import br.com.saqz.groups.application.places.PlacesLookup
import br.com.saqz.groups.application.places.PlacesRateLimiter
import java.time.Clock
import java.time.Duration
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Sempre registrado, como a disponibilidade do WhatsApp: sem `saqz.places.google.api-key` o
 * `availability` responde `enabled=false` e as buscas respondem 503.
 */
@Configuration(proxyBeanMethods = false)
class PlacesConfiguration {
    @Bean
    fun placesLookup(
        clock: ObjectProvider<Clock>,
        @Value("\${saqz.places.google.api-key:}") apiKey: String,
        @Value("\${saqz.places.google.base-url:https://places.googleapis.com}") baseUrl: String,
        @Value("\${saqz.places.google.connect-timeout-ms:2000}") connectTimeoutMs: Long,
        @Value("\${saqz.places.google.read-timeout-ms:4000}") readTimeoutMs: Long,
    ) = PlacesLookup(
        provider = apiKey.trim().takeIf(String::isNotEmpty)?.let {
            HttpGooglePlacesProvider(
                apiKey = it,
                baseUrl = baseUrl,
                connectTimeout = Duration.ofMillis(connectTimeoutMs),
                readTimeout = Duration.ofMillis(readTimeoutMs),
            )
        },
        limiter = PlacesRateLimiter(clock.getIfAvailable { Clock.systemUTC() }),
    )

    @Bean
    fun placesController(lookup: PlacesLookup) = PlacesController(lookup)
}
