package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.sharedkernel.ErrorCode
import br.com.saqz.sharedkernel.RequestIdentity
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Guarda das rotas sob o prefixo /admin: além do bearer válido (filtro anterior na
 * cadeia), o sujeito precisa estar na lista de admins de plataforma. Sem provedor de
 * lookup disponível a resposta é negar — a guarda nunca abre por ausência de fiação.
 */
class PlatformAdminGuardFilter(
    private val lookupProvider: () -> PlatformAdminLookup?,
    private val writeProblem: (HttpServletRequest, HttpServletResponse, Int, ErrorCode?) -> Unit,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI != "/admin" && !request.requestURI.startsWith("/admin/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val identity = SecurityContextHolder.getContext().authentication?.principal as? RequestIdentity
        if (identity == null) {
            writeProblem(request, response, 401, ErrorCode.AUTHENTICATION_REQUIRED)
            return
        }
        val admin = lookupProvider()?.findBySubject(identity.subject)
        if (admin == null) {
            writeProblem(request, response, 403, ErrorCode.ACCESS_FORBIDDEN)
            return
        }
        filterChain.doFilter(request, response)
    }
}
