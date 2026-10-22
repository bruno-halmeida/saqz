package br.com.saqz.identity.adapter.input.http

import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.ErrorCode
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Os caminhos anônimos são comparados decodificados e por segmento, com o mesmo casador que o
 * Spring MVC usa para rotear. Um prefixo cobre a si mesmo e tudo abaixo dele; o exato, só ele.
 */
class BearerAuthenticationFilter(
    private val verifyRequestIdentity: VerifyRequestIdentity,
    anonymousPaths: Set<String> = setOf("/actuator/health"),
    optionalAuthenticationPaths: Set<String> = emptySet(),
    exactAnonymousPaths: Set<String> = emptySet(),
    private val additionalAnonymousRequest: (HttpServletRequest) -> Boolean = { false },
    private val alwaysVerify: (HttpServletRequest) -> Boolean = { false },
    private val writeProblem: (HttpServletRequest, HttpServletResponse, Int, ErrorCode?) -> Unit,
) : OncePerRequestFilter() {
    private val anonymous = anonymousPaths.map(::prefix)
    private val optionalAuthentication = optionalAuthenticationPaths.map(::prefix)
    private val exactAnonymous = exactAnonymousPaths.map(::exact)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        additionalAnonymousRequest(request) || exactAnonymous.any { it.matches(request) } ||
            anonymous.any { it.matches(request) }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = bearerToken(request.getHeader("Authorization"))
        if (token == null) {
            if (optionalAuthentication.any { it.matches(request) }) {
                filterChain.doFilter(request, response)
                return
            }
            writeProblem(request, response, 401, ErrorCode.AUTHENTICATION_REQUIRED)
            return
        }

        val raw = RawIdentityToken(token)
        val outcome = if (alwaysVerify(request)) verifyRequestIdentity.executeFresh(raw) else verifyRequestIdentity.execute(raw)
        when (val verification = outcome) {
            TokenVerification.Rejected -> writeProblem(request, response, 401, ErrorCode.AUTHENTICATION_REQUIRED)
            TokenVerification.ProviderUnavailable ->
                writeProblem(request, response, 503, ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE)
            is TokenVerification.Verified -> {
                val context = SecurityContextHolder.createEmptyContext()
                context.authentication = UsernamePasswordAuthenticationToken(
                    verification.principal,
                    null,
                    emptyList(),
                )
                // Removido pelo MDC.clear() do RequestCorrelationFilter, que envolve este filtro.
                MDC.put("subject", verification.principal.subject)
                SecurityContextHolder.setContext(context)
                filterChain.doFilter(request, response)
            }
        }
    }

    private fun bearerToken(header: String?): String? {
        if (header == null || !header.startsWith("Bearer ", ignoreCase = true)) return null
        val token = header.substringAfter(' ').trim()
        return token.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) }
    }

    private companion object {
        fun prefix(path: String): RequestMatcher = PathPatternRequestMatcher.withDefaults().matcher("$path/**")

        fun exact(path: String): RequestMatcher = PathPatternRequestMatcher.withDefaults().matcher(path)
    }
}
