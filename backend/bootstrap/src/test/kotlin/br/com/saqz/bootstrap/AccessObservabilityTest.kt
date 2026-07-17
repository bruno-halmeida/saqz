package br.com.saqz.bootstrap

import br.com.saqz.access.application.observability.AccessMetricEvent
import br.com.saqz.bootstrap.configuration.observability.MicrometerAccessMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AccessObservabilityTest {
    private val registry = SimpleMeterRegistry()
    private val metrics = MicrometerAccessMetrics(registry)

    @ParameterizedTest
    @CsvSource(
        "bootstrap,success,200",
        "bootstrap,failure,400",
        "http,rejected,401",
        "http,rejected,403",
        "http,rejected,404",
        "http,rejected,429",
        "invite,generated,201",
        "invite,expired,204",
        "invite,redeemed,200",
        "provider,failure,503",
    )
    fun `records every bounded access outcome`(operation: String, result: String, status: String) {
        metrics.record(AccessMetricEvent(operation, result, status))

        assertEquals(
            1.0,
            registry.get("saqz.access.events")
                .tags("operation", operation, "result", result, "status", status)
                .counter().count(),
        )
    }

    @Test
    fun `publishes only the fixed label names`() {
        metrics.record(AccessMetricEvent("bootstrap", "success", "200"))

        assertEquals(setOf("operation", "result", "status"), registry.meters.single().id.tags.map { it.key }.toSet())
    }

    @Test
    fun `registry never contains sensitive request values`() {
        metrics.record(AccessMetricEvent("invite", "redeemed", "200"))
        val registryText = registry.meters.joinToString()

        listOf("person@example.test", "Bearer secret-token", "invite-secret", "user-id", "group-id")
            .forEach { assertFalse(registryText.contains(it)) }
    }
}
