package br.com.saqz.identity.adapter.input.http

import br.com.saqz.sharedkernel.CorrelationId
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

private const val CORRELATION_ATTRIBUTE = "br.com.saqz.correlationId"

class RequestCorrelationFilter : OncePerRequestFilter() {
    private val requestLogger = LoggerFactory.getLogger(RequestCorrelationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId = CorrelationId(UUID.randomUUID().toString())
        request.setAttribute(CORRELATION_ATTRIBUTE, correlationId)
        MDC.put("correlationId", correlationId.value)
        try {
            filterChain.doFilter(request, response)
        } finally {
            requestLogger.info("request_complete correlationId={} status={}", correlationId.value, response.status)
            MDC.remove("correlationId")
        }
    }
}

fun requestCorrelationId(request: HttpServletRequest): CorrelationId =
    request.getAttribute(CORRELATION_ATTRIBUTE) as CorrelationId
