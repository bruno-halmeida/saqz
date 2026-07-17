package br.com.saqz.bootstrap.configuration.http

import br.com.saqz.sharedkernel.CorrelationId
import br.com.saqz.access.application.observability.AccessMetricEvent
import br.com.saqz.access.application.observability.AccessMetrics
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

private const val CORRELATION_ATTRIBUTE = "br.com.saqz.correlationId"
private const val CORRELATION_HEADER = "X-Correlation-ID"

class RequestCorrelationFilter(
    private val accessMetrics: AccessMetrics = AccessMetrics.NONE,
) : OncePerRequestFilter() {
    private val requestLogger = LoggerFactory.getLogger(RequestCorrelationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId = CorrelationId(UUID.randomUUID().toString())
        request.setAttribute(CORRELATION_ATTRIBUTE, correlationId)
        response.setHeader(CORRELATION_HEADER, correlationId.value)
        MDC.put("correlationId", correlationId.value)
        try {
            filterChain.doFilter(request, response)
        } finally {
            if (request.requestURI.startsWith("/api/") && response.status in setOf(401, 403, 404, 429, 503)) {
                val operation = if (response.status == 503) "provider" else "http"
                val result = if (response.status == 503) "failure" else "rejected"
                accessMetrics.record(AccessMetricEvent(operation, result, response.status.toString()))
            }
            requestLogger.info("request_complete correlationId={} status={}", correlationId.value, response.status)
            MDC.remove("correlationId")
        }
    }
}

fun requestCorrelationId(request: HttpServletRequest): CorrelationId =
    request.getAttribute(CORRELATION_ATTRIBUTE) as CorrelationId
