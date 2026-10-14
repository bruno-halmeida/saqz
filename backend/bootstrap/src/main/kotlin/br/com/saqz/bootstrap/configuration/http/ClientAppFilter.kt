package br.com.saqz.bootstrap.configuration.http

import br.com.saqz.access.application.clientapp.RecordClientApp
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.client.CLIENT_APP_REQUEST_ATTRIBUTE
import br.com.saqz.sharedkernel.client.ClientApp
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/** Mesmos nomes que o `HttpTransport` do app manda em toda requisição. */
private const val PLATFORM_HEADER = "X-Saqz-Platform"
private const val VERSION_HEADER = "X-Saqz-App-Version"
private const val BUILD_HEADER = "X-Saqz-App-Build"
private const val MDC_KEY = "app"

/**
 * Descobre qual versão do app fez a chamada. Roda dentro do [RequestCorrelationFilter] e antes da
 * autenticação, para que até a resposta 401 saia no log com a versão:
 *  - valida `X-Saqz-Platform`, `X-Saqz-App-Version` e `X-Saqz-App-Build` (valor fora do padrão é
 *    descartado, nunca recusado) e deixa o [ClientApp] no atributo da requisição;
 *  - põe o texto curto no MDC `app` (`ios/1.0.3(14)`; `-` para o cliente que não manda nada), que o
 *    `logging.pattern.level` imprime em toda linha da requisição.
 *
 * Depois que a cadeia responde, e só para quem se autenticou, pede ao [recorder] para guardar a versão
 * por usuário e plataforma. Isso é por tentativa: nunca lança, e o [RecordClientApp] só toca o banco
 * quando a versão muda ou depois de umas 6 h. O registro roda depois da resposta de propósito: no
 * primeiro `PUT /api/session` a conta só passa a existir dentro do controller.
 *
 * [recorder] devolve `null` quando não há banco (testes de endpoint): nesse caso só o log vale.
 */
class ClientAppFilter(
    private val recorder: () -> RecordClientApp?,
) : OncePerRequestFilter() {
    private val recordLogger = LoggerFactory.getLogger(ClientAppFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val app = ClientApp.fromHeaders(
            request.getHeader(PLATFORM_HEADER),
            request.getHeader(VERSION_HEADER),
            request.getHeader(BUILD_HEADER),
        )
        request.setAttribute(CLIENT_APP_REQUEST_ATTRIBUTE, app)
        // Removido pelo MDC.clear() do RequestCorrelationFilter, que envolve este filtro.
        MDC.put(MDC_KEY, app.label)
        try {
            filterChain.doFilter(request, response)
        } finally {
            record(app)
        }
    }

    private fun record(app: ClientApp) {
        if (app.isLegacy) return
        // O BearerAuthenticationFilter deixa o RequestIdentity aqui; quem ele recusou, ou a rota anônima, não tem.
        val identity = SecurityContextHolder.getContext().authentication?.principal as? RequestIdentity ?: return
        try {
            recorder()?.execute(identity, app)
        } catch (failure: Exception) {
            // Só a classe, como no resto dos diagnósticos: a mensagem de um erro de banco pode trazer dado.
            recordLogger.warn("client_app_record_failed app={} cause={}", app.label, failure.javaClass.simpleName)
        }
    }
}

/** O [ClientApp] da requisição, ou [ClientApp.Legacy] quando o filtro não rodou ou o app não se identificou. */
fun requestClientApp(request: HttpServletRequest): ClientApp =
    ClientApp.fromRequestAttribute(request.getAttribute(CLIENT_APP_REQUEST_ATTRIBUTE))
