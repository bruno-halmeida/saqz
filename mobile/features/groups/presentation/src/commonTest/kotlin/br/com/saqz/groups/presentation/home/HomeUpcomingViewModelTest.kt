package br.com.saqz.groups.presentation.home

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.home.HomeError
import br.com.saqz.groups.domain.home.HomeGateway
import br.com.saqz.groups.domain.home.HomeMemberGroup
import br.com.saqz.groups.domain.home.HomeMemberReadModel
import br.com.saqz.groups.domain.home.HomeReadModel
import br.com.saqz.groups.domain.home.HomeUpcomingGame
import br.com.saqz.groups.port.GroupNowPort
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeUpcomingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = GroupNowPort { Instant.parse("2026-07-28T12:00:00Z") }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `maps the four statuses and formats the row in the game's timezone`() = runTest {
        val viewModel = viewModel(
            listOf(
                upcoming("game-3", "Vôlei Pacaembu", "2026-07-30T23:00:00Z", confirmed = 6, status = null),
                upcoming("game-4", "Vôlei do CERET", "2026-08-04T22:30:00Z", confirmed = 3, status = AttendanceStatus.Confirmed),
                upcoming("game-5", "Vôlei do CERET", "2026-08-11T22:30:00Z", confirmed = 12, status = AttendanceStatus.Waitlisted),
                upcoming("game-6", "Vôlei Pacaembu", "2026-08-13T23:00:00Z", confirmed = 4, status = AttendanceStatus.Declined),
            ),
        )

        val rows = viewModel.state.value.member?.upcomingGames.orEmpty()
        assertEquals(listOf("30", "4", "11", "13"), rows.map { it.day })
        assertEquals(listOf("JUL", "AGO", "AGO", "AGO"), rows.map { it.month })
        assertEquals("Vôlei Pacaembu · 20h00", rows[0].title)
        assertEquals("Quinta · 6 confirmados", rows[0].meta)
        assertEquals(
            listOf(HomeUpcomingStatus.Pending, HomeUpcomingStatus.Going, HomeUpcomingStatus.Waitlisted, HomeUpcomingStatus.Out),
            rows.map { it.status },
        )
        assertEquals(listOf("Sem resposta", "Você vai", "Na espera", "Não vai"), rows.map { it.statusLabel })
        assertEquals("Vôlei Pacaembu, 30/07 às 20h00, Sem resposta", rows[0].contentDescription)
    }

    @Test
    fun `home without upcoming games maps an empty list`() = runTest {
        val viewModel = viewModel(emptyList())

        assertEquals(emptyList(), viewModel.state.value.member?.upcomingGames)
    }

    private fun viewModel(games: List<HomeUpcomingGame>) = HomeViewModel(
        homeGateway = object : HomeGateway {
            override suspend fun read(): SaqzResult<HomeReadModel, HomeError> = SaqzResult.Success(
                HomeReadModel(
                    member = HomeMemberReadModel(
                        nextGame = null,
                        lastCompletedGame = null,
                        groups = listOf(HomeMemberGroup(GroupId("group-1"), "Vôlei do CERET", GroupRole.ATHLETE, 12, 3)),
                        upcomingGames = games,
                    ),
                    admin = null,
                ),
            )
        },
        athleteGateway = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(OwnAthleteProfile("me", "Bruna Silva", null, emptyList())),
        ),
        attendanceGateway = FakeAttendanceGateway(),
        now = now,
    )

    private fun upcoming(
        gameId: String,
        groupName: String,
        startsAt: String,
        confirmed: Int,
        status: AttendanceStatus?,
    ) = HomeUpcomingGame(
        groupId = GroupId(if (groupName.contains("CERET")) "group-1" else "group-2"),
        groupName = groupName,
        gameId = gameId,
        zoneId = "America/Sao_Paulo",
        startsAt = startsAt,
        confirmationDeadline = startsAt,
        capacity = 12,
        confirmedCount = confirmed,
        ownStatus = status,
    )
}
