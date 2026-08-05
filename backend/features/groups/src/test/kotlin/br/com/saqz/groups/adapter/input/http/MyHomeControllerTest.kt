package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.home.HomeAdminGroup
import br.com.saqz.groups.application.home.HomeAdminReadModel
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
import br.com.saqz.groups.application.home.HomeRepository
import br.com.saqz.groups.application.home.HomeRosterMember
import br.com.saqz.groups.application.home.HomeRosterPreview
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.sharedkernel.RequestIdentity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class MyHomeControllerTest {
    private val actor = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val otherGroup = UUID.randomUUID()
    private val nextGame = UUID.randomUUID()
    private val completedGame = UUID.randomUUID()
    private val identity = RequestIdentity("subject", emailVerified = true)
    private val now = Instant.parse("2026-08-01T12:00:00Z")

    @Test
    fun returnsTheCompleteHomeContractAndResolvedActor() {
        var requestedActor: UUID? = null
        val controller = controller(onFind = { requestedActor = it })

        val response = controller.home(identity)

        assertEquals(actor, requestedActor)
        assertEquals(group, response.member.nextGame?.groupId)
        assertEquals(nextGame, response.member.nextGame?.gameId)
        assertEquals("Quadra Central", response.member.nextGame?.local)
        assertEquals("America/Sao_Paulo", response.member.nextGame?.zoneId)
        assertEquals(now.plusSeconds(3_600), response.member.nextGame?.startsAt)
        assertEquals(now, response.member.nextGame?.confirmationDeadline)
        assertEquals(12, response.member.nextGame?.capacity)
        assertEquals(8, response.member.nextGame?.confirmedCount)
        assertEquals(1, response.member.nextGame?.declinedCount)
        assertEquals(2, response.member.nextGame?.pendingCount)
        assertEquals(3, response.member.nextGame?.waitlistCount)
        assertEquals(HomeOwnAttendanceResponse("WAITLISTED", 7), response.member.nextGame?.ownAttendance)
        assertEquals("AVULSO", response.member.nextGame?.membershipType)
        assertEquals(false, response.member.nextGame?.mensalistaPriority)
        assertEquals(
            listOf(HomeRosterMemberResponse("Ana", null)),
            response.member.nextGame?.rosterPreview?.confirmed,
        )
        assertEquals(
            listOf(HomeRosterMemberResponse("Bruno", 7)),
            response.member.nextGame?.rosterPreview?.waitlisted,
        )
        assertEquals(
            listOf(HomeGroupResponse(group, "Grupo Praia", "ATHLETE", 20, 4)),
            response.member.groups,
        )
        assertEquals(
            HomeLastCompletedGameResponse(
                groupId = group,
                groupName = "Grupo Praia",
                gameId = completedGame,
                zoneId = "America/Sao_Paulo",
                startsAt = now.minusSeconds(86_400),
                confirmedCount = 10,
                ownPlayed = true,
            ),
            response.member.lastCompletedGame,
        )
        assertEquals(
            HomeAdminResponse(
                groups = listOf(
                    HomeAdminGroupResponse(
                        id = group,
                        name = "Grupo Praia",
                        entryRequestCount = 2,
                        monthlyCharges = HomeMonthlyChargesResponse(3, 450, "2026-08"),
                        gameToSettle = HomeGameToSettleResponse(completedGame, now.minusSeconds(86_400), "America/Sao_Paulo", 2, 500),
                    ),
                ),
            ),
            response.admin,
        )
        assertEquals(
            HomeOwnChargesResponse(
                groupCount = 2,
                totalCents = 900,
                groups = listOf(
                    HomeOwnChargeGroupResponse(
                        groupId = group,
                        groupName = "Grupo Praia",
                        count = 2,
                        totalCents = 600,
                        nextDueDate = LocalDate.of(2026, 7, 10),
                        overdue = true,
                        pixKey = "racha@saqz.test",
                        pixLabel = "Tesoureiro",
                        oldest = HomeOwnChargeOldestResponse(
                            kind = "MONTHLY",
                            month = "2026-07",
                            gameId = null,
                            gameStartsAt = null,
                            gameZoneId = null,
                            dueDate = LocalDate.of(2026, 7, 10),
                        ),
                    ),
                    HomeOwnChargeGroupResponse(
                        groupId = otherGroup,
                        groupName = "Grupo Quadra",
                        count = 1,
                        totalCents = 300,
                        nextDueDate = LocalDate.of(2026, 8, 10),
                        overdue = false,
                        pixKey = null,
                        pixLabel = null,
                        oldest = HomeOwnChargeOldestResponse(
                            kind = "GAME",
                            month = null,
                            gameId = completedGame,
                            gameStartsAt = now.minusSeconds(86_400),
                            gameZoneId = "America/Sao_Paulo",
                            dueDate = LocalDate.of(2026, 8, 10),
                        ),
                    ),
                ),
            ),
            response.ownCharges,
        )
    }

    @Test
    fun preservesAnEmptyMemberHomeAndNullAdminBlock() {
        val controller = controller(
            fixed = HomeReadModel(
                member = HomeMemberReadModel(null, null, emptyList()),
                admin = null,
            ),
        )

        val response = controller.home(identity)

        assertNull(response.member.nextGame)
        assertNull(response.member.lastCompletedGame)
        assertEquals(emptyList(), response.member.groups)
        assertNull(response.admin)
        assertNull(response.ownCharges)
    }

    private fun controller(
        onFind: (UUID) -> Unit = {},
        fixed: HomeReadModel? = null,
    ): MyHomeController {
        val repository = object : HomeRepository {
            override fun find(actorId: UUID, now: Instant, today: LocalDate): HomeReadModel {
                onFind(actorId)
                return fixed ?: HomeReadModel(
                    member = HomeMemberReadModel(
                        nextGame = HomeNextGame(
                            groupId = group,
                            groupName = "Grupo Praia",
                            gameId = nextGame,
                            local = "Quadra Central",
                            zoneId = "America/Sao_Paulo",
                            startsAt = now.plusSeconds(3_600),
                            confirmationDeadline = now,
                            capacity = 12,
                            confirmedCount = 8,
                            declinedCount = 1,
                            pendingCount = 2,
                            waitlistCount = 3,
                            ownAttendance = HomeOwnAttendance(AttendanceStatus.WAITLISTED, 7),
                            membershipType = AthleteMembershipType.AVULSO,
                            mensalistaPriority = false,
                            rosterPreview = HomeRosterPreview(
                                confirmed = listOf(HomeRosterMember("Ana", null)),
                                waitlisted = listOf(HomeRosterMember("Bruno", 7)),
                            ),
                        ),
                        lastCompletedGame = HomeLastCompletedGame(
                            groupId = group,
                            groupName = "Grupo Praia",
                            gameId = completedGame,
                            zoneId = "America/Sao_Paulo",
                            startsAt = now.minusSeconds(86_400),
                            confirmedCount = 10,
                            ownPlayed = true,
                        ),
                        groups = listOf(HomeMemberGroup(group, "Grupo Praia", GroupRole.ATHLETE, 20, 4)),
                    ),
                    admin = HomeAdminReadModel(
                        groups = listOf(
                            HomeAdminGroup(
                                id = group,
                                name = "Grupo Praia",
                                entryRequestCount = 2,
                                monthlyCharges = HomeMonthlyCharges(3, 450, YearMonth.from(today)),
                                gameToSettle = HomeGameToSettle(completedGame, now.minusSeconds(86_400), "America/Sao_Paulo", 2, 500),
                            ),
                        ),
                    ),
                    ownCharges = HomeOwnChargesReadModel(
                        groupCount = 2,
                        totalCents = 900,
                        groups = listOf(
                            HomeOwnChargeGroup(
                                groupId = group,
                                groupName = "Grupo Praia",
                                count = 2,
                                totalCents = 600,
                                nextDueDate = LocalDate.of(2026, 7, 10),
                                overdue = true,
                                pixKey = "racha@saqz.test",
                                pixLabel = "Tesoureiro",
                                oldest = HomeOwnChargeOldest.Monthly(
                                    month = YearMonth.of(2026, 7),
                                    dueDate = LocalDate.of(2026, 7, 10),
                                ),
                            ),
                            HomeOwnChargeGroup(
                                groupId = otherGroup,
                                groupName = "Grupo Quadra",
                                count = 1,
                                totalCents = 300,
                                nextDueDate = LocalDate.of(2026, 8, 10),
                                overdue = false,
                                pixKey = null,
                                pixLabel = null,
                                oldest = HomeOwnChargeOldest.Game(
                                    gameId = completedGame,
                                    startsAt = now.minusSeconds(86_400),
                                    zoneId = "America/Sao_Paulo",
                                    dueDate = LocalDate.of(2026, 8, 10),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }
        return MyHomeController(
            actorResolver = VerifiedGroupActorResolver { actor },
            query = HomeQuery(repository, Clock.fixed(now, ZoneId.of("UTC")), ZoneId.of("America/Sao_Paulo")),
        )
    }
}
