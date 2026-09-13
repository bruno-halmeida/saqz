package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.admin.AdminUserDirectory
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.adminweb.http.AdminReceivablesRolloutController
import br.com.saqz.receivables.adapter.output.jdbc.JdbcReceivablesRollout
import br.com.saqz.receivables.application.ReceivablesRollout
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class ReceivablesRolloutConfiguration {
    @Bean fun receivablesRollout(dataSource: DataSource, users: AdminUserDirectory, clock: Clock): ReceivablesRollout =
        JdbcReceivablesRollout(dataSource, { users.find(it) != null }, clock)
    @Bean fun adminReceivablesRolloutController(admins: PlatformAdminLookup, rollout: ReceivablesRollout, actors: SubscriptionActorResolver) =
        AdminReceivablesRolloutController(admins, rollout, actors)
}
