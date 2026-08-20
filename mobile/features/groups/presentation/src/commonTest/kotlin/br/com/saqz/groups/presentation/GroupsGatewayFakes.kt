package br.com.saqz.groups.presentation

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.Athlete
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.athlete.AthleteStats
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.athlete.UpdateOwnAthleteProfileCommand
import br.com.saqz.groups.domain.attendance.AttendanceCapacity
import br.com.saqz.groups.domain.attendance.AttendanceCapacityCommand
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendancePromotionCommand
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken
import br.com.saqz.groups.domain.attendance.AutoConfirmationCommand
import br.com.saqz.groups.domain.attendance.AutoConfirmationUpdate
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.attendance.VersionedAttendanceCapacity
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.GameWriteCommand
import br.com.saqz.groups.domain.game.SeriesBoundaryCommand
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.domain.game.VersionedSeries
import br.com.saqz.groups.domain.game.WeeklySeriesWriteCommand
import br.com.saqz.groups.domain.finance.AthleteFinanceGateway
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.ChargeStatusCommand
import br.com.saqz.groups.domain.finance.ExpenseList
import br.com.saqz.groups.domain.finance.ExpenseWriteCommand
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceStatementGateway
import br.com.saqz.groups.domain.finance.FinanceStatementPage
import br.com.saqz.groups.domain.finance.FinanceStatementQuery
import br.com.saqz.groups.domain.finance.FinanceStatementSummary
import br.com.saqz.groups.domain.finance.FinanceTotals
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.group.CreateGroupProfileCommand
import br.com.saqz.groups.domain.group.CreateGroupCommand
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.GroupProfile
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.domain.group.UpdateGroupProfileCommand
import br.com.saqz.groups.domain.group.UpdateGroupSettingsCommand
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.GroupInviteUrl
import br.com.saqz.groups.domain.membership.GroupInviteMetadata
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.finance.MonthlyChargeCommand
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.finance.VersionedCharge
import br.com.saqz.groups.domain.finance.VersionedExpense
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import br.com.saqz.groups.model.GroupTimeZone as ModelGroupTimeZone
import kotlinx.coroutines.CompletableDeferred

class FakeGroupGateway(
    var readResult: SaqzResult<VersionedGroup, GroupProfileError> = SaqzResult.Success(sampleVersionedGroup()),
    var deleteResult: SaqzResult<Unit, GroupProfileError> = SaqzResult.Success(Unit),
) : GroupGateway {
    var readCalls = 0

    override suspend fun create(command: CreateGroupCommand): SaqzResult<Group, GroupProfileError> =
        SaqzResult.Success(sampleGroup(command.name, command.timeZone))

    override suspend fun read(groupId: GroupId): SaqzResult<VersionedGroup, GroupProfileError> {
        readCalls += 1
        return readResult
    }

    override suspend fun update(command: UpdateGroupSettingsCommand): SaqzResult<VersionedGroup, GroupProfileError> =
        readResult

    override suspend fun delete(groupId: GroupId): SaqzResult<Unit, GroupProfileError> = deleteResult
}

class FakeGroupProfileGateway(
    var readResult: SaqzResult<VersionedGroup, GroupProfileError> = SaqzResult.Success(sampleVersionedGroup()),
    var createResult: SaqzResult<Group, GroupProfileError> = SaqzResult.Success(sampleGroup()),
    var updateResult: SaqzResult<VersionedGroup, GroupProfileError> = SaqzResult.Success(sampleVersionedGroup()),
) : GroupProfileGateway {
    var lastCreateCommand: CreateGroupProfileCommand? = null
    var lastUpdateCommand: UpdateGroupProfileCommand? = null
    val createCommands = mutableListOf<CreateGroupProfileCommand>()
    var createHandler: suspend (CreateGroupProfileCommand) -> SaqzResult<Group, GroupProfileError> = { createResult }

    override suspend fun createProfile(command: CreateGroupProfileCommand): SaqzResult<Group, GroupProfileError> {
        lastCreateCommand = command
        createCommands += command
        return createHandler(command)
    }

    override suspend fun readProfile(groupId: GroupId): SaqzResult<VersionedGroup, GroupProfileError> = readResult

    override suspend fun updateProfile(command: UpdateGroupProfileCommand): SaqzResult<VersionedGroup, GroupProfileError> {
        lastUpdateCommand = command
        return updateResult
    }
}

class FakeAthleteGateway(
    var ownProfileResult: SaqzResult<OwnAthleteProfile, AthleteError> = SaqzResult.Success(
        OwnAthleteProfile("me", "Bruno", null, emptyList()),
    ),
    var rosterResult: SaqzResult<List<AthleteRosterEntry>, AthleteError> = SaqzResult.Success(emptyList()),
    var updateResult: SaqzResult<Athlete, AthleteError>? = null,
    var updateOwnProfileResult: SaqzResult<Athlete, AthleteError>? = null,
    var removeResult: SaqzResult<Unit, AthleteError> = SaqzResult.Success(Unit),
    var statsResult: SaqzResult<AthleteStats, AthleteError> = SaqzResult.Success(AthleteStats(0, null, 0)),
) : AthleteGateway {
    var lastRosterFilter: AthleteRosterFilter? = null
    var lastUpdateOwnProfileCommand: UpdateOwnAthleteProfileCommand? = null
    var lastRemovedUserId: String? = null
    var lastUpdateCommand: br.com.saqz.groups.domain.athlete.UpdateAthleteCommand? = null
    var lastStatsUserId: String? = null
    var ownProfileCalls = 0

    override suspend fun roster(
        groupId: GroupId,
        filter: AthleteRosterFilter,
    ): SaqzResult<List<AthleteRosterEntry>, AthleteError> {
        lastRosterFilter = filter
        return rosterResult
    }

    override suspend fun updateOwnPosition(
        groupId: GroupId,
        position: AthletePosition?,
    ): SaqzResult<Athlete, AthleteError> = error("not used in this screen")

    override suspend fun updateOwnProfile(
        command: UpdateOwnAthleteProfileCommand,
    ): SaqzResult<Athlete, AthleteError> {
        lastUpdateOwnProfileCommand = command
        return updateOwnProfileResult ?: SaqzResult.Success(
            Athlete(
                userId = "me",
                displayName = "Bruno",
                role = GroupRole.ATHLETE,
                position = command.position,
                membershipType = AthleteMembershipType.AVULSO,
                active = true,
                nickname = command.nickname,
                secondaryPosition = command.secondaryPosition,
                level = command.level,
                preferredSide = command.preferredSide,
                heightCm = command.heightCm,
            ),
        )
    }

    override suspend fun updateAthlete(command: br.com.saqz.groups.domain.athlete.UpdateAthleteCommand): SaqzResult<Athlete, AthleteError> {
        lastUpdateCommand = command
        return updateResult ?: SaqzResult.Success(
            Athlete(
                userId = command.userId,
                displayName = "Member",
                role = GroupRole.ATHLETE,
                position = command.position,
                membershipType = command.membershipType,
                active = command.active,
                nickname = command.nickname,
                secondaryPosition = command.secondaryPosition,
                level = command.level,
                preferredSide = command.preferredSide,
                heightCm = command.heightCm,
                monthlyFeeCents = command.monthlyFeeCents,
                monthlyDueDay = command.monthlyDueDay,
            ),
        )
    }

    override suspend fun stats(groupId: GroupId, userId: String): SaqzResult<AthleteStats, AthleteError> {
        lastStatsUserId = userId
        return statsResult
    }

    override suspend fun removeAthlete(groupId: GroupId, userId: String): SaqzResult<Unit, AthleteError> {
        lastRemovedUserId = userId
        return removeResult
    }

    override suspend fun ownProfile(): SaqzResult<OwnAthleteProfile, AthleteError> {
        ownProfileCalls += 1
        return ownProfileResult
    }
}

class FakeGameGateway(
    var listResult: SaqzResult<List<Game>, GameError> = SaqzResult.Success(emptyList()),
    var readResult: SaqzResult<VersionedGame, GameError> = SaqzResult.Success(sampleVersionedGame()),
    var createResult: SaqzResult<VersionedGame, GameError> = SaqzResult.Success(sampleVersionedGame()),
    var editResult: SaqzResult<VersionedGame, GameError> = SaqzResult.Success(sampleVersionedGame()),
    var lifecycleResult: SaqzResult<VersionedGame, GameError> = SaqzResult.Success(sampleVersionedGame()),
    private val reads: ArrayDeque<CompletableDeferred<SaqzResult<VersionedGame, GameError>>>? = null,
    private val readResults: ArrayDeque<SaqzResult<VersionedGame, GameError>>? = null,
    private val lifecycleResults: ArrayDeque<SaqzResult<VersionedGame, GameError>>? = null,
    private val lifecycleDeferreds: ArrayDeque<CompletableDeferred<SaqzResult<VersionedGame, GameError>>>? = null,
) : GameGateway {
    var readCalls = 0
    var createCalls = 0
    var editCalls = 0
    var lastCreateCommand: GameWriteCommand? = null
    var lastEditGameId: String? = null
    var lastEditCommand: GameWriteCommand? = null
    var lastLifecycleAction: GameLifecycleAction? = null
    val editVersions = mutableListOf<GameVersionToken>()
    val lifecycleGameIds = mutableListOf<String>()
    val lifecycleVersions = mutableListOf<GameVersionToken>()

    override suspend fun list(groupId: GroupId): SaqzResult<List<Game>, GameError> = listResult

    override suspend fun read(groupId: GroupId, gameId: String): SaqzResult<VersionedGame, GameError> {
        readCalls += 1
        return reads?.getOrNull(readCalls - 1)?.await() ?: readResults?.removeFirstOrNull() ?: readResult
    }

    fun completeRead(index: Int, value: SaqzResult<VersionedGame, GameError>) {
        reads?.getOrNull(index)?.complete(value)
    }

    override suspend fun create(groupId: GroupId, command: GameWriteCommand): SaqzResult<VersionedGame, GameError> {
        createCalls += 1
        lastCreateCommand = command
        return createResult
    }

    override suspend fun edit(
        groupId: GroupId,
        gameId: String,
        version: GameVersionToken,
        command: GameWriteCommand,
    ): SaqzResult<VersionedGame, GameError> {
        editCalls += 1
        lastEditGameId = gameId
        lastEditCommand = command
        editVersions += version
        return editResult
    }

    override suspend fun lifecycle(
        groupId: GroupId,
        gameId: String,
        version: GameVersionToken,
        action: GameLifecycleAction,
    ): SaqzResult<VersionedGame, GameError> {
        lifecycleGameIds += gameId
        lastLifecycleAction = action
        lifecycleVersions += version
        return lifecycleDeferreds?.removeFirstOrNull()?.await()
            ?: lifecycleResults?.removeFirstOrNull()
            ?: lifecycleResult
    }

    override suspend fun createSeries(
        groupId: GroupId,
        command: WeeklySeriesWriteCommand,
    ): SaqzResult<VersionedSeries, GameError> = error("not used in this screen")

    override suspend fun readSeries(groupId: GroupId, seriesId: String): SaqzResult<VersionedSeries, GameError> =
        error("not used in this screen")

    override suspend fun boundary(
        groupId: GroupId,
        seriesId: String,
        version: GameVersionToken,
        command: SeriesBoundaryCommand,
    ): SaqzResult<VersionedSeries, GameError> = error("not used in this screen")
}

class FakeAttendanceGateway(
    var detailResult: SaqzResult<AttendanceDetail, AttendanceError> = SaqzResult.Success(sampleAttendanceDetail()),
    var rosterResult: SaqzResult<AttendanceRoster, AttendanceError> = SaqzResult.Success(sampleAttendanceRoster()),
    var respondResult: SaqzResult<VersionedAttendanceMutation, AttendanceError> =
        SaqzResult.Success(sampleVersionedAttendanceMutation()),
    var promoteResult: SaqzResult<VersionedAttendanceMutation, AttendanceError> =
        SaqzResult.Success(sampleVersionedAttendanceMutation()),
    var capacityResult: SaqzResult<VersionedAttendanceCapacity, AttendanceError> =
        SaqzResult.Success(sampleVersionedAttendanceCapacity()),
    var autoConfirmationResult: SaqzResult<AutoConfirmationUpdate, AttendanceError> =
        SaqzResult.Success(AutoConfirmationUpdate(false)),
) : AttendanceGateway {
    var readCalls = 0
    var rosterCalls = 0
    var respondCalls = 0
    var promoteCalls = 0
    var capacityCalls = 0
    var lastPromotionCommand: AttendancePromotionCommand? = null
    var lastAttendanceCommand: SelfAttendanceCommand? = null
    var rosterResults: MutableList<SaqzResult<AttendanceRoster, AttendanceError>> = mutableListOf()
    var respondDeferred: CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>? = null
    var lastCapacityCommand: AttendanceCapacityCommand? = null
    var lastCapacityVersion: AttendanceVersionToken? = null
    var promoteDeferred: CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>? = null
    var capacityDeferred: CompletableDeferred<SaqzResult<VersionedAttendanceCapacity, AttendanceError>>? = null

    override suspend fun read(groupId: GroupId, gameId: String): SaqzResult<AttendanceDetail, AttendanceError> {
        readCalls++
        return detailResult
    }

    override suspend fun roster(groupId: GroupId, gameId: String): SaqzResult<AttendanceRoster, AttendanceError> {
        rosterCalls++
        return if (rosterResults.isNotEmpty()) rosterResults.removeAt(0) else rosterResult
    }

    override suspend fun respond(
        groupId: GroupId,
        gameId: String,
        command: SelfAttendanceCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError> {
        respondCalls++
        lastAttendanceCommand = command
        return respondDeferred?.await() ?: respondResult
    }

    override suspend fun promote(
        groupId: GroupId,
        gameId: String,
        command: AttendancePromotionCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError> {
        promoteCalls++
        lastPromotionCommand = command
        return promoteDeferred?.await() ?: promoteResult
    }

    override suspend fun override(
        groupId: GroupId,
        gameId: String,
        command: br.com.saqz.groups.domain.attendance.OverrideAttendanceCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError> =
        error("not used in this screen")

    override suspend fun capacity(
        groupId: GroupId,
        gameId: String,
        version: AttendanceVersionToken,
        command: AttendanceCapacityCommand,
    ): SaqzResult<VersionedAttendanceCapacity, AttendanceError> {
        capacityCalls++
        lastCapacityVersion = version
        lastCapacityCommand = command
        return capacityDeferred?.await() ?: capacityResult
    }

    override suspend fun updateAutoConfirmation(
        groupId: GroupId,
        command: AutoConfirmationCommand,
    ): SaqzResult<AutoConfirmationUpdate, AttendanceError> = autoConfirmationResult
}

class FakeGroupMembershipGateway(
    var listResult: SaqzResult<List<GroupMembership>, GroupMembershipError> = SaqzResult.Success(emptyList()),
    var changeRoleResult: SaqzResult<GroupMembership, GroupMembershipError> =
        SaqzResult.Success(GroupMembership("member", "Member", GroupRole.ADMIN)),
) : GroupMembershipGateway {
    var lastRoleCommand: ChangeMembershipRoleCommand? = null

    override suspend fun listMemberships(groupId: GroupId): SaqzResult<List<GroupMembership>, GroupMembershipError> =
        listResult

    override suspend fun changeRole(command: ChangeMembershipRoleCommand): SaqzResult<GroupMembership, GroupMembershipError> {
        lastRoleCommand = command
        return changeRoleResult
    }

    override suspend fun rotateInvite(groupId: GroupId): SaqzResult<GroupInviteUrl, GroupMembershipError> =
        error("not used in this screen")

    override suspend fun readInviteMetadata(groupId: GroupId): SaqzResult<GroupInviteMetadata, GroupMembershipError> =
        error("not used in this screen")

    override suspend fun expireInvite(groupId: GroupId): SaqzResult<Unit, GroupMembershipError> =
        error("not used in this screen")

}

class FakeGroupSystemTimeZonePort : GroupSystemTimeZonePort {
    override fun detect(done: (GroupSystemTimeZoneResult) -> Unit) {
        done(GroupSystemTimeZoneResult.Available(ModelGroupTimeZone.parse("America/Sao_Paulo").let {
            (it as ModelGroupTimeZone.ParseResult.Valid).value
        }))
    }
}

class FakeFinanceStatementGateway(
    var result: SaqzResult<FinanceStatementPage, FinanceError> = SaqzResult.Success(
        FinanceStatementPage(
            month = "2026-08",
            items = emptyList(),
            summary = FinanceStatementSummary(0L, 0L, 0L, 0L),
            limit = 20,
            offset = 0,
            hasMore = false,
        ),
    ),
    var statementDeferred: CompletableDeferred<SaqzResult<FinanceStatementPage, FinanceError>>? = null,
) : FinanceStatementGateway {
    override suspend fun statement(groupId: GroupId, query: FinanceStatementQuery) = statementDeferred?.await() ?: result
}

class FakeAthleteFinanceGateway(
    var ownChargesResult: SaqzResult<ChargeList, FinanceError> = SaqzResult.Success(ChargeList(emptyList())),
    var ownChargesDeferred: CompletableDeferred<SaqzResult<ChargeList, FinanceError>>? = null,
) : AthleteFinanceGateway {
    var ownChargesCalls = 0
        private set

    override suspend fun ownCharges(groupId: GroupId): SaqzResult<ChargeList, FinanceError> {
        ownChargesCalls++
        return ownChargesDeferred?.await() ?: ownChargesResult
    }
}

class FakeOrganizerFinanceGateway(
    var chargesResult: SaqzResult<ChargeList, FinanceError> = SaqzResult.Success(ChargeList(emptyList())),
) : OrganizerFinanceGateway {
    override suspend fun charges(groupId: GroupId) = chargesResult

    override suspend fun generateMonthly(groupId: GroupId, command: MonthlyChargeCommand) =
        error("not used in this screen")

    override suspend fun updateChargeStatus(
        groupId: GroupId,
        chargeId: String,
        version: FinanceVersionToken,
        command: ChargeStatusCommand,
    ) = error("not used in this screen") as SaqzResult<VersionedCharge, FinanceError>

    override suspend fun expenses(groupId: GroupId) =
        error("not used in this screen") as SaqzResult<ExpenseList, FinanceError>

    override suspend fun createExpense(groupId: GroupId, command: ExpenseWriteCommand) =
        error("not used in this screen") as SaqzResult<VersionedExpense, FinanceError>

    override suspend fun editExpense(
        groupId: GroupId,
        expenseId: String,
        version: FinanceVersionToken,
        command: ExpenseWriteCommand,
    ) = error("not used in this screen") as SaqzResult<VersionedExpense, FinanceError>

    override suspend fun voidExpense(groupId: GroupId, expenseId: String, version: FinanceVersionToken) =
        error("not used in this screen") as SaqzResult<VersionedExpense, FinanceError>

    override suspend fun totals(groupId: GroupId) =
        error("not used in this screen") as SaqzResult<FinanceTotals, FinanceError>
}

fun sampleGroup(
    name: String = "Vôlei do CERET",
    timeZone: GroupTimeZone = GroupTimeZone("America/Sao_Paulo"),
    role: GroupRole = GroupRole.ADMIN,
    profile: GroupProfile? = sampleProfile(),
) = Group(
    id = GroupId("group-1"),
    name = name,
    timeZone = timeZone,
    version = 2,
    role = role,
    profile = profile,
)

fun sampleVersionedGroup(
    group: Group = sampleGroup(),
) = VersionedGroup(group, GroupVersionToken("etag-2"))

private fun sampleProfile() = GroupProfile(
    modality = GroupModality.COURT_VOLLEYBALL,
    composition = br.com.saqz.groups.domain.group.GroupComposition.MIXED,
    description = null,
    city = "São Paulo",
    level = br.com.saqz.groups.domain.group.GroupLevel.INTERMEDIATE,
    customLevel = null,
    playStyle = br.com.saqz.groups.domain.group.GroupPlayStyle.SIX_ZERO,
    customPlayStyle = null,
    defaultVenue = br.com.saqz.groups.domain.group.GroupVenue(name = "CERET", address = "Rua Canuto Abreu"),
    regularSlots = listOf(
        br.com.saqz.groups.domain.group.GroupRegularSlot(
            weekday = br.com.saqz.groups.domain.group.GroupWeekday.TUESDAY,
            startTime = "19:30",
            durationMinutes = 120,
        ),
    ),
    defaultCapacity = 12,
    defaultConfirmationLeadMinutes = 360,
)

fun sampleRosterEntry(
    userId: String = "member-1",
    financialStatus: br.com.saqz.groups.domain.athlete.AthleteFinancialStatus =
        br.com.saqz.groups.domain.athlete.AthleteFinancialStatus.DESCONHECIDO,
) = AthleteRosterEntry(
    userId = userId,
    displayName = "Member",
    phone = null,
    position = AthletePosition.CENTRAL,
    membershipType = AthleteMembershipType.MENSALISTA,
    active = true,
    financialStatus = financialStatus,
)

fun sampleGame() = Game(
    id = "game-1",
    groupId = GroupId("group-1"),
    title = "Jogo de terça",
    venue = GameVenue(name = "CERET", address = "Rua Canuto Abreu"),
    localDate = "2026-08-04",
    localTime = "19:30",
    zoneId = "America/Sao_Paulo",
    startsAt = "2026-08-04T19:30:00-03:00",
    durationMinutes = 120,
    capacity = 12,
    confirmationDeadline = "2026-08-04T12:00:00-03:00",
    status = GameStatus.Published,
    version = 1,
    confirmedCount = 8,
    availableSpots = 4,
    waitlistCount = 0,
)

fun sampleVersionedGame(game: Game = sampleGame()) = VersionedGame(game, GameVersionToken("etag-1"))

fun sampleAttendanceDetail() = AttendanceDetail(
    confirmedCount = 8,
    availableSpots = 4,
    waitlistCount = 2,
    capacity = 12,
)

fun sampleAttendanceRoster() = AttendanceRoster(
    confirmed = listOf(AttendanceRosterMember("confirmed-1", "Ana")),
    waitlisted = listOf(
        AttendanceRosterMember("wait-1", "Caio", 1),
        AttendanceRosterMember("wait-2", "Duda", 2),
    ),
)

fun sampleVersionedAttendanceMutation() = VersionedAttendanceMutation(
    value = br.com.saqz.groups.domain.attendance.AttendanceMutation(
        attendance = br.com.saqz.groups.domain.attendance.AttendanceEntry(
            memberId = "wait-1",
            status = br.com.saqz.groups.domain.attendance.AttendanceStatus.Confirmed,
            version = 2,
        ),
        promotedCount = 1,
        detail = sampleAttendanceDetail().copy(confirmedCount = 9, availableSpots = 3, waitlistCount = 1),
    ),
    version = AttendanceVersionToken("\"2\""),
)

fun sampleVersionedAttendanceCapacity() = VersionedAttendanceCapacity(
    value = AttendanceCapacity(
        capacity = 14,
        version = 2,
        promotedCount = 0,
        detail = sampleAttendanceDetail().copy(capacity = 14, availableSpots = 6),
    ),
    version = AttendanceVersionToken("\"2\""),
)

fun sampleCancelledGame() = sampleGame().copy(status = GameStatus.Cancelled, version = 2)
