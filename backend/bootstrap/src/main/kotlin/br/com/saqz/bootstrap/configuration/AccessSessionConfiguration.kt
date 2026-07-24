package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.input.http.AccessGroupController
import br.com.saqz.groups.adapter.input.http.AthleteController
import br.com.saqz.groups.adapter.input.http.AccessGroupReadController
import br.com.saqz.groups.adapter.input.http.AccessGroupSettingsController
import br.com.saqz.groups.adapter.input.http.AccessInviteManagementController
import br.com.saqz.groups.adapter.input.http.AccessInviteRedemptionController
import br.com.saqz.groups.adapter.input.http.AccessMembershipController
import br.com.saqz.groups.adapter.input.http.AttendanceShareController
import br.com.saqz.access.adapter.input.http.AccessSessionController
import br.com.saqz.groups.adapter.output.crypto.JcaAttendanceLinkTokenGenerator
import br.com.saqz.groups.adapter.output.crypto.JcaSecureTokenGenerator
import br.com.saqz.groups.adapter.output.jdbc.attendance.share.JdbcAttendanceLinkRepository
import br.com.saqz.groups.adapter.output.jdbc.group.create.JdbcGroupCreationRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.group.settings.JdbcGroupSettingsRepository
import br.com.saqz.groups.adapter.output.jdbc.photo.JdbcGroupPhotoRepository
import br.com.saqz.groups.adapter.output.media.GroupPhotoValidator
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteManagementRepository
import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteRedemptionRepository
import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcMembershipRepository
import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.link.BranchAttendanceLinkFactory
import br.com.saqz.groups.adapter.output.link.BranchInviteLinkFactory
import br.com.saqz.groups.application.attendance.share.ReadAttendanceShareSnapshot
import br.com.saqz.groups.application.attendance.share.ResolveAttendanceLink
import br.com.saqz.groups.application.attendance.share.RotateAttendanceLink
import br.com.saqz.groups.application.create.CreateGroup
import br.com.saqz.groups.application.read.GetGroup
import br.com.saqz.groups.application.settings.UpdateGroupSettings
import br.com.saqz.groups.application.invite.manage.ExpireInvite
import br.com.saqz.groups.application.invite.manage.RotateInvite
import br.com.saqz.groups.application.invite.redeem.RedeemInvite
import br.com.saqz.groups.application.membership.ChangeMemberRole
import br.com.saqz.groups.application.membership.ListAccessMemberships
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRosterRepository
import br.com.saqz.groups.application.athlete.GetOwnAthleteProfile
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
import br.com.saqz.groups.adapter.input.http.WeeklySeriesController
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcGameOccurrenceRepository
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcSeriesBoundaryRepository
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcWeeklySeriesRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeManagementRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcExpenseRepository
import br.com.saqz.groups.adapter.output.jdbc.attendance.AttendanceChargeAdapter
import br.com.saqz.groups.adapter.output.jdbc.attendance.JdbcAttendanceCommandRepository
import br.com.saqz.groups.application.attendance.AdjustGameCapacity
import br.com.saqz.groups.application.attendance.AttendanceDetailQuery
import br.com.saqz.groups.application.attendance.AttendanceRosterQuery
import br.com.saqz.groups.application.attendance.RespondAttendance
import br.com.saqz.groups.application.game.ChangeGameLifecycle
import br.com.saqz.groups.application.game.CreateGame
import br.com.saqz.groups.application.game.EditGame
import br.com.saqz.groups.application.game.GameAttendanceCountSource
import br.com.saqz.groups.application.game.GameSideEffectPort
import br.com.saqz.groups.application.game.GetGame
import br.com.saqz.groups.application.game.ListGames
import br.com.saqz.groups.application.game.recurrence.GameIdFactory
import br.com.saqz.groups.application.game.series.ApplySeriesBoundary
import br.com.saqz.groups.application.game.series.WeeklySeriesService
import br.com.saqz.groups.application.finance.charge.ChargeManagement
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.application.finance.charge.GameFinanceSideEffects
import br.com.saqz.groups.application.finance.expense.ExpenseService
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.access.application.session.CompleteSessionProfile
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
import java.time.Instant
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
    fun completeSessionProfile(repository: JdbcSessionRepository) = CompleteSessionProfile(repository)

    @Bean
    fun accessSessionController(useCase: BootstrapSession, profile: CompleteSessionProfile) =
        AccessSessionController(useCase, profile)

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
        getGroup: GetGroup,
    ) = AccessGroupController(verifiedGroupActorResolver, createGroup, getGroup)

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
    @Bean fun weeklySeriesRepository(dataSource: DataSource) = JdbcWeeklySeriesRepository(dataSource)
    @Bean fun weeklySeriesService(repository: JdbcWeeklySeriesRepository, ids: GameIdFactory) = WeeklySeriesService(repository, ids, Clock.systemUTC())
    @Bean fun seriesBoundaryRepository(dataSource: DataSource) = JdbcSeriesBoundaryRepository(dataSource)
    @Bean fun applySeriesBoundary(repository: JdbcSeriesBoundaryRepository, ids: GameIdFactory) = ApplySeriesBoundary(repository, ids::create, Clock.systemUTC())
    @Bean fun weeklySeriesController(actor: VerifiedGroupActorResolver, series: WeeklySeriesService, boundaries: ApplySeriesBoundary) = WeeklySeriesController(actor, series, boundaries)
    @Bean fun chargeTransactionRepository(dataSource: DataSource) = JdbcChargeTransactionRepository(dataSource)
    @Bean fun chargeTransactions(transaction: JdbcTransactionRunner, repository: JdbcChargeTransactionRepository) = ChargeTransactions(transaction, repository, Instant::now)
    @Bean fun gameSideEffects(charges: ChargeTransactions): GameSideEffectPort = GameFinanceSideEffects(charges)
    @Bean fun attendanceCharges(charges: ChargeTransactions) = AttendanceChargeAdapter(charges)
    @Bean fun respondAttendance(transaction: JdbcTransactionRunner, repository: JdbcAttendanceCommandRepository, charges: AttendanceChargeAdapter) = RespondAttendance(transaction, repository, charges, Instant::now)
    @Bean fun adjustGameCapacity(transaction: JdbcTransactionRunner, repository: JdbcAttendanceCommandRepository, charges: AttendanceChargeAdapter) = AdjustGameCapacity(transaction, repository, charges, Instant::now)
    @Bean fun attendanceController(actor: VerifiedGroupActorResolver, responses: RespondAttendance, capacities: AdjustGameCapacity, details: AttendanceDetailQuery, rosters: AttendanceRosterQuery) = AttendanceController(actor, responses, capacities, details, rosters)
    @Bean fun chargeManagementRepository(dataSource: DataSource) = JdbcChargeManagementRepository(dataSource)
    @Bean fun chargeManagement(transaction: JdbcTransactionRunner, repository: JdbcChargeManagementRepository) = ChargeManagement(transaction, repository, Instant::now, java.util.UUID::randomUUID)
    @Bean fun chargeController(actor: VerifiedGroupActorResolver, management: ChargeManagement, generation: ChargeTransactions) = ChargeController(actor, management, generation)
    @Bean fun expenseRepository(dataSource: DataSource) = JdbcExpenseRepository(dataSource)
    @Bean fun expenseService(transaction: JdbcTransactionRunner, repository: JdbcExpenseRepository) = ExpenseService(transaction, repository, java.util.UUID::randomUUID, Instant::now)
    @Bean fun expenseController(actor: VerifiedGroupActorResolver, expenses: ExpenseService, charges: ChargeManagement) = ExpenseController(actor, expenses, charges)

    @Bean fun athleteRepository(dataSource: DataSource) = JdbcAthleteRepository(dataSource)
    @Bean fun athleteRosterRepository(dataSource: DataSource) = JdbcAthleteRosterRepository(dataSource)
    @Bean fun updateOwnAthleteProfile(transaction: JdbcTransactionRunner, readRepository: JdbcGroupReadRepository, athletes: JdbcAthleteRepository) = UpdateOwnAthleteProfile(transaction, readRepository, athletes)
    @Bean fun updateAthlete(transaction: JdbcTransactionRunner, readRepository: JdbcGroupReadRepository, athletes: JdbcAthleteRepository) = UpdateAthlete(transaction, readRepository, athletes, GroupAccessPolicy())
    @Bean fun removeAthlete(transaction: JdbcTransactionRunner, readRepository: JdbcGroupReadRepository, athletes: JdbcAthleteRepository) = RemoveAthlete(transaction, readRepository, athletes, GroupAccessPolicy())
    @Bean fun listAthletes(readRepository: JdbcGroupReadRepository, roster: JdbcAthleteRosterRepository) = ListAthletes(readRepository, roster, GroupAccessPolicy())
    @Bean fun getOwnAthleteProfile(roster: JdbcAthleteRosterRepository) = GetOwnAthleteProfile(roster)
    @Bean fun athleteController(actor: VerifiedGroupActorResolver, list: ListAthletes, updateOwn: UpdateOwnAthleteProfile, update: UpdateAthlete, remove: RemoveAthlete, ownProfile: GetOwnAthleteProfile) = AthleteController(actor, list, updateOwn, update, remove, ownProfile)
}
