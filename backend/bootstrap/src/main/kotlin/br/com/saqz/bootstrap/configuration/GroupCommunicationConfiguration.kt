package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.input.http.GroupCommunicationController
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGroupCommunicationRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.GroupCommunicationService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class GroupCommunicationConfiguration {
    @Bean fun chargeReminderStore(dataSource: DataSource) = br.com.saqz.groups.adapter.output.jdbc.communication.JdbcChargeReminderStore(dataSource)
    @Bean fun chargeReminderService(transaction: JdbcTransactionRunner, groups: JdbcGroupReadRepository,
        repository: JdbcGroupCommunicationRepository, store: br.com.saqz.groups.adapter.output.jdbc.communication.JdbcChargeReminderStore) =
        br.com.saqz.groups.application.communication.ChargeReminderService(transaction, groups, repository, store)
    @Bean fun chargeReminderController(actors: VerifiedGroupActorResolver, service: br.com.saqz.groups.application.communication.ChargeReminderService) =
        br.com.saqz.groups.adapter.input.http.ChargeReminderController(actors, service)
    @Bean fun groupCommunicationRepository(dataSource: DataSource) = JdbcGroupCommunicationRepository(dataSource)
    @Bean fun groupCommunicationService(transaction: JdbcTransactionRunner, groups: JdbcGroupReadRepository, repository: JdbcGroupCommunicationRepository) =
        GroupCommunicationService(transaction, groups, repository)
    @Bean fun groupCommunicationController(actors: VerifiedGroupActorResolver, service: GroupCommunicationService) =
        GroupCommunicationController(actors, service)
}
