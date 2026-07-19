package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.input.http.AccessGroupController
import br.com.saqz.groups.adapter.input.http.AccessGroupReadController
import br.com.saqz.groups.adapter.input.http.AccessGroupSettingsController
import br.com.saqz.groups.adapter.input.http.AccessInviteManagementController
import br.com.saqz.groups.adapter.input.http.AccessInviteRedemptionController
import br.com.saqz.groups.adapter.input.http.AccessMembershipController
import br.com.saqz.access.adapter.input.http.AccessSessionController
import br.com.saqz.groups.adapter.output.crypto.JcaSecureTokenGenerator
import br.com.saqz.groups.adapter.output.jdbc.group.create.JdbcGroupCreationRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.group.settings.JdbcGroupSettingsRepository
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteManagementRepository
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteRedemptionRepository
import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcMembershipRepository
import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.link.BranchInviteLinkFactory
import br.com.saqz.groups.application.create.CreateGroup
import br.com.saqz.groups.application.read.GetGroup
import br.com.saqz.groups.application.settings.UpdateGroupSettings
import br.com.saqz.groups.application.invite.manage.ExpireInvite
import br.com.saqz.groups.application.invite.manage.RotateInvite
import br.com.saqz.groups.application.invite.redeem.RedeemInvite
import br.com.saqz.groups.application.membership.ChangeMemberRole
import br.com.saqz.groups.application.membership.ListAccessMemberships
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.adapter.input.http.EmailNotVerifiedException
import br.com.saqz.groups.adapter.input.http.InvalidDisplayNameException
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.flywaydb.core.Flyway
import org.springframework.core.env.Environment
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.net.URI
import java.time.Clock
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class AccessSessionConfiguration {
    @Bean
    fun accessDataSource(environment: Environment): DataSource = DriverManagerDataSource(
        environment.getRequiredProperty("spring.datasource.url"),
        environment.getProperty("spring.datasource.username").orEmpty(),
        environment.getProperty("spring.datasource.password").orEmpty(),
    )

    @Bean(initMethod = "migrate")
    fun accessFlyway(dataSource: DataSource): Flyway = Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()

    @Bean
    fun sessionRepository(dataSource: DataSource) = JdbcSessionRepository(dataSource)

    @Bean
    fun bootstrapSession(repository: JdbcSessionRepository) = BootstrapSession(repository)

    @Bean
    fun verifiedGroupActorResolver(bootstrapSession: BootstrapSession) = VerifiedGroupActorResolver { identity ->
        when (val result = bootstrapSession.execute(identity)) {
            BootstrapSessionResult.EmailNotVerified -> throw EmailNotVerifiedException()
            BootstrapSessionResult.InvalidDisplayName -> throw InvalidDisplayNameException()
            is BootstrapSessionResult.Success -> result.session.user.id
        }
    }

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
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        createGroup: CreateGroup,
    ) = AccessGroupController(verifiedGroupActorResolver, createGroup)

    @Bean
    fun groupReadRepository(dataSource: DataSource) = JdbcGroupReadRepository(dataSource)

    @Bean
    fun getGroup(repository: JdbcGroupReadRepository) = GetGroup(repository, GroupAccessPolicy())

    @Bean
    fun accessGroupReadController(
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        getGroup: GetGroup,
    ) = AccessGroupReadController(verifiedGroupActorResolver, getGroup)

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
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        updateGroupSettings: UpdateGroupSettings,
    ) = AccessGroupSettingsController(verifiedGroupActorResolver, updateGroupSettings)

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
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        listAccessMemberships: ListAccessMemberships,
        changeMemberRole: ChangeMemberRole,
    ) = AccessMembershipController(verifiedGroupActorResolver, listAccessMemberships, changeMemberRole)

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
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        rotateInvite: RotateInvite,
        expireInvite: ExpireInvite,
    ) = AccessInviteManagementController(verifiedGroupActorResolver, rotateInvite, expireInvite)

    @Bean
    fun inviteRedemptionRepository(dataSource: DataSource) = JdbcInviteRedemptionRepository(dataSource)

    @Bean
    fun redeemInvite(
        transaction: JdbcTransactionRunner,
        repository: JdbcInviteRedemptionRepository,
    ) = RedeemInvite(transaction, repository, Clock.systemUTC())

    @Bean
    fun accessInviteRedemptionController(
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        redeemInvite: RedeemInvite,
    ) = AccessInviteRedemptionController(verifiedGroupActorResolver, redeemInvite)
}
