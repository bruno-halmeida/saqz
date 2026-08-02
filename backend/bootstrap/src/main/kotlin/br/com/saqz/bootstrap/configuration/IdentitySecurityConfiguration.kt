package br.com.saqz.bootstrap.configuration

import br.com.saqz.bootstrap.configuration.http.ApiProblemWriter
import br.com.saqz.bootstrap.configuration.http.AsaasWebhookBodySizeFilter
import br.com.saqz.bootstrap.configuration.http.RequestCorrelationFilter
import br.com.saqz.identity.adapter.input.http.BearerAuthenticationFilter
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.ErrorCode
import org.springframework.context.annotation.Bean
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
    ) { request, response, status, code ->
        problemWriter.write(request, response, status, code)
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        bearerAuthenticationFilter: BearerAuthenticationFilter,
        requestCorrelationFilter: RequestCorrelationFilter,
        asaasWebhookBodySizeFilter: AsaasWebhookBodySizeFilter,
        problemWriter: ApiProblemWriter,
    ): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/password-reset/**").permitAll()
                .requestMatchers("/api/invites/preview").permitAll()
                .requestMatchers("/webhooks/asaas").permitAll()
                .anyRequest().authenticated()
        }
        .exceptionHandling {
            it.authenticationEntryPoint { request, response, _ ->
                problemWriter.write(request, response, 401, ErrorCode.AUTHENTICATION_REQUIRED)
            }
        }
        .addFilterBefore(bearerAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        .addFilterBefore(requestCorrelationFilter, BearerAuthenticationFilter::class.java)
        .addFilterAfter(asaasWebhookBodySizeFilter, RequestCorrelationFilter::class.java)
        .build()

    private companion object {
        /** Quem esqueceu a senha não tem sessão: os três passos do VUL-80 passam sem bearer. */
        val ANONYMOUS_PATHS = setOf("/actuator/health", "/api/password-reset", "/webhooks/asaas")
        val OPTIONAL_AUTHENTICATION_PATHS = setOf("/api/invites/preview")
    }
}
