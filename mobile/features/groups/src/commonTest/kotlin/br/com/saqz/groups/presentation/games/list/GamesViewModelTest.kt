package br.com.saqz.groups.presentation.games.list

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.*
import br.com.saqz.groups.domain.group.GroupRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GamesViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `selection clears previous group before loading`() =
        runTest(mainDispatcher) {
            val f = fixture()
            f.vm.onIntent(select(A))
            runCurrent()
            f.gateway.pending[B] =
                CompletableDeferred()
            f.vm.onIntent(select(B))
            assertEquals(B, f.vm.state.value.groupId)
            assertTrue(
                f.vm.state.value.upcoming
                    .isEmpty(),
            )
            assertTrue(
                f.vm.state.value.past
                    .isEmpty(),
            )
            assertTrue(f.vm.state.value.isLoading)
            f.gateway.pending
                .getValue(B)
                .complete(success(emptyList()))
            runCurrent()
        }

    @Test fun `success splits upcoming and past and orders authoritatively`() =
        runTest(mainDispatcher) {
            val f = fixture()
            f.gateway.results[A] =
                success(listOf(game("future-2", "2026-08-20"), game("past", "2026-08-01"), game("future-1", "2026-08-12")))
            f.vm.onIntent(select(A))
            runCurrent()
            assertEquals(
                listOf("future-1", "future-2"),
                f.vm.state.value.upcoming
                    .map { it.id },
            )
            assertEquals(
                listOf("past"),
                f.vm.state.value.past
                    .map { it.id },
            )
        }

    @Test fun `presentation uses pt BR date time and server availability`() =
        runTest(mainDispatcher) {
            val f = fixture()
            f.vm.onIntent(select(A))
            runCurrent()
            val item =
                f.vm.state.value.upcoming
                    .single()
            assertEquals("12/08/2026", item.dateText)
            assertEquals("19:30", item.timeText)
            assertEquals("Arena Central", item.venueText)
            assertEquals(21, item.availableSpots)
            assertEquals(2, item.waitlistCount)
        }

    @Test fun `empty success is distinct from loading and error`() =
        runTest(mainDispatcher) {
            val f = fixture()
            f.gateway.results[A] =
                success(emptyList())
            f.vm.onIntent(select(A))
            runCurrent()
            assertFalse(f.vm.state.value.isLoading)
            assertNull(f.vm.state.value.error)
            assertTrue(
                f.vm.state.value.upcoming
                    .isEmpty(),
            )
        }

    @Test fun `initial failure exposes error without protected content`() =
        runTest(mainDispatcher) {
            val f = fixture()
            f.gateway.results[A] =
                SaqzResult.Failure(GameError.Data(DataError.Connectivity))
            f.vm.onIntent(select(A))
            runCurrent()
            assertEquals(GamesLoadError.UNAVAILABLE, f.vm.state.value.error)
            assertTrue(
                f.vm.state.value.upcoming
                    .isEmpty(),
            )
        }

    @Test fun `refresh keeps content while request is pending`() =
        runTest(mainDispatcher) {
            val f = fixture()
            f.vm.onIntent(select(A))
            runCurrent()
            f.gateway.pending[A] =
                CompletableDeferred()
            f.vm.onIntent(GamesIntent.Refresh)
            runCurrent()
            assertTrue(f.vm.state.value.isRefreshing)
            assertEquals(
                listOf("future"),
                f.vm.state.value.upcoming
                    .map { it.id },
            )
            f.gateway.pending
                .getValue(A)
                .complete(success(listOf(game("new", "2026-08-19"))))
            runCurrent()
            assertEquals(
                listOf("new"),
                f.vm.state.value.upcoming
                    .map { it.id },
            )
        }

    @Test fun `duplicate refresh is single flight`() =
        runTest(mainDispatcher) {
            val f = fixture()
            f.vm.onIntent(select(A))
            runCurrent()
            f.gateway.pending[A] =
                CompletableDeferred()
            f.vm.onIntent(GamesIntent.Refresh)
            f.vm.onIntent(GamesIntent.Refresh)
            runCurrent()
            assertEquals(
                2,
                f.gateway.calls.count {
                    it ==
                        A
                },
            )
            f.gateway.pending
                .getValue(A)
                .complete(success(emptyList()))
            runCurrent()
        }

    @Test fun `stale prior group response cannot replace selected group`() =
        runTest(mainDispatcher) {
            val f = fixture()
            f.gateway.pending[A] =
                CompletableDeferred()
            f.vm.onIntent(select(A))
            runCurrent()
            f.vm.onIntent(select(B))
            runCurrent()
            f.gateway.pending
                .getValue(A)
                .complete(success(listOf(game("stale", "2026-08-20"))))
            runCurrent()
            assertEquals(B, f.vm.state.value.groupId)
            assertFalse(
                f.vm.state.value.upcoming.any {
                    it.id ==
                        "stale"
                },
            )
        }

    @Test fun `organizer receives one create navigation effect`() =
        runTest(mainDispatcher) {
            val f = fixture()
            f.vm.onIntent(select(A, GroupRole.ADMIN))
            runCurrent()
            val effect = async { f.vm.effects.first() }
            f.vm.onIntent(GamesIntent.OpenCreate)
            f.vm.onIntent(GamesIntent.OpenCreate)
            assertEquals(GamesEffect.OpenCreate(A), effect.await())
            assertNull(withTimeoutOrNull(1) { f.vm.effects.first() })
        }

    @Test fun `athlete cannot open create`() =
        runTest(mainDispatcher) {
            val f = fixture()
            f.vm.onIntent(select(A, GroupRole.ATHLETE))
            runCurrent()
            f.vm.onIntent(GamesIntent.OpenCreate)
            assertNull(withTimeoutOrNull(1) { f.vm.effects.first() })
            assertFalse(f.vm.state.value.canCreate)
        }

    @Test fun `member receives one allowed game navigation effect`() =
        runTest(mainDispatcher) {
            val f = fixture()
            f.vm.onIntent(select(A, GroupRole.ATHLETE))
            runCurrent()
            val effect = async { f.vm.effects.first() }
            f.vm.onIntent(GamesIntent.OpenGame("future"))
            f.vm.onIntent(GamesIntent.OpenGame("future"))
            assertEquals(GamesEffect.OpenGame(A, "future"), effect.await())
            assertNull(withTimeoutOrNull(1) { f.vm.effects.first() })
        }

    @Test fun `unknown or loading game open is ignored`() =
        runTest(mainDispatcher) {
            val f = fixture()
            f.gateway.pending[A] = CompletableDeferred()
            f.vm.onIntent(select(A))
            runCurrent()
            f.vm.onIntent(GamesIntent.OpenGame("future"))
            assertNull(withTimeoutOrNull(1) { f.vm.effects.first() })
            f.gateway.pending
                .getValue(A)
                .complete(success(listOf(game("future", "2026-08-12"))))
            runCurrent()
            f.vm.onIntent(GamesIntent.OpenGame("unknown"))
            assertNull(withTimeoutOrNull(1) { f.vm.effects.first() })
        }

    private fun fixture(): Fixture {
        val gateway = FakeGateway()
        gateway.results[A] = success(listOf(game("future", "2026-08-12")))
        gateway.results[B] =
            success(listOf(game("b", "2026-08-13")))
        return Fixture(GamesViewModel(gateway), gateway)
    }

    private fun select(
        group: String,
        role: GroupRole = GroupRole.OWNER,
    ) = GamesIntent.SelectGroup(group, role, "2026-08-10")

    private fun game(
        id: String,
        date: String,
    ) = Game(
        id,
        GroupId(A),
        "Treino semanal",
        GameVenue(null, "Arena Central", "Rua das Flores 100"),
        date,
        "19:30:00",
        "America/Sao_Paulo",
        "${date}T22:30:00Z",
        90,
        24,
        "${date}T19:30:00Z",
        2500,
        null,
        GameStatus.Published,
        1,
        3,
        21,
        2,
    )

    private fun success(games: List<Game>) = SaqzResult.Success(games)

    private class FakeGateway : GameGateway {
        val results = mutableMapOf<String, SaqzResult<List<Game>, GameError>>()
        val pending = mutableMapOf<String, CompletableDeferred<SaqzResult<List<Game>, GameError>>>()
        val calls = mutableListOf<String>()

        override suspend fun list(groupId: GroupId): SaqzResult<List<Game>, GameError> {
            calls += groupId.value
            return pending[groupId.value]?.await() ?: results.getValue(groupId.value)
        }

        override suspend fun read(
            groupId: GroupId,
            gameId: String,
        ) = error("unused")

        override suspend fun create(
            groupId: GroupId,
            command: GameWriteCommand,
        ) = error("unused")

        override suspend fun edit(
            groupId: GroupId,
            gameId: String,
            version: GameVersionToken,
            command: GameWriteCommand,
        ) = error("unused")

        override suspend fun lifecycle(
            groupId: GroupId,
            gameId: String,
            version: GameVersionToken,
            action: GameLifecycleAction,
        ) = error("unused")

        override suspend fun createSeries(
            groupId: GroupId,
            command: WeeklySeriesWriteCommand,
        ) = error("unused")

        override suspend fun readSeries(
            groupId: GroupId,
            seriesId: String,
        ) = error("unused")

        override suspend fun boundary(
            groupId: GroupId,
            seriesId: String,
            version: GameVersionToken,
            command: SeriesBoundaryCommand,
        ) = error("unused")
    }

    private data class Fixture(
        val vm: GamesViewModel,
        val gateway: FakeGateway,
    )

    private companion object {
        const val A = "group-a"
        const val B = "group-b"
    }
}
