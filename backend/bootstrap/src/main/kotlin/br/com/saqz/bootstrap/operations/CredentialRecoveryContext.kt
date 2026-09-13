package br.com.saqz.bootstrap.operations

import br.com.saqz.bootstrap.configuration.ReceivablesConfiguration
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasCredentialRecovery
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialCredentialRecovery
import br.com.saqz.receivables.application.RecoverFinancialCredential
import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.net.URI
import java.time.Clock
import javax.sql.DataSource

internal fun credentialRecoveryContext(): ConfigurableApplicationContext =
    SpringApplicationBuilder(CredentialRecoveryContext::class.java)
        .web(WebApplicationType.NONE)
        .bannerMode(Banner.Mode.OFF)
        .logStartupInfo(false)
        .properties(mapOf("logging.level.root" to "OFF", "spring.main.register-shutdown-hook" to "false"))
        .run()

/** Explicit source only: no component stereotype, auto-configuration, web server, migration or scheduled jobs. */
internal class CredentialRecoveryContext {
    @Bean
    fun dataSource(environment: Environment): DataSource = DriverManagerDataSource().apply {
        setUrl(environment.getRequiredProperty("spring.datasource.url"))
        username = environment.getProperty("spring.datasource.username").orEmpty()
        password = environment.getProperty("spring.datasource.password").orEmpty()
    }

    @Bean
    fun secrets(environment: Environment): FinancialSecrets = ReceivablesConfiguration().financialSecrets(environment)

    @Bean
    fun recovery(dataSource: DataSource, secrets: FinancialSecrets, environment: Environment) = RecoverFinancialCredential(
        JdbcFinancialCredentialRecovery(dataSource, secrets),
        HttpAsaasCredentialRecovery(
            URI(environment.getRequiredProperty("saqz.receivables.asaas-base-url")),
            environment.getRequiredProperty("saqz.receivables.asaas-platform-key"),
        ),
        Clock.systemUTC(),
    )
}
