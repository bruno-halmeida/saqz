package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.input.http.MonthlyInviteController
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.crypto.JcaSecureTokenGenerator
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.monthlyinvite.JdbcMonthlyInviteLinkRepository
import br.com.saqz.groups.adapter.output.jdbc.monthlyinvite.JdbcMonthlyInviteRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.link.PublicInviteLinkFactory
import br.com.saqz.groups.application.monthlyinvite.GetOrRenewMonthlyInviteLink
import br.com.saqz.groups.application.monthlyinvite.InvalidateMonthlyInviteLink
import br.com.saqz.groups.application.monthlyinvite.ManageMonthlyInvites
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteDispatcher
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import javax.sql.DataSource

/**
 * Convite de mensalistas (DEB-106). Sempre registrado; a fila de envio ([MonthlyInviteDispatcher]) só existe com o
 * WhatsApp ligado e, sem ela, o lote responde `WHATSAPP_UNAVAILABLE` e o app mostra o link para copiar.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class MonthlyInviteConfiguration {
    @Bean
    fun monthlyInviteLinkRepository(dataSource: DataSource) = JdbcMonthlyInviteLinkRepository(dataSource)

    @Bean
    fun monthlyInviteRepository(dataSource: DataSource) = JdbcMonthlyInviteRepository(dataSource)

    @Bean
    fun getOrRenewMonthlyInviteLink(
        repository: JdbcMonthlyInviteLinkRepository,
        tokenGenerator: JcaSecureTokenGenerator,
        linkFactory: PublicInviteLinkFactory,
        clock: Clock,
    ) = GetOrRenewMonthlyInviteLink(repository, tokenGenerator, linkFactory, clock)

    @Bean
    fun invalidateMonthlyInviteLink(
        transaction: JdbcTransactionRunner,
        readRepository: JdbcGroupReadRepository,
        repository: JdbcMonthlyInviteLinkRepository,
        clock: Clock,
    ) = InvalidateMonthlyInviteLink(transaction, readRepository, repository, GroupAccessPolicy(), clock)

    @Bean
    fun manageMonthlyInvites(
        transaction: JdbcTransactionRunner,
        readRepository: JdbcGroupReadRepository,
        invitees: JdbcMonthlyInviteRepository,
        links: JdbcMonthlyInviteLinkRepository,
        renewLink: GetOrRenewMonthlyInviteLink,
        linkFactory: PublicInviteLinkFactory,
        subscriptionLimits: SubscriptionLimits,
        writeAccess: GroupWriteAccess,
        dispatcher: ObjectProvider<MonthlyInviteDispatcher>,
        clock: Clock,
    ) = ManageMonthlyInvites(
        transaction, readRepository, GroupAccessPolicy(), invitees, links, renewLink, linkFactory,
        subscriptionLimits, writeAccess, dispatcher.ifAvailable, clock,
    )

    @Bean
    fun monthlyInviteController(
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        manage: ManageMonthlyInvites,
        invalidate: InvalidateMonthlyInviteLink,
    ) = MonthlyInviteController(verifiedGroupActorResolver, manage, invalidate)
}
