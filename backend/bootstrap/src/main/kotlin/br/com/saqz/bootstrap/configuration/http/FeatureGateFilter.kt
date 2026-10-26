package br.com.saqz.bootstrap.configuration.http

import br.com.saqz.access.application.flags.FeatureFlags
import br.com.saqz.access.application.flags.FeatureKeys
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.client.ClientApp
import br.com.saqz.sharedkernel.client.ClientPlatform
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Uma ação nova que só existe com a flag ligada. [appOnly] = só barra quem chama pelo app (Android ou
 * iOS no `ClientApp`): o site (`/assinar`, `/comecar`) usa a mesma rota sem os cabeçalhos do app.
 */
data class FeatureGateRoute(val matcher: RequestMatcher, val key: String, val appOnly: Boolean = false)

/**
 * Recusa com `FEATURE_DISABLED` (403, problem+json) as ações novas das flags que o backend barra
 * (FLG-003) quando a flag está desligada para quem chama. Só a ação nova: consultar, cancelar e
 * manter o que existe passam. A validação das compras da App Store e do Google Play nunca entra aqui.
 *
 * Roda depois do Bearer, com a pessoa já identificada. Sem banco (testes de endpoint) não há
 * [FeatureFlags] e nada é barrado.
 */
class FeatureGateFilter(
    private val features: () -> FeatureFlags?,
    private val routes: List<FeatureGateRoute> = ROUTES,
    private val reject: (HttpServletRequest, HttpServletResponse) -> Unit,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val route = routes.firstOrNull { it.matcher.matches(request) }
        val flags = route?.let { features() }
        if (route == null || flags == null) {
            filterChain.doFilter(request, response)
            return
        }
        val client = requestClientApp(request)
        val fromApp = (client as? ClientApp.Identified)?.platform.let { it == ClientPlatform.ANDROID || it == ClientPlatform.IOS }
        if (route.appOnly && !fromApp) {
            filterChain.doFilter(request, response)
            return
        }
        val identity = SecurityContextHolder.getContext().authentication?.principal as? RequestIdentity
        val userId = identity?.let { flags.userIdBySubject(it.subject) }
        if (flags.enabledFor(route.key, userId, client)) filterChain.doFilter(request, response) else reject(request, response)
    }

    companion object {
        private fun matcher(method: HttpMethod, pattern: String): RequestMatcher =
            PathPatternRequestMatcher.withDefaults().matcher(method, pattern)

        /**
         * Assinatura pelo Asaas: só criar assinatura (mudar de plano, cancelar e reativar são de quem já
         * assina). WhatsApp: só vincular um grupo novo (consultar e ligar/desligar o vínculo continuam).
         * Chat: ainda não há rota no backend.
         */
        val ROUTES: List<FeatureGateRoute> = listOf(
            FeatureGateRoute(matcher(HttpMethod.POST, "/subscriptions"), FeatureKeys.PURCHASES_ASAAS, appOnly = true),
            FeatureGateRoute(matcher(HttpMethod.PUT, "/api/groups/*/whatsapp-binding"), FeatureKeys.WHATSAPP),
        )
    }
}
