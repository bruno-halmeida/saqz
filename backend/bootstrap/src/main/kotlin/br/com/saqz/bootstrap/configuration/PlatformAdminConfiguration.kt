package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.input.http.PlatformAdminMeController
import br.com.saqz.access.adapter.output.jdbc.admin.JdbcPlatformAdminRepository
import br.com.saqz.access.application.admin.PlatformAdminLookup
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class PlatformAdminConfiguration {
    @Bean
    fun platformAdminLookup(dataSource: DataSource): PlatformAdminLookup =
        JdbcPlatformAdminRepository(dataSource)

    @Bean
    fun platformAdminMeController(lookup: PlatformAdminLookup) = PlatformAdminMeController(lookup)
}
