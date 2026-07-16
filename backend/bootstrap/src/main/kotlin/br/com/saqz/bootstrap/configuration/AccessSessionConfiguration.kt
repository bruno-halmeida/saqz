package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.input.http.AccessSessionController
import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
import br.com.saqz.access.application.session.BootstrapSession
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class AccessSessionConfiguration {
    @Bean
    fun sessionRepository(dataSource: DataSource) = JdbcSessionRepository(dataSource)

    @Bean
    fun bootstrapSession(repository: JdbcSessionRepository) = BootstrapSession(repository)

    @Bean
    fun accessSessionController(useCase: BootstrapSession) = AccessSessionController(useCase)
}
