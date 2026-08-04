package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.input.http.AccessGroupController
import br.com.saqz.groups.adapter.input.http.AthleteController
import br.com.saqz.groups.adapter.input.http.AccessGroupReadController
import br.com.saqz.groups.adapter.input.http.AccessGroupSettingsController
import br.com.saqz.groups.adapter.input.http.AccessInviteManagementController
import br.com.saqz.groups.adapter.input.http.AccessInviteRedemptionController
import br.com.saqz.groups.adapter.input.http.AccessInvitePreviewController
import br.com.saqz.groups.adapter.input.http.AccessMembershipController
import br.com.saqz.groups.adapter.input.http.AccessEntryRequestController
import br.com.saqz.groups.adapter.input.http.AttendanceShareController
import br.com.saqz.access.adapter.input.http.AccessSessionController
import br.com.saqz.access.adapter.input.http.PasswordResetController
import br.com.saqz.access.adapter.output.jdbc.passwordreset.JdbcPasswordResetRepository
import br.com.saqz.access.application.passwordreset.PasswordAccounts
import br.com.saqz.access.application.passwordreset.PasswordReset
import br.com.saqz.access.application.passwordreset.ResetCodeNotifier
import br.com.saqz.access.application.passwordreset.ResetSecretHasher
import br.com.saqz.access.application.passwordreset.SecureResetSecrets
import com.google.firebase.FirebaseApp
import org.slf4j.LoggerFactory
import br.com.saqz.groups.adapter.output.crypto.JcaAttendanceLinkTokenGenerator
import br.com.saqz.groups.adapter.output.crypto.JcaSecureTokenGenerator
import br.com.saqz.groups.adapter.output.jdbc.attendance.share.JdbcAttendanceLinkRepository
import br.com.saqz.groups.adapter.output.jdbc.group.create.JdbcGroupCreationRepository
import br.com.saqz.groups.adapter.output.jdbc.group.delete.JdbcGroupDeletionRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.group.settings.JdbcGroupSettingsRepository
import br.com.saqz.groups.adapter.output.jdbc.photo.JdbcGroupPhotoRepository
import br.com.saqz.groups.adapter.output.media.GroupPhotoValidator
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteManagementRepository
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteRedemptionRepository
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInvitePreviewRepository
import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcMembershipRepository
import br.com.saqz.access.adapter.input.http.UserPhotoController
import br.com.saqz.access.adapter.output.jdbc.photo.JdbcUserPhotoRepository
import br.com.saqz.access.adapter.output.media.UserPhotoConverter
import br.com.saqz.access.application.photo.UserPhotoService
import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
import br.com.saqz.access.adapter.output.mail.VerificationCodeMailer
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.link.BranchAttendanceLinkFactory
import br.com.saqz.groups.adapter.output.link.BranchInviteLinkFactory
import br.com.saqz.groups.application.attendance.share.ReadAttendanceShareSnapshot
import br.com.saqz.groups.application.attendance.share.ResolveAttendanceLink
import br.com.saqz.groups.application.attendance.share.RotateAttendanceLink
import br.com.saqz.groups.application.create.CreateGroup
import br.com.saqz.groups.application.delete.DeleteGroup
import br.com.saqz.groups.application.delete.DeleteGroupResult
import br.com.saqz.groups.application.read.GetGroup
import br.com.saqz.groups.application.settings.UpdateGroupSettings
import br.com.saqz.groups.application.invite.manage.ExpireInvite
import br.com.saqz.groups.application.invite.manage.GetInviteMetadata
import br.com.saqz.groups.application.invite.manage.RotateInvite
import br.com.saqz.groups.application.invite.redeem.RedeemInvite
import br.com.saqz.groups.application.invite.preview.AnonymousInvitePreviewRateLimiter
import br.com.saqz.groups.application.invite.preview.PreviewInvite
import br.com.saqz.sharedkernel.subscription.OwnedGroupCounter
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionPlanLookup
import br.com.saqz.subscriptions.application.SubscriptionLimitsAdapter
import br.com.saqz.subscriptions.application.SubscriptionPlanLookup
import br.com.saqz.groups.application.membership.ChangeMemberRole
import br.com.saqz.groups.application.membership.ListAccessMemberships
import br.com.saqz.groups.application.entryrequest.ApproveEntryRequest
import br.com.saqz.groups.application.entryrequest.ListEntryRequests
import br.com.saqz.groups.application.entryrequest.RejectEntryRequest
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRosterRepository
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteStatsRepository
import br.com.saqz.groups.application.athlete.GetOwnAthleteProfile
import br.com.saqz.groups.application.athlete.GetAthleteStats
import br.com.saqz.groups.application.athlete.ListAthletes
import br.com.saqz.groups.application.athlete.RemoveAthlete
import br.com.saqz.groups.application.athlete.UpdateAthlete
import br.com.saqz.groups.application.athlete.UpdateOwnAthleteProfile
import br.com.saqz.groups.application.photo.GroupPhotoService
import br.com.saqz.groups.adapter.input.http.GroupPhotoController
import br.com.saqz.groups.adapter.input.http.GameController
import br.com.saqz.groups.adapter.input.http.ChargeController
import br.com.saqz.groups.adapter.input.http.ExpenseController
import br.com.saqz.groups.adapter.input.http.AttendanceController
import br.com.saqz.groups.adapter.input.http.AutoConfirmationController
import br.com.saqz.groups.adapter.input.http.WeeklySeriesController
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcGameOccurrenceRepository
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcOccurrenceMaterializationRepository
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcSeriesBoundaryRepository
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcWeeklySeriesRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeManagementRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcExpenseRepository
import br.com.saqz.groups.adapter.output.jdbc.attendance.AttendanceChargeAdapter
import br.com.saqz.groups.adapter.output.jdbc.attendance.JdbcAutoConfirmationRepository
import br.com.saqz.groups.adapter.output.jdbc.attendance.JdbcAttendanceCommandRepository
import br.com.saqz.groups.application.attendance.AdjustGameCapacity
import br.com.saqz.groups.application.attendance.AutoConfirmAttendance
import br.com.saqz.groups.application.attendance.AutoConfirmationMaterializationPort
import br.com.saqz.groups.application.attendance.AttendanceDetailQuery
import br.com.saqz.groups.application.attendance.AttendanceRosterQuery
import br.com.saqz.groups.application.attendance.RespondAttendance
import br.com.saqz.groups.application.game.ChangeGameLifecycle
import br.com.saqz.groups.application.game.CreateGame
import br.com.saqz.groups.application.game.EditGame
import br.com.saqz.groups.application.game.GameAttendanceCountSource
import br.com.saqz.groups.application.game.GameSideEffectPort
import br.com.saqz.groups.application.game.GameSideEffects
import br.com.saqz.groups.application.game.GetGame
import br.com.saqz.groups.application.game.ListGames
import br.com.saqz.groups.application.game.recurrence.GameIdFactory
import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeries
import br.com.saqz.groups.application.game.series.ApplySeriesBoundary
import br.com.saqz.groups.application.game.series.WeeklySeriesService
import br.com.saqz.groups.adapter.input.scheduling.MonthlyChargeJob
import br.com.saqz.groups.application.finance.charge.ChargeManagement
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.application.finance.charge.MonthlyChargeSchedule
import br.com.saqz.groups.application.finance.charge.GameFinanceSideEffects
import br.com.saqz.groups.application.finance.expense.ExpenseService
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.access.application.session.CompleteSessionProfile
import br.com.saqz.access.application.session.AccountGroupCleanup
import br.com.saqz.access.application.session.AccountTransactionRunner
import br.com.saqz.access.application.session.DeleteAccount
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.adapter.input.http.InvalidDisplayNameException
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Configuration
import org.flywaydb.core.Flyway
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.EnableScheduling
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
@EnableScheduling
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
            BootstrapSessionResult.InvalidDisplayName -> throw InvalidDisplayNameException()
            is BootstrapSessionResult.Success -> result.session.user.id
        }
    }

    @Bean
    fun completeSessionProfile(repository: JdbcSessionRepository) = CompleteSessionProfile(repository)

    @Bean
    fun deleteAccount(
        transaction: JdbcTransactionRunner,
        repository: JdbcSessionRepository,
        deleteGroup: DeleteGroup,
        athleteRepository: JdbcAthleteRepository,
        dataSource: DataSource,
    ): DeleteAccount {
        val jdbc = JdbcClient.create(dataSource)
        val cleanup = object : AccountGroupCleanup {
            override fun deleteOwnedGroups(ownerUserId: UUID) {
                ownedGroupIds(ownerUserId).forEach { groupId ->
                    when (deleteGroup.execute(ownerUserId, groupId)) {
                        DeleteGroupResult.Success,
                        DeleteGroupResult.GroupNotFound,
                        -> Unit
                        DeleteGroupResult.AccessForbidden ->
                            error("account owner could not soft-delete owned group")
                    }
                }
            }

            override fun removeMemberships(userId: UUID) {
                membershipGroupIds(userId).forEach { groupId ->
                    athleteRepository.remove(groupId, userId)
                }
                jdbc.sql("DELETE FROM group_entry_requests WHERE user_id = :userId")
                    .param("userId", userId)
                    .update()
            }

            private fun ownedGroupIds(ownerUserId: UUID): List<UUID> = jdbc.sql(
                """
                SELECT id
                FROM access_groups
                WHERE owner_user_id = :ownerUserId AND deleted_at IS NULL
                ORDER BY id
                FOR UPDATE
                """.trimIndent(),
            )
                .param("ownerUserId", ownerUserId)
                .query(UUID::class.java)
                .list()
                .filterNotNull()

            private fun membershipGroupIds(userId: UUID): List<UUID> = jdbc.sql(
                """
                SELECT memberships.group_id
                FROM group_memberships memberships
                JOIN access_groups groups ON groups.id = memberships.group_id
                WHERE memberships.user_id = :userId
                  AND groups.owner_user_id <> :userId
                  AND groups.deleted_at IS NULL
                ORDER BY memberships.group_id
                FOR UPDATE OF memberships, groups
                """.trimIndent(),
            )
                .param("userId", userId)
                .query(UUID::class.java)
                .list()
                .filterNotNull()
        }
        return DeleteAccount(
            transactionRunner = object : AccountTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = transaction.inTransaction(block)
            },
            repository = repository,
            groupCleanup = cleanup,
        )
    }

    @Bean
    fun accessSessionController(
        useCase: BootstrapSession,
        profile: CompleteSessionProfile,
        deleteAccount: DeleteAccount,
    ) = AccessSessionController(useCase, profile, deleteAccount)

    @Bean fun userPhotoRepository(dataSource: DataSource) = JdbcUserPhotoRepository(dataSource)
    @Bean fun userPhotoConverter() = UserPhotoConverter()
    @Bean fun userPhotoService(converter: UserPhotoConverter, repository: JdbcUserPhotoRepository) =
        UserPhotoService(converter, repository)
    @Bean fun userPhotoController(bootstrapSession: BootstrapSession, service: UserPhotoService) =
        UserPhotoController(bootstrapSession, service)

    @Bean
    fun verificationCodeMailer(sender: JavaMailSender, @Value("\${saqz.mail.from}") from: String) =
        VerificationCodeMailer(sender, from)

    @Bean
    fun passwordResetRepository(dataSource: DataSource) = JdbcPasswordResetRepository(dataSource)

    @Bean
    fun passwordAccounts(firebaseApp: FirebaseApp): PasswordAccounts = FirebasePasswordAccounts(firebaseApp)

    /**
     * O caso de uso engole a falha de entrega para não virar oráculo de quem tem conta,
     * então o registro do SMTP fora do ar tem que sair daqui — senão ninguém fica sabendo.
     */
    @Bean
    fun resetCodeNotifier(mailer: VerificationCodeMailer) = ResetCodeNotifier { recipient, code, validity ->
        try {
            mailer.send(recipient, code, validity)
        } catch (failure: Exception) {
            LoggerFactory.getLogger(AccessSessionConfiguration::class.java)
                .error("password_reset_mail_failed", failure)
            throw failure
        }
    }

    /** Bean para o teste de validade poder mover o relógio sem trocar a fiação inteira. */
    @Bean
    fun passwordResetClock(): Clock = Clock.systemUTC()

    /**
     * Sem default de propósito: o segredo do HMAC é o que impede um dump do banco de
     * virar tomada de conta, então subir sem ele tem que quebrar alto, como o
     * `saqz.branch.domain`.
     */
    @Bean
    fun resetSecretHasher(@Value("\${saqz.password-reset.secret}") secret: String) = ResetSecretHasher(secret)

    @Bean
    fun passwordReset(
        repository: JdbcPasswordResetRepository,
        accounts: PasswordAccounts,
        notifier: ResetCodeNotifier,
        hasher: ResetSecretHasher,
        clock: Clock,
    ) = PasswordReset(repository, accounts, notifier, SecureResetSecrets(), hasher, clock)

    @Bean
    fun passwordResetController(passwordReset: PasswordReset) = PasswordResetController(passwordReset)

    @Bean
    fun groupCreationRepository(dataSource: DataSource) = JdbcGroupCreationRepository(dataSource)

    @Bean
    fun ownedGroupCounter(repository: JdbcGroupCreationRepository): OwnedGroupCounter =
        OwnedGroupCounter(repository::countOwnedGroups)

    @Bean
    fun accessTransactionRunner(dataSource: DataSource) = JdbcTransactionRunner(dataSource)

    @Bean
    fun subscriptionPlanLookup(dataSource: DataSource): SubscriptionPlanLookup =
        JdbcSubscriptionPlanLookup(dataSource)

    @Bean
    fun subscriptionLimits(lookup: SubscriptionPlanLookup): SubscriptionLimits =
        SubscriptionLimitsAdapter(lookup)

    @Bean
    fun createGroup(
        transaction: JdbcTransactionRunner,
        repository: JdbcGroupCreationRepository,
        subscriptionLimits: SubscriptionLimits,
    ) = CreateGroup(transaction, repository, subscriptionLimits)

    @Bean
    fun groupDeletionRepository(dataSource: DataSource) = JdbcGroupDeletionRepository(dataSource)

    @Bean
    fun deleteGroup(
        transaction: JdbcTransactionRunner,
        repository: JdbcGroupDeletionRepository,
    ) = DeleteGroup(transaction, repository)

    @Bean
    fun accessGroupController(
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        createGroup: CreateGroup,
        getGroup: GetGroup,
        deleteGroup: DeleteGroup,
    ) = AccessGroupController(verifiedGroupActorResolver, createGroup, getGroup, deleteGroup)

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
        getGroup: GetGroup,
    ) = AccessGroupSettingsController(verifiedGroupActorResolver, updateGroupSettings, getGroup)

    @Bean fun groupPhotoRepository(dataSource: DataSource) = JdbcGroupPhotoRepository(dataSource)
    @Bean fun groupPhotoValidator() = GroupPhotoValidator()
    @Bean fun groupPhotoService(
        getGroup: GetGroup,
        validator: GroupPhotoValidator,
        repository: JdbcGroupPhotoRepository,
    ) = GroupPhotoService(getGroup, validator, repository)
    @Bean fun groupPhotoController(
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        service: GroupPhotoService,
    ) = GroupPhotoController(verifiedGroupActorResolver, service)

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
    fun listEntryRequests(
        readRepository: JdbcGroupReadRepository,
        repository: JdbcInviteRedemptionRepository,
    ) = ListEntryRequests(readRepository, repository, GroupAccessPolicy())

    @Bean
    fun approveEntryRequest(
        transaction: JdbcTransactionRunner,
        readRepository: JdbcGroupReadRepository,
        repository: JdbcInviteRedemptionRepository,
        membershipRepository: JdbcMembershipRepository,
        subscriptionLimits: SubscriptionLimits,
    ) = ApproveEntryRequest(
        transaction,
        readRepository,
        repository,
        membershipRepository,
        subscriptionLimits,
        GroupAccessPolicy(),
        Clock.systemUTC(),
    )

    @Bean
    fun rejectEntryRequest(
        transaction: JdbcTransactionRunner,
        readRepository: JdbcGroupReadRepository,
        repository: JdbcInviteRedemptionRepository,
    ) = RejectEntryRequest(transaction, readRepository, repository, GroupAccessPolicy())

    @Bean
    fun accessEntryRequestController(
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        listEntryRequests: ListEntryRequests,
        approveEntryRequest: ApproveEntryRequest,
        rejectEntryRequest: RejectEntryRequest,
    ) = AccessEntryRequestController(
        verifiedGroupActorResolver,
        listEntryRequests,
        approveEntryRequest,
        rejectEntryRequest,
    )

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
        clock: Clock,
    ) = RotateInvite(
        transaction,
        readRepository,
        inviteRepository,
        GroupAccessPolicy(),
        tokenGenerator,
        linkFactory,
        clock,
    )

    @Bean
    fun expireInvite(
        transaction: JdbcTransactionRunner,
        readRepository: JdbcGroupReadRepository,
        inviteRepository: JdbcInviteManagementRepository,
    ) = ExpireInvite(transaction, readRepository, inviteRepository, GroupAccessPolicy())

    @Bean
    fun getInviteMetadata(
        transaction: JdbcTransactionRunner,
        readRepository: JdbcGroupReadRepository,
        inviteRepository: JdbcInviteManagementRepository,
        clock: Clock,
    ) = GetInviteMetadata(transaction, readRepository, inviteRepository, GroupAccessPolicy(), clock)

    @Bean
    fun accessInviteManagementController(
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        rotateInvite: RotateInvite,
        expireInvite: ExpireInvite,
        getInviteMetadata: GetInviteMetadata,
    ) = AccessInviteManagementController(verifiedGroupActorResolver, rotateInvite, expireInvite, getInviteMetadata)

    @Bean
    fun inviteRedemptionRepository(dataSource: DataSource) = JdbcInviteRedemptionRepository(dataSource)

    @Bean
    fun redeemInvite(
        transaction: JdbcTransactionRunner,
        repository: JdbcInviteRedemptionRepository,
        subscriptionLimits: SubscriptionLimits,
        clock: Clock,
    ) = RedeemInvite(transaction, repository, subscriptionLimits, clock)

    @Bean
    fun accessInviteRedemptionController(
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        redeemInvite: RedeemInvite,
    ) = AccessInviteRedemptionController(verifiedGroupActorResolver, redeemInvite)

    @Bean
    fun invitePreviewRepository(dataSource: DataSource) = JdbcInvitePreviewRepository(dataSource)

    @Bean
    fun invitePreviewAnonymousRateLimiter() = AnonymousInvitePreviewRateLimiter()

    @Bean
    fun previewInvite(
        transaction: JdbcTransactionRunner,
        repository: JdbcInvitePreviewRepository,
        anonymousRateLimiter: AnonymousInvitePreviewRateLimiter,
    ) = PreviewInvite(transaction, repository, anonymousRateLimiter, Clock.systemUTC())

    @Bean
    fun accessInvitePreviewController(
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        previewInvite: PreviewInvite,
    ) = AccessInvitePreviewController(verifiedGroupActorResolver, previewInvite)

    @Bean
    fun attendanceLinkRepository(dataSource: DataSource) = JdbcAttendanceLinkRepository(dataSource)

    @Bean
    fun attendanceLinkTokenGenerator() = JcaAttendanceLinkTokenGenerator()

    @Bean
    fun attendanceLinkFactory(@Value("\${saqz.branch.domain}") branchDomain: String) =
        BranchAttendanceLinkFactory(URI(branchDomain))

    @Bean
    fun rotateAttendanceLink(
        transaction: JdbcTransactionRunner,
        repository: JdbcAttendanceLinkRepository,
        tokenGenerator: JcaAttendanceLinkTokenGenerator,
        linkFactory: BranchAttendanceLinkFactory,
    ) = RotateAttendanceLink(
        transaction,
        repository,
        GroupAccessPolicy(),
        tokenGenerator,
        linkFactory,
        Clock.systemUTC(),
    )

    @Bean
    fun resolveAttendanceLink(
        transaction: JdbcTransactionRunner,
        repository: JdbcAttendanceLinkRepository,
    ) = ResolveAttendanceLink(transaction, repository, Clock.systemUTC())

    @Bean
    fun readAttendanceShareSnapshot(
        transaction: JdbcTransactionRunner,
        repository: JdbcAttendanceLinkRepository,
    ) = ReadAttendanceShareSnapshot(transaction, repository, GroupAccessPolicy())

    @Bean
    fun attendanceShareController(
        verifiedGroupActorResolver: VerifiedGroupActorResolver,
        rotateAttendanceLink: RotateAttendanceLink,
        resolveAttendanceLink: ResolveAttendanceLink,
        readAttendanceShareSnapshot: ReadAttendanceShareSnapshot,
    ) = AttendanceShareController(
        verifiedGroupActorResolver,
        rotateAttendanceLink,
        resolveAttendanceLink,
        readAttendanceShareSnapshot,
    )

    @Bean fun gameRepository(dataSource: DataSource) = JdbcGameOccurrenceRepository(dataSource)
    @Bean fun attendanceRepository(dataSource: DataSource) = JdbcAttendanceCommandRepository(dataSource)
    @Bean fun createGame(transaction: JdbcTransactionRunner, repository: JdbcGameOccurrenceRepository) = CreateGame(transaction, repository)
    @Bean fun editGame(transaction: JdbcTransactionRunner, repository: JdbcGameOccurrenceRepository, effects: GameSideEffectPort) = EditGame(transaction, repository, effects)
    @Bean fun changeGameLifecycle(transaction: JdbcTransactionRunner, repository: JdbcGameOccurrenceRepository, effects: GameSideEffectPort) = ChangeGameLifecycle(transaction, repository, effects)
    @Bean fun listGames(repository: JdbcGameOccurrenceRepository, counts: GameAttendanceCountSource) = ListGames(repository, counts)
    @Bean fun getGame(repository: JdbcGameOccurrenceRepository, counts: GameAttendanceCountSource) = GetGame(repository, counts)
    @Bean fun gameController(
        actor: VerifiedGroupActorResolver,
        create: CreateGame,
        edit: EditGame,
        lifecycle: ChangeGameLifecycle,
        list: ListGames,
        get: GetGame,
        attendance: AttendanceDetailQuery,
    ) = GameController(actor, create, edit, lifecycle, list, get, attendance)
    @Bean fun gameIdFactory() = GameIdFactory(java.util.UUID::randomUUID)
    @Bean fun occurrenceMaterializationRepository(dataSource: DataSource) = JdbcOccurrenceMaterializationRepository(dataSource)
    @Bean fun materializeWeeklySeries(
        transaction: JdbcTransactionRunner,
        repository: JdbcOccurrenceMaterializationRepository,
        ids: GameIdFactory,
        autoConfirm: AutoConfirmAttendance,
    ) = MaterializeWeeklySeries(
        transaction,
        repository,
        ids,
        Clock.systemUTC(),
        AutoConfirmationMaterializationPort { occurrences -> autoConfirm.applyMaterialized(occurrences) },
    )
    @Bean fun weeklySeriesRepository(dataSource: DataSource) = JdbcWeeklySeriesRepository(dataSource)
    @Bean fun weeklySeriesService(
        repository: JdbcWeeklySeriesRepository,
        ids: GameIdFactory,
        autoConfirm: AutoConfirmAttendance,
    ) = WeeklySeriesService(
        repository,
        ids,
        Clock.systemUTC(),
        AutoConfirmationMaterializationPort { occurrences -> autoConfirm.applyMaterialized(occurrences) },
    )
    @Bean fun seriesBoundaryRepository(dataSource: DataSource) = JdbcSeriesBoundaryRepository(dataSource)
    @Bean fun applySeriesBoundary(
        repository: JdbcSeriesBoundaryRepository,
        ids: GameIdFactory,
        autoConfirm: AutoConfirmAttendance,
    ) = ApplySeriesBoundary(
        repository,
        ids::create,
        Clock.systemUTC(),
        AutoConfirmationMaterializationPort { occurrences -> autoConfirm.applyMaterialized(occurrences) },
    )
    @Bean fun weeklySeriesController(actor: VerifiedGroupActorResolver, series: WeeklySeriesService, boundaries: ApplySeriesBoundary) = WeeklySeriesController(actor, series, boundaries)
    @Bean fun chargeTransactionRepository(dataSource: DataSource) = JdbcChargeTransactionRepository(dataSource)
    @Bean fun chargeTransactions(transaction: JdbcTransactionRunner, repository: JdbcChargeTransactionRepository) = ChargeTransactions(transaction, repository, Instant::now)
    @Bean fun autoConfirmationRepository(dataSource: DataSource) = JdbcAutoConfirmationRepository(dataSource)
    @Bean fun autoConfirmAttendance(
        transaction: JdbcTransactionRunner,
        repository: JdbcAutoConfirmationRepository,
    ) = AutoConfirmAttendance(transaction, repository, Instant::now)
    /**
     * AutoConfirmAttendance também é GameSideEffectPort; sem @Primary o autowire por tipo
     * de editGame/changeGameLifecycle fica ambíguo e o boot com datasource falha.
     */
    @Bean
    @Primary
    fun gameSideEffects(charges: ChargeTransactions, autoConfirm: AutoConfirmAttendance): GameSideEffectPort =
        GameSideEffects(listOf(GameFinanceSideEffects(charges), autoConfirm))
    @Bean fun attendanceCharges(charges: ChargeTransactions) = AttendanceChargeAdapter(charges)
    @Bean fun respondAttendance(transaction: JdbcTransactionRunner, repository: JdbcAttendanceCommandRepository, charges: AttendanceChargeAdapter) = RespondAttendance(transaction, repository, charges, Instant::now)
    @Bean fun adjustGameCapacity(transaction: JdbcTransactionRunner, repository: JdbcAttendanceCommandRepository, charges: AttendanceChargeAdapter) = AdjustGameCapacity(transaction, repository, charges, Instant::now)
    @Bean fun attendanceController(actor: VerifiedGroupActorResolver, responses: RespondAttendance, capacities: AdjustGameCapacity, details: AttendanceDetailQuery, rosters: AttendanceRosterQuery) = AttendanceController(actor, responses, capacities, details, rosters)
    @Bean fun autoConfirmationController(actor: VerifiedGroupActorResolver, autoConfirm: AutoConfirmAttendance) = AutoConfirmationController(actor, autoConfirm)
    @Bean fun chargeManagementRepository(dataSource: DataSource) = JdbcChargeManagementRepository(dataSource)
    @Bean fun chargeManagement(transaction: JdbcTransactionRunner, repository: JdbcChargeManagementRepository) = ChargeManagement(transaction, repository, Instant::now, java.util.UUID::randomUUID)
    @Bean fun chargeController(actor: VerifiedGroupActorResolver, management: ChargeManagement, generation: ChargeTransactions) = ChargeController(actor, management, generation)
    @Bean fun expenseRepository(dataSource: DataSource) = JdbcExpenseRepository(dataSource)
    @Bean fun expenseService(transaction: JdbcTransactionRunner, repository: JdbcExpenseRepository) = ExpenseService(transaction, repository, java.util.UUID::randomUUID, Instant::now)
    @Bean fun expenseController(actor: VerifiedGroupActorResolver, expenses: ExpenseService, charges: ChargeManagement) = ExpenseController(actor, expenses, charges)
    @Bean fun monthlyChargeSchedule(
        repository: JdbcChargeTransactionRepository,
        charges: ChargeTransactions,
        @Value("\${saqz.finance.monthly-charges.zone}") zone: String,
    ) = MonthlyChargeSchedule(
        repository,
        charges,
        { LocalDate.now(ZoneId.of(zone)) },
        { membership, reason ->
            LoggerFactory.getLogger(MonthlyChargeSchedule::class.java)
                .warn("mensalidade nao gerada para o membro {} do grupo {}: {}", membership.memberId, membership.groupId, reason)
        },
    )
    @Bean fun monthlyChargeJob(schedule: MonthlyChargeSchedule) = MonthlyChargeJob(schedule)

    @Bean fun athleteRepository(dataSource: DataSource) = JdbcAthleteRepository(dataSource)
    @Bean fun athleteRosterRepository(dataSource: DataSource) = JdbcAthleteRosterRepository(dataSource)
    @Bean fun athleteStatsRepository(dataSource: DataSource) = JdbcAthleteStatsRepository(dataSource)
    @Bean fun updateOwnAthleteProfile(transaction: JdbcTransactionRunner, readRepository: JdbcGroupReadRepository, athletes: JdbcAthleteRepository) = UpdateOwnAthleteProfile(transaction, readRepository, athletes)
    @Bean fun updateAthlete(transaction: JdbcTransactionRunner, readRepository: JdbcGroupReadRepository, athletes: JdbcAthleteRepository) = UpdateAthlete(transaction, readRepository, athletes, GroupAccessPolicy())
    @Bean fun removeAthlete(transaction: JdbcTransactionRunner, readRepository: JdbcGroupReadRepository, athletes: JdbcAthleteRepository) = RemoveAthlete(transaction, readRepository, athletes, GroupAccessPolicy())
    @Bean fun listAthletes(readRepository: JdbcGroupReadRepository, roster: JdbcAthleteRosterRepository) = ListAthletes(readRepository, roster, GroupAccessPolicy())
    @Bean fun getOwnAthleteProfile(roster: JdbcAthleteRosterRepository) = GetOwnAthleteProfile(roster)
    @Bean fun getAthleteStats(readRepository: JdbcGroupReadRepository, athletes: JdbcAthleteRepository, stats: JdbcAthleteStatsRepository) = GetAthleteStats(readRepository, athletes, stats)
    @Bean fun athleteController(actor: VerifiedGroupActorResolver, list: ListAthletes, updateOwn: UpdateOwnAthleteProfile, update: UpdateAthlete, remove: RemoveAthlete, ownProfile: GetOwnAthleteProfile, stats: GetAthleteStats) = AthleteController(actor, list, updateOwn, update, remove, ownProfile, stats)
}
