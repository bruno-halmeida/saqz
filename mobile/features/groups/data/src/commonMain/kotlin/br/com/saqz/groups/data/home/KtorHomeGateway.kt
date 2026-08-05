package br.com.saqz.groups.data.home

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.home.HomeAdminGroup
import br.com.saqz.groups.domain.home.HomeAdminReadModel
import br.com.saqz.groups.domain.home.HomeError
import br.com.saqz.groups.domain.home.HomeGameToSettle
import br.com.saqz.groups.domain.home.HomeGateway
import br.com.saqz.groups.domain.home.HomeLastCompletedGame
import br.com.saqz.groups.domain.home.HomeMemberGroup
import br.com.saqz.groups.domain.home.HomeMemberReadModel
import br.com.saqz.groups.domain.home.HomeMonthlyCharges
import br.com.saqz.groups.domain.home.HomeNextGame
import br.com.saqz.groups.domain.home.HomeOwnAttendance
import br.com.saqz.groups.domain.home.HomeOwnChargeGroup
import br.com.saqz.groups.domain.home.HomeOwnChargeOldest
import br.com.saqz.groups.domain.home.HomeOwnCharges
import br.com.saqz.groups.domain.home.HomeReadModel
import br.com.saqz.groups.domain.home.HomeRosterMember
import br.com.saqz.groups.domain.home.HomeRosterPreview
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
internal data class HomeGroupTransport(
    val id: String,
    val name: String,
    val role: String,
    val memberCount: Int,
    val gamesPlayed: Int,
)

@Serializable
internal data class HomeRosterMemberTransport(
    val displayName: String,
    val waitlistPosition: Long?,
)

@Serializable
internal data class HomeRosterPreviewTransport(
    val confirmed: List<HomeRosterMemberTransport>,
    val waitlisted: List<HomeRosterMemberTransport>,
)

@Serializable
internal data class HomeOwnAttendanceTransport(
    val status: String,
    val waitlistPosition: Long?,
)

@Serializable
internal data class HomeNextGameTransport(
    val groupId: String,
    val groupName: String,
    val gameId: String,
    val local: String,
    val zoneId: String,
    val startsAt: String,
    val confirmationDeadline: String,
    val capacity: Int,
    val confirmedCount: Int,
    val declinedCount: Int,
    val pendingCount: Int,
    val waitlistCount: Int,
    val ownAttendance: HomeOwnAttendanceTransport?,
    val membershipType: String,
    val mensalistaPriority: Boolean,
    val rosterPreview: HomeRosterPreviewTransport,
)

@Serializable
internal data class HomeLastCompletedGameTransport(
    val groupId: String,
    val groupName: String,
    val gameId: String,
    val zoneId: String,
    val startsAt: String,
    val confirmedCount: Int,
    val ownPlayed: Boolean,
)

@Serializable
internal data class HomeMemberTransport(
    val nextGame: HomeNextGameTransport?,
    val lastCompletedGame: HomeLastCompletedGameTransport?,
    val groups: List<HomeGroupTransport>,
)

@Serializable
internal data class HomeMonthlyChargesTransport(
    val count: Int,
    val totalCents: Long,
    val month: String,
)

@Serializable
internal data class HomeGameToSettleTransport(
    val gameId: String,
    val startsAt: String,
    val zoneId: String,
    val pendingCount: Int,
    val totalCents: Long,
)

@Serializable
internal data class HomeAdminGroupTransport(
    val id: String,
    val name: String,
    val entryRequestCount: Int,
    val monthlyCharges: HomeMonthlyChargesTransport,
    val gameToSettle: HomeGameToSettleTransport?,
)

@Serializable
internal data class HomeAdminTransport(
    val groups: List<HomeAdminGroupTransport>,
)

@Serializable
internal data class HomeOwnChargeOldestTransport(
    val kind: String,
    val month: String? = null,
)

@Serializable
internal data class HomeOwnChargeGroupTransport(
    val groupId: String,
    val groupName: String,
    val count: Int,
    val totalCents: Long,
    val nextDueDate: String,
    val overdue: Boolean,
    val pixKey: String?,
    val pixLabel: String?,
    val oldest: HomeOwnChargeOldestTransport,
)

@Serializable
internal data class HomeOwnChargesTransport(
    val groupCount: Int,
    val totalCents: Long,
    val groups: List<HomeOwnChargeGroupTransport>,
)

@Serializable
internal data class HomeResponseTransport(
    val member: HomeMemberTransport,
    val admin: HomeAdminTransport?,
    val ownCharges: HomeOwnChargesTransport? = null,
)

class KtorHomeGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : HomeGateway {
    override suspend fun read(): SaqzResult<HomeReadModel, HomeError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "api/me/home",
                HomeResponseTransport.serializer(),
            )
        }.toDomainResult()
}

private fun NetworkResult<HomeResponseTransport>.toDomainResult(): SaqzResult<HomeReadModel, HomeError> = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomain())
    is NetworkResult.Success -> value.toDomain()
        ?.let { SaqzResult.Success(it) }
        ?: SaqzResult.Failure(HomeError.Data(DataError.InvalidResponse))
}

private fun NetworkError.toDomain(): HomeError = HomeError.Data(
    when (this) {
        is NetworkError.ApiProblemError -> problem.status.toDataError()
        is NetworkError.HttpStatus -> status.toDataError()
        NetworkError.Timeout -> DataError.Timeout
        NetworkError.Connectivity -> DataError.Connectivity
        NetworkError.InvalidResponse -> DataError.InvalidResponse
        NetworkError.PayloadTooLarge -> DataError.PayloadTooLarge
        NetworkError.Unavailable,
        NetworkError.Unknown,
        -> DataError.Unknown
    },
)

private fun Int.toDataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}

private fun HomeResponseTransport.toDomain(): HomeReadModel? {
    val memberDomain = member.toDomain() ?: return null
    val adminDomain = when {
        admin == null -> null
        else -> admin.toDomain()
    }
    return HomeReadModel(member = memberDomain, admin = adminDomain, ownCharges = ownCharges?.toDomain())
}

/**
 * Bloco `oldest` com competência que não bate com o contrato derruba **o grupo**, não a
 * Home inteira: o aviso é secundário e o resto do agregado continua útil. Grupo de fora é
 * dívida que a pessoa não vê, então o contador do topo cai junto — um "R$ 80,00 em aberto"
 * com uma linha só embaixo seria pior que a linha faltando.
 */
private fun HomeOwnChargesTransport.toDomain(): HomeOwnCharges? {
    val groupsDomain = groups.mapNotNull { it.toDomain() }
    if (groupsDomain.size != groups.size || groupsDomain.isEmpty()) return null
    return HomeOwnCharges(groupCount = groupCount, totalCents = totalCents, groups = groupsDomain)
}

private fun HomeOwnChargeGroupTransport.toDomain(): HomeOwnChargeGroup? {
    val oldestDomain = oldest.toDomain() ?: return null
    return HomeOwnChargeGroup(
        groupId = GroupId(groupId),
        groupName = groupName,
        count = count,
        totalCents = totalCents,
        nextDueDate = nextDueDate,
        overdue = overdue,
        pixKey = pixKey,
        pixLabel = pixLabel,
        oldest = oldestDomain,
    )
}

private fun HomeOwnChargeOldestTransport.toDomain(): HomeOwnChargeOldest? = when (kind) {
    "MONTHLY" -> month?.let(HomeOwnChargeOldest::Monthly)
    "GAME" -> HomeOwnChargeOldest.Game
    else -> null
}

private fun HomeMemberTransport.toDomain(): HomeMemberReadModel? {
    val groupsDomain = groups.map { it.toDomain() }
    if (groupsDomain.any { it == null }) return null
    return HomeMemberReadModel(
        nextGame = nextGame?.toDomain() ?: if (nextGame == null) null else return null,
        lastCompletedGame = lastCompletedGame?.toDomain()
            ?: if (lastCompletedGame == null) null else return null,
        groups = groupsDomain.filterNotNull(),
    )
}

private fun HomeGroupTransport.toDomain() = role.toGroupRole()?.let { roleDomain ->
    HomeMemberGroup(
        id = GroupId(id),
        name = name,
        role = roleDomain,
        memberCount = memberCount,
        gamesPlayed = gamesPlayed,
    )
}

private fun HomeNextGameTransport.toDomain(): HomeNextGame? {
    val membershipTypeDomain = membershipType.toMembershipType() ?: return null
    val rosterPreviewDomain = rosterPreview.toDomain()
    return HomeNextGame(
        groupId = GroupId(groupId),
        groupName = groupName,
        gameId = gameId,
        local = local,
        zoneId = zoneId,
        startsAt = startsAt,
        confirmationDeadline = confirmationDeadline,
        capacity = capacity,
        confirmedCount = confirmedCount,
        declinedCount = declinedCount,
        pendingCount = pendingCount,
        waitlistCount = waitlistCount,
        ownAttendance = ownAttendance?.toDomain() ?: if (ownAttendance == null) null else return null,
        membershipType = membershipTypeDomain,
        mensalistaPriority = mensalistaPriority,
        rosterPreview = rosterPreviewDomain,
    )
}

private fun HomeOwnAttendanceTransport.toDomain(): HomeOwnAttendance? = status.toAttendanceStatus()?.let {
    HomeOwnAttendance(status = it, waitlistPosition = waitlistPosition)
}

private fun HomeRosterPreviewTransport.toDomain() = HomeRosterPreview(
    confirmed = confirmed.map(HomeRosterMemberTransport::toDomain),
    waitlisted = waitlisted.map(HomeRosterMemberTransport::toDomain),
)

private fun HomeRosterMemberTransport.toDomain() = HomeRosterMember(
    displayName = displayName,
    waitlistPosition = waitlistPosition,
)

private fun HomeLastCompletedGameTransport.toDomain() = HomeLastCompletedGame(
    groupId = GroupId(groupId),
    groupName = groupName,
    gameId = gameId,
    zoneId = zoneId,
    startsAt = startsAt,
    confirmedCount = confirmedCount,
    ownPlayed = ownPlayed,
)

private fun HomeAdminTransport.toDomain() = HomeAdminReadModel(groups = groups.map(HomeAdminGroupTransport::toDomain))

private fun HomeAdminGroupTransport.toDomain() = HomeAdminGroup(
    id = GroupId(id),
    name = name,
    entryRequestCount = entryRequestCount,
    monthlyCharges = HomeMonthlyCharges(
        count = monthlyCharges.count,
        totalCents = monthlyCharges.totalCents,
        billingMonth = monthlyCharges.month,
    ),
    gameToSettle = gameToSettle?.let {
        HomeGameToSettle(
            gameId = it.gameId,
            startsAt = it.startsAt,
            zoneId = it.zoneId,
            pendingCount = it.pendingCount,
            totalCents = it.totalCents,
        )
    },
)

private fun String.toGroupRole() = when (this) {
    "OWNER" -> GroupRole.OWNER
    "ADMIN" -> GroupRole.ADMIN
    "ATHLETE" -> GroupRole.ATHLETE
    else -> null
}

private fun String.toAttendanceStatus() = when (this) {
    "CONFIRMED" -> AttendanceStatus.Confirmed
    "DECLINED" -> AttendanceStatus.Declined
    "WAITLISTED" -> AttendanceStatus.Waitlisted
    else -> null
}

private fun String.toMembershipType() = when (this) {
    "MENSALISTA" -> AthleteMembershipType.MENSALISTA
    "AVULSO" -> AthleteMembershipType.AVULSO
    else -> null
}
