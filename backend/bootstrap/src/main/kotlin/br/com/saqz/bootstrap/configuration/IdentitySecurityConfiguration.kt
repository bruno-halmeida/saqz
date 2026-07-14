package br.com.saqz.bootstrap.configuration

import br.com.saqz.identity.adapter.input.http.BearerAuthenticationFilter
import br.com.saqz.identity.adapter.input.http.SessionController
import br.com.saqz.identity.adapter.input.http.RequestCorrelationFilter
import br.com.saqz.identity.adapter.input.http.SafeExceptionHandler
import br.com.saqz.identity.adapter.input.http.writeProblem
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.ErrorCode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration(proxyBeanMethods = false)
class IdentitySecurityConfiguration {
    @Bean
    fun sessionController() = SessionController()

    @Bean
    fun safeExceptionHandler() = SafeExceptionHandler()

    @Bean
    fun requestCorrelationFilter() = RequestCorrelationFilter()

    @Bean
    fun bearerAuthenticationFilter(verifyRequestIdentity: VerifyRequestIdentity) =
        BearerAuthenticationFilter(verifyRequestIdentity)

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        bearerAuthenticationFilter: BearerAuthenticationFilter,
        requestCorrelationFilter: RequestCorrelationFilter,
    ): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
        }
        .exceptionHandling {
            it.authenticationEntryPoint { request, response, _ ->
                writeProblem(request, response, 401, ErrorCode.AUTHENTICATION_REQUIRED)
            }
        }
        .addFilterBefore(bearerAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        .addFilterBefore(requestCorrelationFilter, BearerAuthenticationFilter::class.java)
        .build()
}
