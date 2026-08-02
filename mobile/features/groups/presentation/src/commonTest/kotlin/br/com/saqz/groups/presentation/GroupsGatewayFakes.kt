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
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import br.com.saqz.groups.model.GroupTimeZone as ModelGroupTimeZone

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

    override suspend fun ownProfile(): SaqzResult<OwnAthleteProfile, AthleteError> = ownProfileResult
}

class FakeGameGateway(
    var listResult: SaqzResult<List<Game>, GameError> = SaqzResult.Success(emptyList()),
) : GameGateway {
    override suspend fun list(groupId: GroupId): SaqzResult<List<Game>, GameError> = listResult

    override suspend fun read(groupId: GroupId, gameId: String): SaqzResult<VersionedGame, GameError> =
        error("not used in this screen")

    override suspend fun create(groupId: GroupId, command: GameWriteCommand): SaqzResult<VersionedGame, GameError> =
        error("not used in this screen")

    override suspend fun edit(
        groupId: GroupId,
        gameId: String,
        version: GameVersionToken,
        command: GameWriteCommand,
    ): SaqzResult<VersionedGame, GameError> = error("not used in this screen")

    override suspend fun lifecycle(
        groupId: GroupId,
        gameId: String,
        version: GameVersionToken,
        action: GameLifecycleAction,
    ): SaqzResult<VersionedGame, GameError> = error("not used in this screen")

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
