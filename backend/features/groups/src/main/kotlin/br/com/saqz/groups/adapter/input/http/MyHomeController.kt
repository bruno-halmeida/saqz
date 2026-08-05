package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.home.HomeAdminGroup
import br.com.saqz.groups.application.home.HomeGameToSettle
import br.com.saqz.groups.application.home.HomeLastCompletedGame
import br.com.saqz.groups.application.home.HomeMemberGroup
import br.com.saqz.groups.application.home.HomeMemberReadModel
import br.com.saqz.groups.application.home.HomeMonthlyCharges
import br.com.saqz.groups.application.home.HomeNextGame
import br.com.saqz.groups.application.home.HomeOwnAttendance
import br.com.saqz.groups.application.home.HomeOwnChargeGroup
import br.com.saqz.groups.application.home.HomeOwnChargeOldest
import br.com.saqz.groups.application.home.HomeOwnChargesReadModel
import br.com.saqz.groups.application.home.HomeQuery
import br.com.saqz.groups.application.home.HomeReadModel
import br.com.saqz.groups.application.home.HomeRosterMember
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class HomeGroupResponse(
    val id: UUID,
    val name: String,
    val role: String,
    val memberCount: Int,
    val gamesPlayed: Int,
)

data class HomeRosterMemberResponse(
    val displayName: String,
    val waitlistPosition: Long?,
)

data class HomeRosterPreviewResponse(
    val confirmed: List<HomeRosterMemberResponse>,
    val waitlisted: List<HomeRosterMemberResponse>,
)

data class HomeOwnAttendanceResponse(
    val status: String,
    val waitlistPosition: Long?,
)

data class HomeNextGameResponse(
    val groupId: UUID,
    val groupName: String,
    val gameId: UUID,
    val local: String,
    val zoneId: String,
    val startsAt: Instant,
    val confirmationDeadline: Instant,
    val capacity: Int,
    val confirmedCount: Int,
    val declinedCount: Int,
    val pendingCount: Int,
    val waitlistCount: Int,
    val ownAttendance: HomeOwnAttendanceResponse?,
    val membershipType: String,
    val mensalistaPriority: Boolean,
    val rosterPreview: HomeRosterPreviewResponse,
)

data class HomeLastCompletedGameResponse(
    val groupId: UUID,
    val groupName: String,
    val gameId: UUID,
    val zoneId: String,
    val startsAt: Instant,
    val confirmedCount: Int,
    val ownPlayed: Boolean,
)

data class HomeMemberResponse(
    val nextGame: HomeNextGameResponse?,
    val lastCompletedGame: HomeLastCompletedGameResponse?,
    val groups: List<HomeGroupResponse>,
)

data class HomeMonthlyChargesResponse(
    val count: Int,
    val totalCents: Long,
    val month: String,
)

data class HomeGameToSettleResponse(
    val gameId: UUID,
    val startsAt: Instant,
    val zoneId: String,
    val pendingCount: Int,
    val totalCents: Long,
)

data class HomeAdminGroupResponse(
    val id: UUID,
    val name: String,
    val entryRequestCount: Int,
    val monthlyCharges: HomeMonthlyChargesResponse,
    val gameToSettle: HomeGameToSettleResponse?,
)

data class HomeAdminResponse(
    val groups: List<HomeAdminGroupResponse>,
)

data class HomeOwnChargeOldestResponse(
    val kind: String,
    val month: String?,
    val gameId: UUID?,
    val gameStartsAt: Instant?,
    val gameZoneId: String?,
    val dueDate: LocalDate,
)

data class HomeOwnChargeGroupResponse(
    val groupId: UUID,
    val groupName: String,
    val count: Int,
    val totalCents: Long,
    val nextDueDate: LocalDate,
    val overdue: Boolean,
    val pixKey: String?,
    val pixLabel: String?,
    val oldest: HomeOwnChargeOldestResponse,
)

data class HomeOwnChargesResponse(
    val groupCount: Int,
    val totalCents: Long,
    val groups: List<HomeOwnChargeGroupResponse>,
)

data class HomeResponse(
    val member: HomeMemberResponse,
    val admin: HomeAdminResponse?,
    val ownCharges: HomeOwnChargesResponse?,
)

@RestController
class MyHomeController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val query: HomeQuery,
) {
    @GetMapping("/api/me/home")
    fun home(@AuthenticationPrincipal identity: RequestIdentity): HomeResponse =
        query.execute(actorResolver.resolve(identity)).toResponse()
}

private fun HomeReadModel.toResponse() = HomeResponse(
    member = member.toResponse(),
    admin = admin?.let { HomeAdminResponse(it.groups.map(HomeAdminGroup::toResponse)) },
    ownCharges = ownCharges?.toResponse(),
)

private fun HomeOwnChargesReadModel.toResponse() = HomeOwnChargesResponse(
    groupCount = groupCount,
    totalCents = totalCents,
    groups = groups.map(HomeOwnChargeGroup::toResponse),
)

private fun HomeOwnChargeGroup.toResponse() = HomeOwnChargeGroupResponse(
    groupId = groupId,
    groupName = groupName,
    count = count,
    totalCents = totalCents,
    nextDueDate = nextDueDate,
    overdue = overdue,
    pixKey = pixKey,
    pixLabel = pixLabel,
    oldest = oldest.toResponse(),
)

private fun HomeOwnChargeOldest.toResponse() = when (this) {
    is HomeOwnChargeOldest.Monthly ->
        HomeOwnChargeOldestResponse("MONTHLY", month.toString(), null, null, null, dueDate)
    is HomeOwnChargeOldest.Game ->
        HomeOwnChargeOldestResponse("GAME", null, gameId, startsAt, zoneId, dueDate)
}

private fun HomeMemberReadModel.toResponse() = HomeMemberResponse(
    nextGame = nextGame?.toResponse(),
    lastCompletedGame = lastCompletedGame?.toResponse(),
    groups = groups.map(HomeMemberGroup::toResponse),
)

private fun HomeMemberGroup.toResponse() = HomeGroupResponse(
    id = id,
    name = name,
    role = role.name,
    memberCount = memberCount,
    gamesPlayed = gamesPlayed,
)

private fun HomeNextGame.toResponse() = HomeNextGameResponse(
    groupId = groupId,
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
    ownAttendance = ownAttendance?.toResponse(),
    membershipType = membershipType.name,
    mensalistaPriority = mensalistaPriority,
    rosterPreview = rosterPreview.let {
        HomeRosterPreviewResponse(
            confirmed = it.confirmed.map(HomeRosterMember::toResponse),
            waitlisted = it.waitlisted.map(HomeRosterMember::toResponse),
        )
    },
)

private fun HomeOwnAttendance.toResponse() = HomeOwnAttendanceResponse(status.name, waitlistPosition)

private fun HomeRosterMember.toResponse() = HomeRosterMemberResponse(displayName, waitlistPosition)

private fun HomeLastCompletedGame.toResponse() = HomeLastCompletedGameResponse(
    groupId = groupId,
    groupName = groupName,
    gameId = gameId,
    zoneId = zoneId,
    startsAt = startsAt,
    confirmedCount = confirmedCount,
    ownPlayed = ownPlayed,
)

private fun HomeAdminGroup.toResponse() = HomeAdminGroupResponse(
    id = id,
    name = name,
    entryRequestCount = entryRequestCount,
    monthlyCharges = monthlyCharges.toResponse(),
    gameToSettle = gameToSettle?.toResponse(),
)

private fun HomeMonthlyCharges.toResponse() = HomeMonthlyChargesResponse(count, totalCents, billingMonth.toString())

private fun HomeGameToSettle.toResponse() = HomeGameToSettleResponse(
    gameId = gameId,
    startsAt = startsAt,
    zoneId = zoneId,
    pendingCount = pendingCount,
    totalCents = totalCents,
)
