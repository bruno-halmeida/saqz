package br.com.saqz.bootstrap.configuration.http

import br.com.saqz.sharedkernel.CorrelationId
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

private const val CORRELATION_ATTRIBUTE = "br.com.saqz.correlationId"
private const val CORRELATION_HEADER = "X-Correlation-ID"
private const val CORRELATION_ATTEMPT_HEADER = "X-Correlation-Attempt"
private const val MAX_ATTEMPT_DIGITS = 2
private const val UUID_LENGTH = 36
private const val NANOS_PER_MILLI = 1_000_000L

class RequestCorrelationFilter : OncePerRequestFilter() {
    private val requestLogger = LoggerFactory.getLogger(RequestCorrelationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // O id nasce no app (HttpTransport do mobile) para o cliente conhecê-lo mesmo quando a
        // resposta não chega. Sem header, ou com header que não é UUID, o servidor gera um.
        val started = System.nanoTime()
        val supplied = request.getHeader(CORRELATION_HEADER)?.takeIf(::isUuid)
        val correlationId = CorrelationId(supplied ?: UUID.randomUUID().toString())
        request.setAttribute(CORRELATION_ATTRIBUTE, correlationId)
        response.setHeader(CORRELATION_HEADER, correlationId.value)
        MDC.put("correlationId", correlationId.value)
        // Tentativa do retry do app (VUL-253): o id é o mesmo nas N tentativas, este número as separa.
        val attempt = request.getHeader(CORRELATION_ATTEMPT_HEADER)?.takeIf(::isAttempt) ?: "1"
        MDC.put("attempt", attempt)
        try {
            filterChain.doFilter(request, response)
        } finally {
            // `correlationId={} status={}` ficam adjacentes: SafeDiagnosticsIntegrationTest casa nessa substring.
            // `requestURI` nao carrega query string, entao nenhum parametro vai para o log.
            requestLogger.info(
                "request_complete correlationId={} status={} method={} path={} durationMs={} attempt={}",
                correlationId.value,
                response.status,
                request.method,
                request.requestURI,
                (System.nanoTime() - started) / NANOS_PER_MILLI,
                attempt,
            )
            // Este filtro é o mais externo da cadeia: limpa também o `subject` que o
            // BearerAuthenticationFilter põe, depois de a linha acima já tê-lo impresso.
            MDC.clear()
        }
    }
}

fun requestCorrelationId(request: HttpServletRequest): CorrelationId =
    request.getAttribute(CORRELATION_ATTRIBUTE) as CorrelationId

private fun isUuid(value: String): Boolean =
    value.length == UUID_LENGTH && runCatching { UUID.fromString(value) }.isSuccess

private fun isAttempt(value: String): Boolean =
    value.isNotEmpty() && value.length <= MAX_ATTEMPT_DIGITS && value.all(Char::isDigit)
