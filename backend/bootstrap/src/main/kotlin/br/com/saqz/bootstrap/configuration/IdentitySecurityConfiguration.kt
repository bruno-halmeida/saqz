package br.com.saqz.bootstrap.configuration

import br.com.saqz.bootstrap.configuration.http.ApiProblemWriter
import br.com.saqz.bootstrap.configuration.http.AsaasWebhookBodySizeFilter
import br.com.saqz.bootstrap.configuration.http.ClientAppFilter
import br.com.saqz.bootstrap.configuration.http.RequestCorrelationFilter
import br.com.saqz.access.adapter.input.http.PlatformAdminGuardFilter
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.clientapp.RecordClientApp
import br.com.saqz.identity.adapter.input.http.BearerAuthenticationFilter
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.ErrorCode
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class IdentitySecurityConfiguration {
    @Bean
    fun apiProblemWriter(objectMapper: ObjectMapper) = ApiProblemWriter(objectMapper)

    @Bean
    fun requestCorrelationFilter() = RequestCorrelationFilter()

    /**
     * Sem banco (testes de endpoint) não há [RecordClientApp]: o filtro segue logando a versão do app e
     * só deixa de guardá-la.
     */
    @Bean
    fun clientAppFilter(recordClientApp: ObjectProvider<RecordClientApp>) =
        ClientAppFilter { recordClientApp.getIfAvailable() }

    @Bean
    fun asaasWebhookBodySizeFilter(problemWriter: ApiProblemWriter) =
        AsaasWebhookBodySizeFilter { request, response ->
            problemWriter.write(request, response, 413)
        }

    @Bean
    fun bearerAuthenticationFilter(
        verifyRequestIdentity: VerifyRequestIdentity,
        problemWriter: ApiProblemWriter,
    ) = BearerAuthenticationFilter(
        verifyRequestIdentity,
        ANONYMOUS_PATHS,
        OPTIONAL_AUTHENTICATION_PATHS,
        EXACT_ANONYMOUS_PATHS,
        additionalAnonymousRequest = { request -> PUBLIC_RECEIVABLES_READS.any { it.matches(request) } },
        // Dinheiro e exclusão nunca usam o cache do token: revogação vale na hora.
        alwaysVerify = { request ->
            request.method != "GET" && MONEY_ROUTES.any { it.matches(request) } ||
                SENSITIVE_DELETES.any { it.matches(request) }
        },
    ) { request, response, status, code ->
        problemWriter.write(request, response, status, code)
    }

    /**
     * A guarda resolve o lookup por ObjectProvider: em contexto sem datasource (testes de
     * endpoint) não há bean de lookup e a resposta é negar — /admin nunca abre por engano.
     */
    @Bean
    fun platformAdminGuardFilter(
        platformAdminLookup: ObjectProvider<PlatformAdminLookup>,
        problemWriter: ApiProblemWriter,
    ) = PlatformAdminGuardFilter({ platformAdminLookup.getIfAvailable() }) { request, response, status, code ->
        problemWriter.write(request, response, status, code)
    }

    /**
     * Ambos rodam só na cadeia de segurança: sem desligar o auto-registro do Boot eles
     * também rodariam no container e derrubariam o preflight CORS (OPTIONS sem bearer).
     */
    @Bean
    fun bearerFilterRegistration(filter: BearerAuthenticationFilter) =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun platformAdminGuardFilterRegistration(filter: PlatformAdminGuardFilter) =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    /** Também só na cadeia de segurança: o registro depende de o Bearer já ter autenticado a requisição. */
    @Bean
    fun clientAppFilterRegistration(filter: ClientAppFilter) =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    /** Origens do adm-web (lista separada por vírgula); vazio = nenhum CORS liberado. */
    @Bean
    fun corsConfigurationSource(
        @Value("\${saqz.adminweb.origins:}") origins: String,
    ): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/public/receivables/**", CorsConfiguration().apply {
            allowedOrigins = listOf("*")
            allowedMethods = listOf("GET", "OPTIONS")
            allowedHeaders = listOf("Accept")
            allowCredentials = false
        })
        val allowed = origins.split(',').map(String::trim).filter(String::isNotEmpty)
        if (allowed.isNotEmpty()) {
            val configuration = CorsConfiguration().apply {
                allowedOrigins = allowed
                allowedMethods = listOf("GET", "POST", "OPTIONS")
                allowedHeaders = listOf("Authorization", "Content-Type")
            }
            // Register before /admin/**: the first matching CORS rule wins.
            val rolloutConfiguration = CorsConfiguration(configuration).apply {
                allowedMethods = listOf("GET", "PUT", "OPTIONS")
            }
            listOf("/admin/receivables/rollout", "/admin/receivables/rollout/users/*").forEach { path ->
                source.registerCorsConfiguration(path, rolloutConfiguration)
            }
            WEB_PATHS.forEach { path -> source.registerCorsConfiguration(path, configuration) }
            source.registerCorsConfiguration("/api/session", CorsConfiguration(configuration).apply {
                allowedMethods = listOf("PUT", "OPTIONS")
            })
            source.registerCorsConfiguration("/api/session/profile", CorsConfiguration(configuration).apply {
                allowedMethods = listOf("PATCH", "OPTIONS")
            })
            source.registerCorsConfiguration("/api/session/app-link", CorsConfiguration(configuration).apply {
                allowedMethods = listOf("POST", "OPTIONS")
            })
            source.registerCorsConfiguration("/api/session/app-link/redeem", CorsConfiguration(configuration).apply {
                allowedMethods = listOf("POST", "OPTIONS")
            })
            source.registerCorsConfiguration("/api/session/onboarding", CorsConfiguration(configuration).apply {
                allowedMethods = listOf("GET", "PUT", "OPTIONS")
            })
        }
        return source
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        bearerAuthenticationFilter: BearerAuthenticationFilter,
        platformAdminGuardFilter: PlatformAdminGuardFilter,
        requestCorrelationFilter: RequestCorrelationFilter,
        clientAppFilter: ClientAppFilter,
        asaasWebhookBodySizeFilter: AsaasWebhookBodySizeFilter,
        problemWriter: ApiProblemWriter,
    ): SecurityFilterChain = http
        .cors { }
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers(
                org.springframework.http.HttpMethod.GET,
                "/public/receivables/terms/*", "/public/receivables/checkout-return",
            ).permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/api/password-reset/**").permitAll()
                .requestMatchers("/api/invites/preview").permitAll()
                .requestMatchers("/subscriptions/checkout-login").permitAll()
                .requestMatchers("/api/session/app-link/redeem").permitAll()
                .requestMatchers("/public/account-confirmation/*").permitAll()
                .requestMatchers("/webhooks/asaas", "/api/receivables/webhooks/asaas/*").permitAll()
                .requestMatchers("/webhooks/app-store", "/webhooks/google-play").permitAll()
                // Segunda barreira do /admin: a guarda (filtro) decide e deixa o admin aprovado na requisição;
                // aqui o Spring Security exige essa aprovação pelo caminho decodificado, como o MVC roteia.
                .requestMatchers(PlatformAdminGuardFilter.ADMIN_PATTERN).access(PLATFORM_ADMIN_APPROVED)
                .anyRequest().authenticated()
        }
        .exceptionHandling {
            it.authenticationEntryPoint { request, response, _ ->
                problemWriter.write(request, response, 401, ErrorCode.AUTHENTICATION_REQUIRED)
            }
            it.accessDeniedHandler { request, response, _ ->
                problemWriter.write(request, response, 403, ErrorCode.ACCESS_FORBIDDEN)
            }
        }
        .addFilterBefore(bearerAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        .addFilterAfter(platformAdminGuardFilter, BearerAuthenticationFilter::class.java)
        .addFilterBefore(requestCorrelationFilter, BearerAuthenticationFilter::class.java)
        // Mesma posição do RequestCorrelationFilter, logo depois dele (o Spring Security mantém a ordem de
        // inserção entre filtros de mesma posição): dentro do MDC dele e antes do Bearer, para o 401 também
        // sair no log com a versão do app.
        .addFilterBefore(clientAppFilter, BearerAuthenticationFilter::class.java)
        .addFilterAfter(asaasWebhookBodySizeFilter, RequestCorrelationFilter::class.java)
        .build()

    private companion object {
        /** Leituras públicas de recebíveis: termos (uma versão por segmento) e a volta do checkout. */
        val PUBLIC_RECEIVABLES_READS: List<RequestMatcher> = listOf(
            matcher(HttpMethod.GET, "/public/receivables/checkout-return"),
            matcher(HttpMethod.GET, "/public/receivables/terms/*"),
        )
        val MONEY_ROUTES: List<RequestMatcher> = listOf(matcher("/subscriptions/**"), matcher("/api/receivables/**"))
        val SENSITIVE_DELETES: List<RequestMatcher> = listOf(
            matcher(HttpMethod.DELETE, "/api/session"),
            matcher(HttpMethod.DELETE, "/api/groups/*"),
        )

        /** Só passa quem a [PlatformAdminGuardFilter] aprovou nesta mesma requisição. */
        val PLATFORM_ADMIN_APPROVED = AuthorizationManager<RequestAuthorizationContext> { _, context ->
            AuthorizationDecision(PlatformAdminGuardFilter.approvedAdmin(context.request) != null)
        }

        fun matcher(pattern: String): RequestMatcher = PathPatternRequestMatcher.withDefaults().matcher(pattern)

        fun matcher(method: HttpMethod, pattern: String): RequestMatcher =
            PathPatternRequestMatcher.withDefaults().matcher(method, pattern)

        /**
         * Quem esqueceu a senha não tem sessão: os três passos do VUL-80 passam sem bearer.
         * O link do e-mail de assinar também chega antes de existir sessão Firebase.
         */
        val ANONYMOUS_PATHS = setOf(
            "/actuator/health",
            "/actuator/prometheus",
            "/api/password-reset",
            "/subscriptions/checkout-login",
            "/webhooks/asaas",
            "/webhooks/app-store",
            "/webhooks/google-play",
            "/api/receivables/webhooks/asaas",
            "/public/account-confirmation",
        )
        val EXACT_ANONYMOUS_PATHS = setOf("/api/session/app-link/redeem")
        val OPTIONAL_AUTHENTICATION_PATHS = setOf("/api/invites/preview")

        /**
         * As duas superfícies web servidas na origem do painel: o admin e a página de
         * assinar (VUL-207). Registro por path é deliberado — o resto da API é só mobile.
         */
        val WEB_PATHS = listOf("/admin/**", "/plans", "/coupons/validate", "/subscriptions/**")
    }
}
