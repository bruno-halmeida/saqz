package br.com.saqz.groups.presentation.list

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.*
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.GroupCreationEntitlement
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.sampleGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GroupListNextGameTest {
    private val now = Instant.parse("2026-08-01T12:00:00Z")
    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun cleanup() = Dispatchers.resetMain()

    @Test fun `nearest future game is shown in the group time zone`() = runTest {
        val next = sampleGame().copy(startsAt = "2026-08-04T22:30:00Z", confirmedCount = 9, capacity = 12)
        val games = listOf(next.copy(startsAt = "2026-08-10T22:30:00Z"), next)
        assertEquals("04/08 · 19:30 · 9 de 12", games.nextGroupCardGame(GroupRole.ADMIN, now)?.label)
    }

    @Test fun `cancelled completed and past games are excluded`() = runTest {
        val games = listOf(
            sampleGame().copy(status = GameStatus.Cancelled),
            sampleGame().copy(status = GameStatus.Completed),
            sampleGame().copy(startsAt = "2026-07-01T22:30:00Z"),
        )
        assertNull(games.nextGroupCardGame(GroupRole.ADMIN, now))
    }

    @Test fun `draft is visible to organizer without pretending attendance is pending`() = runTest {
        val games = listOf(sampleGame().copy(status = GameStatus.Draft))
        assertNotNull(games.nextGroupCardGame(GroupRole.ADMIN, now))
        assertNull(games.nextGroupCardGame(GroupRole.ATHLETE, now))
        assertFalse(games.nextGroupCardGame(GroupRole.OWNER, now)!!.needsConfirmation)
    }

    @Test fun `refresh shows newly created game and removes cancelled game`() = runTest {
        val games = FakeGameGateway()
        val vm = viewModel(games)
        assertNull(vm.state.value.groups.single().nextGame)
        games.listResult = SaqzResult.Success(listOf(sampleGame()))
        vm.onIntent(GroupListIntent.Refresh)
        assertNotNull(vm.state.value.groups.single().nextGame)
        games.listResult = SaqzResult.Success(listOf(sampleGame().copy(status = GameStatus.Cancelled)))
        vm.onIntent(GroupListIntent.Refresh)
        assertNull(vm.state.value.groups.single().nextGame)
    }

    @Test fun `failed games request does not claim no upcoming game`() = runTest {
        val vm = viewModel(FakeGameGateway(SaqzResult.Failure(GameError.Data(DataError.Server))))
        assertTrue(vm.state.value.loadFailed)
    }

    private fun viewModel(games: FakeGameGateway) = GroupListViewModel(
        FakeAthleteGateway(ownProfileResult = SaqzResult.Success(OwnAthleteProfile(
            "me", "Bruno", null, listOf(OwnAthleteMembership(
                GroupId("group-1"), "Grupo", GroupRole.ADMIN, null, AthleteMembershipType.MENSALISTA, true,
            )),
        ))),
        FakeGroupGateway(), GroupCreationEntitlement { false }, games, now = { now },
    )
}
