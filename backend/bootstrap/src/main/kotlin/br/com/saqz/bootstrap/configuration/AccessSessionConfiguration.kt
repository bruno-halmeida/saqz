package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.input.http.AccessGroupController
import br.com.saqz.access.adapter.input.http.AccessGroupReadController
import br.com.saqz.access.adapter.input.http.AccessGroupSettingsController
import br.com.saqz.access.adapter.input.http.AccessInviteManagementController
import br.com.saqz.access.adapter.input.http.AccessInviteRedemptionController
import br.com.saqz.access.adapter.input.http.AccessMembershipController
import br.com.saqz.access.adapter.input.http.AccessSessionController
import br.com.saqz.access.adapter.output.crypto.JcaSecureTokenGenerator
import br.com.saqz.access.adapter.output.jdbc.group.create.JdbcGroupCreationRepository
import br.com.saqz.access.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.access.adapter.output.jdbc.group.settings.JdbcGroupSettingsRepository
import br.com.saqz.access.adapter.output.jdbc.invite.JdbcInviteManagementRepository
import br.com.saqz.access.adapter.output.jdbc.invite.JdbcInviteRedemptionRepository
import br.com.saqz.access.adapter.output.jdbc.membership.JdbcMembershipRepository
import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
import br.com.saqz.access.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.access.adapter.output.link.BranchInviteLinkFactory
import br.com.saqz.access.application.group.create.CreateGroup
import br.com.saqz.access.application.group.read.GetGroup
import br.com.saqz.access.application.group.settings.UpdateGroupSettings
import br.com.saqz.access.application.invite.manage.ExpireInvite
import br.com.saqz.access.application.invite.manage.RotateInvite
import br.com.saqz.access.application.invite.redeem.RedeemInvite
import br.com.saqz.access.application.membership.ChangeMemberRole
import br.com.saqz.access.application.membership.ListAccessMemberships
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.domain.GroupAccessPolicy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI
import java.time.Clock
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

    @Bean
    fun groupSettingsRepository(dataSource: DataSource) = JdbcGroupSettingsRepository(dataSource)

    @Bean
    fun updateGroupSettings(
        transaction: JdbcTransactionRunner,
        readRepository: JdbcGroupReadRepository,
        settingsRepository: JdbcGroupSettingsRepository,
    ) = UpdateGroupSettings(transaction, readRepository, settingsRepository, GroupAccessPolicy())

    @Bean
    fun accessGroupSettingsController(
        bootstrapSession: BootstrapSession,
        updateGroupSettings: UpdateGroupSettings,
    ) = AccessGroupSettingsController(bootstrapSession, updateGroupSettings)

    @Bean
    fun membershipRepository(dataSource: DataSource) = JdbcMembershipRepository(dataSource)

    @Bean
    fun listAccessMemberships(
        readRepository: JdbcGroupReadRepository,
        membershipRepository: JdbcMembershipRepository,
    ) = ListAccessMemberships(readRepository, membershipRepository, GroupAccessPolicy())

    @Bean
    fun changeMemberRole(
        transaction: JdbcTransactionRunner,
        readRepository: JdbcGroupReadRepository,
        membershipRepository: JdbcMembershipRepository,
    ) = ChangeMemberRole(transaction, readRepository, membershipRepository, GroupAccessPolicy())

    @Bean
    fun accessMembershipController(
        bootstrapSession: BootstrapSession,
        listAccessMemberships: ListAccessMemberships,
        changeMemberRole: ChangeMemberRole,
    ) = AccessMembershipController(bootstrapSession, listAccessMemberships, changeMemberRole)

    @Bean
    fun inviteManagementRepository(dataSource: DataSource) = JdbcInviteManagementRepository(dataSource)

    @Bean
    fun inviteTokenGenerator() = JcaSecureTokenGenerator()

    @Bean
    fun inviteLinkFactory(@Value("\${saqz.branch.domain}") branchDomain: String) =
        BranchInviteLinkFactory(URI(branchDomain))

    @Bean
    fun rotateInvite(
        transaction: JdbcTransactionRunner,
        readRepository: JdbcGroupReadRepository,
        inviteRepository: JdbcInviteManagementRepository,
        tokenGenerator: JcaSecureTokenGenerator,
        linkFactory: BranchInviteLinkFactory,
    ) = RotateInvite(
        transaction,
        readRepository,
        inviteRepository,
        GroupAccessPolicy(),
        tokenGenerator,
        linkFactory,
    )

    @Bean
    fun expireInvite(
        transaction: JdbcTransactionRunner,
        readRepository: JdbcGroupReadRepository,
        inviteRepository: JdbcInviteManagementRepository,
    ) = ExpireInvite(transaction, readRepository, inviteRepository, GroupAccessPolicy())

    @Bean
    fun accessInviteManagementController(
        bootstrapSession: BootstrapSession,
        rotateInvite: RotateInvite,
        expireInvite: ExpireInvite,
    ) = AccessInviteManagementController(bootstrapSession, rotateInvite, expireInvite)

    @Bean
    fun inviteRedemptionRepository(dataSource: DataSource) = JdbcInviteRedemptionRepository(dataSource)

    @Bean
    fun redeemInvite(
        transaction: JdbcTransactionRunner,
        repository: JdbcInviteRedemptionRepository,
    ) = RedeemInvite(transaction, repository, Clock.systemUTC())

    @Bean
    fun accessInviteRedemptionController(
        bootstrapSession: BootstrapSession,
        redeemInvite: RedeemInvite,
    ) = AccessInviteRedemptionController(bootstrapSession, redeemInvite)
}
