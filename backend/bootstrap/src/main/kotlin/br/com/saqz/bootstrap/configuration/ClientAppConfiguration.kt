package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.output.jdbc.clientapp.JdbcClientAppVersionStore
import br.com.saqz.access.application.clientapp.ClientAppVersionStore
import br.com.saqz.access.application.clientapp.RecordClientApp
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/** A última versão do app por usuário e plataforma; só existe com banco. O filtro HTTP é o `ClientAppFilter`. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class ClientAppConfiguration {
    @Bean
    fun clientAppVersionStore(dataSource: DataSource): ClientAppVersionStore = JdbcClientAppVersionStore(dataSource)

    @Bean
    fun recordClientApp(store: ClientAppVersionStore) = RecordClientApp(store)
}
