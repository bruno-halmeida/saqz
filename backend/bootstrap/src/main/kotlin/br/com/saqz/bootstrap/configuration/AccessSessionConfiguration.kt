package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.input.http.AccessGroupController
import br.com.saqz.access.adapter.input.http.AccessGroupReadController
import br.com.saqz.access.adapter.input.http.AccessSessionController
import br.com.saqz.access.adapter.output.jdbc.group.create.JdbcGroupCreationRepository
import br.com.saqz.access.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
import br.com.saqz.access.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.access.application.group.create.CreateGroup
import br.com.saqz.access.application.group.read.GetGroup
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.domain.GroupAccessPolicy
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

    @Bean
    fun groupCreationRepository(dataSource: DataSource) = JdbcGroupCreationRepository(dataSource)

    @Bean
    fun accessTransactionRunner(dataSource: DataSource) = JdbcTransactionRunner(dataSource)

    @Bean
    fun createGroup(
        transaction: JdbcTransactionRunner,
        repository: JdbcGroupCreationRepository,
    ) = CreateGroup(transaction, repository)

    @Bean
    fun accessGroupController(
        bootstrapSession: BootstrapSession,
        createGroup: CreateGroup,
    ) = AccessGroupController(bootstrapSession, createGroup)

    @Bean
    fun groupReadRepository(dataSource: DataSource) = JdbcGroupReadRepository(dataSource)

    @Bean
    fun getGroup(repository: JdbcGroupReadRepository) = GetGroup(repository, GroupAccessPolicy())

    @Bean
    fun accessGroupReadController(
        bootstrapSession: BootstrapSession,
        getGroup: GetGroup,
    ) = AccessGroupReadController(bootstrapSession, getGroup)
}
