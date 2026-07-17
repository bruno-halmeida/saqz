package br.com.saqz.bootstrap.configuration.observability

import br.com.saqz.access.application.observability.AccessMetricEvent
import br.com.saqz.access.application.observability.AccessMetrics
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class MicrometerAccessMetrics(
    private val registry: MeterRegistry,
    private val correlationId: () -> String = { MDC.get("correlationId") ?: "unavailable" },
) : AccessMetrics {
    private val logger = LoggerFactory.getLogger(MicrometerAccessMetrics::class.java)

    override fun record(event: AccessMetricEvent) {
        registry.counter(
            "saqz.access.events",
            "operation", event.operation,
            "result", event.result,
            "status", event.status,
        ).increment()
        logger.info(
            "access_event correlationId={} operation={} result={} status={}",
            correlationId(), event.operation, event.result, event.status,
        )
    }
}
