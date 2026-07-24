package br.com.saqz.groups.presentation.games.detail

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
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
import br.com.saqz.groups.domain.group.GroupRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * Factory-identity matrix for [GameDetailViewModelFactory] (T14). Derived from
 * LIFE-01..05, GROUPNAV-01.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModelFactoryTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `create threads the route identity into the produced view model`() = runTest(mainDispatcher) {
        val factory = GameDetailViewModelFactory(FakeGameGateway(), attendanceGateway = null, attendanceShareGateway = null)

        val viewModel = factory.create(groupId = "g1", gameId = "game-a", role = GroupRole.OWNER)
        runCurrent()

        assertEquals("g1", viewModel.state.value.groupId)
        assertEquals("game-a", viewModel.state.value.gameId)
        assertEquals(GroupRole.OWNER, viewModel.state.value.role)
    }

    @Test
    fun `each entry receives its own view model instance for the same route identity`() = runTest(mainDispatcher) {
        val factory = GameDetailViewModelFactory(FakeGameGateway(), attendanceGateway = null, attendanceShareGateway = null)

        val first = factory.create(groupId = "g1", gameId = "game-a", role = GroupRole.OWNER)
        val second = factory.create(groupId = "g1", gameId = "game-a", role = GroupRole.OWNER)

        assertNotSame(first, second)
    }

    @Test
    fun `create accepts an explicit SavedStateHandle instead of a shared singleton`() = runTest(mainDispatcher) {
        val factory = GameDetailViewModelFactory(FakeGameGateway(), attendanceGateway = null, attendanceShareGateway = null)
        val handleA = SavedStateHandle()
        val handleB = SavedStateHandle()

        val first = factory.create(groupId = "g1", gameId = "game-a", role = GroupRole.OWNER, savedStateHandle = handleA)
        val second = factory.create(groupId = "g1", gameId = "game-a", role = GroupRole.OWNER, savedStateHandle = handleB)

        assertNotSame(first, second)
    }

    private class FakeGameGateway : GameGateway {
        override suspend fun list(groupId: GroupId): SaqzResult<List<Game>, GameError> = error("unused")
        override suspend fun create(groupId: GroupId, command: GameWriteCommand): SaqzResult<VersionedGame, GameError> =
            error("unused")
        override suspend fun edit(
            groupId: GroupId,
            gameId: String,
            version: GameVersionToken,
            command: GameWriteCommand,
        ): SaqzResult<VersionedGame, GameError> = error("unused")
        override suspend fun createSeries(
            groupId: GroupId,
            command: WeeklySeriesWriteCommand,
        ): SaqzResult<VersionedSeries, GameError> = error("unused")
        override suspend fun readSeries(groupId: GroupId, seriesId: String): SaqzResult<VersionedSeries, GameError> =
            error("unused")
        override suspend fun boundary(
            groupId: GroupId,
            seriesId: String,
            version: GameVersionToken,
            command: SeriesBoundaryCommand,
        ): SaqzResult<VersionedSeries, GameError> = error("unused")

        override suspend fun read(groupId: GroupId, gameId: String): SaqzResult<VersionedGame, GameError> =
            SaqzResult.Success(
                VersionedGame(
                    game = Game(
                        id = gameId,
                        groupId = groupId,
                        title = "Treino",
                        venue = GameVenue(name = "Arena", address = "Rua 1"),
                        localDate = "2026-08-12",
                        localTime = "19:30:00",
                        zoneId = "America/Sao_Paulo",
                        startsAt = "2026-08-12T22:30:00Z",
                        durationMinutes = 90,
                        capacity = 24,
                        confirmationDeadline = "2026-08-12T19:00:00Z",
                        gameFeeCents = 2_500,
                        notes = "Notas",
                        status = GameStatus.Draft,
                        version = 1,
                        confirmedCount = 0,
                        availableSpots = 24,
                        waitlistCount = 0,
                        financeReviewRequired = false,
                    ),
                    version = GameVersionToken("\"1\""),
                ),
            )

        override suspend fun lifecycle(
            groupId: GroupId,
            gameId: String,
            version: GameVersionToken,
            action: GameLifecycleAction,
        ): SaqzResult<VersionedGame, GameError> = error("unused")
    }
}
