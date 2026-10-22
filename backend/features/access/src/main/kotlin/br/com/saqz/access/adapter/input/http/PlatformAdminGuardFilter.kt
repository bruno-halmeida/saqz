package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.sharedkernel.ErrorCode
import br.com.saqz.sharedkernel.RequestIdentity
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Guarda das rotas sob o prefixo /admin: além do bearer válido (filtro anterior na
 * cadeia), o sujeito precisa estar na lista de admins de plataforma. Sem provedor de
 * lookup disponível a resposta é negar — a guarda nunca abre por ausência de fiação.
 *
 * O caminho é comparado decodificado e por segmento, com o mesmo casador que o Spring MVC
 * usa para rotear. Comparar o `requestURI` cru deixava `/%61dmin/...` passar reto por aqui
 * e cair no controller de `/admin/...` com qualquer token válido. Quem passa deixa o admin
 * resolvido em [ADMIN_ATTRIBUTE]: a regra de autorização do Spring Security exige esse
 * atributo em toda rota sob `/admin` ([ADMIN_PATTERN]), então um caminho que escape desta guarda é negado lá.
 */
class PlatformAdminGuardFilter(
    private val lookupProvider: () -> PlatformAdminLookup?,
    private val writeProblem: (HttpServletRequest, HttpServletResponse, Int, ErrorCode?) -> Unit,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !ADMIN_ROUTES.matches(request)

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
        request.setAttribute(ADMIN_ATTRIBUTE, admin)
        filterChain.doFilter(request, response)
    }

    companion object {
        const val ADMIN_PATTERN = "/admin/**"
        private const val ADMIN_ATTRIBUTE = "br.com.saqz.access.platformAdmin"
        private val ADMIN_ROUTES = PathPatternRequestMatcher.withDefaults().matcher(ADMIN_PATTERN)

        /** O admin que esta guarda aprovou nesta requisição; null quando ela não rodou ou negou. */
        fun approvedAdmin(request: HttpServletRequest): PlatformAdminView? =
            request.getAttribute(ADMIN_ATTRIBUTE) as? PlatformAdminView
    }
}
