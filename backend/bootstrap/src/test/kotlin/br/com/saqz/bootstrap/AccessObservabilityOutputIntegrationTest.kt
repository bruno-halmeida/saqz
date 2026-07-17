package br.com.saqz.bootstrap

import br.com.saqz.access.application.observability.AccessMetricEvent
import br.com.saqz.bootstrap.configuration.observability.MicrometerAccessMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(OutputCaptureExtension::class)
class AccessObservabilityOutputIntegrationTest {
    @Test
    fun `structured event contains stable outcome and correlation id`(output: CapturedOutput) {
        val metrics = MicrometerAccessMetrics(SimpleMeterRegistry()) { "correlation-test" }

        metrics.record(AccessMetricEvent("provider", "failure", "503"))

        assertTrue(output.out.contains("access_event correlationId=correlation-test operation=provider result=failure status=503"))
    }

    @Test
    fun `structured event excludes forbidden values`(output: CapturedOutput) {
        MicrometerAccessMetrics(SimpleMeterRegistry()) { "correlation-safe" }
            .record(AccessMetricEvent("invite", "redeemed", "200"))

        listOf("person@example.test", "Bearer secret-token", "invite-secret", "password-value")
            .forEach { assertFalse(output.out.contains(it)) }
    }
}
