package br.com.saqz.identity.adapter.input.http

import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class BearerAuthenticationFilter(
    private val verifyRequestIdentity: VerifyRequestIdentity,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI == "/actuator/health"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = bearerToken(request.getHeader("Authorization"))
        if (token == null) {
            writeProblem(response, 401, "AUTHENTICATION_REQUIRED")
            return
        }

        when (val verification = verifyRequestIdentity.execute(RawIdentityToken(token))) {
            TokenVerification.Rejected -> writeProblem(response, 401, "AUTHENTICATION_REQUIRED")
            TokenVerification.ProviderUnavailable -> writeProblem(response, 503, "IDENTITY_PROVIDER_UNAVAILABLE")
            is TokenVerification.Verified -> {
                val context = SecurityContextHolder.createEmptyContext()
                context.authentication = UsernamePasswordAuthenticationToken(
                    verification.principal,
                    null,
                    emptyList(),
                )
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
}

fun writeProblem(response: HttpServletResponse, status: Int, code: String) {
    response.status = status
    response.setHeader("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE)
    response.outputStream.write("{\"status\":$status,\"code\":\"$code\"}".toByteArray())
}
