package br.com.saqz.groups.presentation.games.detail

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameLifecycleAction as DomainGameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.GameWriteCommand
import br.com.saqz.groups.domain.game.SeriesBoundaryCommand
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.domain.game.VersionedSeries
import br.com.saqz.groups.domain.game.WeeklySeriesWriteCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModelTest {
    @Test
    fun `initial load exposes authoritative snapshot and version`() = runTest {
        val fixture = fixture(this)

        runCurrent()

        assertEquals("Treino", fixture.viewModel.state.value.game?.title)
        assertEquals(GameVersionToken("\"7\""), fixture.viewModel.state.value.version)
        assertFalse(fixture.viewModel.state.value.isLoading)
    }

    @Test
    fun `hidden initial load exposes hidden error`() = runTest {
        val fixture = fixture(this, readResult = SaqzResult.Failure(GameError.HiddenResource))

        runCurrent()

        assertNull(fixture.viewModel.state.value.game)
        assertEquals(GameDetailError.HIDDEN, fixture.viewModel.state.value.error)
    }

    @Test
    fun `data failure on initial load exposes unavailable error`() = runTest {
        val fixture = fixture(this, readResult = SaqzResult.Failure(GameError.Authentication))

        runCurrent()

        assertNull(fixture.viewModel.state.value.game)
        assertEquals(GameDetailError.UNAVAILABLE, fixture.viewModel.state.value.error)
    }

    @Test
    fun `athlete sees snapshot without organizer actions`() = runTest {
        val fixture = fixture(this, role = GroupRole.ATHLETE)

        runCurrent()

        assertTrue(fixture.viewModel.state.value.actions.isEmpty())
        assertFalse(fixture.viewModel.state.value.canEdit)
    }

    @Test
    fun `draft organizer can edit and publish only`() = runTest {
        val fixture = fixture(this)

        runCurrent()

        assertTrue(fixture.viewModel.state.value.canEdit)
        assertEquals(listOf(GameLifecycleAction.PUBLISH), fixture.viewModel.state.value.actions)
    }

    @Test
    fun `published organizer can edit cancel and complete`() = runTest {
        val fixture = fixture(this, initial = versioned(GameStatus.Published))

        runCurrent()

        assertEquals(
            listOf(GameLifecycleAction.CANCEL, GameLifecycleAction.COMPLETE),
            fixture.viewModel.state.value.actions,
        )
        assertTrue(fixture.viewModel.state.value.canEdit)
    }

    @Test
    fun `request lifecycle requires confirmation before gateway call`() = runTest {
        val fixture = fixture(this)
        runCurrent()

        fixture.viewModel.onIntent(GameDetailIntent.RequestLifecycle(GameLifecycleAction.PUBLISH))

        assertEquals(GameLifecycleAction.PUBLISH, fixture.viewModel.state.value.pendingAction)
        assertTrue(fixture.gateway.lifecycleCalls.isEmpty())
    }

    @Test
    fun `dismiss confirmation clears pending lifecycle action`() = runTest {
        val fixture = fixture(this)
        runCurrent()
        fixture.viewModel.onIntent(GameDetailIntent.RequestLifecycle(GameLifecycleAction.PUBLISH))

        fixture.viewModel.onIntent(GameDetailIntent.DismissConfirmation)

        assertNull(fixture.viewModel.state.value.pendingAction)
    }

    @Test
    fun `confirmed lifecycle uses exact version updates snapshot and emits once`() = runTest {
        val fixture = fixture(this)
        runCurrent()
        val effect = async { fixture.viewModel.effects.first() }

        fixture.viewModel.onIntent(GameDetailIntent.RequestLifecycle(GameLifecycleAction.PUBLISH))
        fixture.viewModel.onIntent(GameDetailIntent.ConfirmLifecycle)
        runCurrent()

        assertEquals(GameVersionToken("\"7\""), fixture.gateway.lifecycleCalls.single().version)
        assertEquals(DomainGameLifecycleAction.Publish, fixture.gateway.lifecycleCalls.single().action)
        assertEquals(GameStatus.Published, fixture.viewModel.state.value.game?.status)
        assertEquals(
            GameDetailEffect.LifecycleApplied(GameLifecycleAction.PUBLISH),
            effect.await(),
        )
    }

    @Test
    fun `duplicate confirm is single flight`() = runTest {
        val fixture = fixture(this)
        runCurrent()
        fixture.gateway.gate = CompletableDeferred()

        fixture.viewModel.onIntent(GameDetailIntent.RequestLifecycle(GameLifecycleAction.PUBLISH))
        fixture.viewModel.onIntent(GameDetailIntent.ConfirmLifecycle)
        fixture.viewModel.onIntent(GameDetailIntent.ConfirmLifecycle)
        runCurrent()

        assertEquals(1, fixture.gateway.lifecycleCalls.size)
        fixture.gateway.gate?.complete(Unit)
        runCurrent()
    }

    @Test
    fun `conflict preserves snapshot and offers authoritative reload`() = runTest {
        val fixture = fixture(
            scope = this,
            lifecycleResult = SaqzResult.Failure(GameError.Conflict),
        )
        runCurrent()

        fixture.viewModel.onIntent(GameDetailIntent.RequestLifecycle(GameLifecycleAction.PUBLISH))
        fixture.viewModel.onIntent(GameDetailIntent.ConfirmLifecycle)
        runCurrent()

        assertEquals(GameStatus.Draft, fixture.viewModel.state.value.game?.status)
        assertEquals(GameDetailError.CONFLICT, fixture.viewModel.state.value.error)
        assertTrue(fixture.viewModel.state.value.reloadAvailable)

        fixture.gateway.readResult = SaqzResult.Success(versioned(GameStatus.Published))
        fixture.viewModel.onIntent(GameDetailIntent.Reload)
        runCurrent()

        assertEquals(GameStatus.Published, fixture.viewModel.state.value.game?.status)
    }

    @Test
    fun `invalid lifecycle preserves snapshot and offers reload`() = runTest {
        val fixture = fixture(
            scope = this,
            lifecycleResult = SaqzResult.Failure(GameError.InvalidLifecycle),
        )
        runCurrent()

        fixture.viewModel.onIntent(GameDetailIntent.RequestLifecycle(GameLifecycleAction.PUBLISH))
        fixture.viewModel.onIntent(GameDetailIntent.ConfirmLifecycle)
        runCurrent()

        assertEquals(GameStatus.Draft, fixture.viewModel.state.value.game?.status)
        assertEquals(GameDetailError.INVALID_LIFECYCLE, fixture.viewModel.state.value.error)
        assertTrue(fixture.viewModel.state.value.reloadAvailable)
    }

    @Test
    fun `cancelled snapshot is terminal and ignores edit`() = runTest {
        val fixture = fixture(this, initial = versioned(GameStatus.Cancelled))
        runCurrent()
        val effect = async { withTimeoutOrNull(1) { fixture.viewModel.effects.first() } }

        fixture.viewModel.onIntent(GameDetailIntent.OpenEdit)

        assertTrue(fixture.viewModel.state.value.terminal)
        assertTrue(fixture.viewModel.state.value.actions.isEmpty())
        assertNull(effect.await())
    }

    @Test
    fun `completed snapshot is terminal without lifecycle actions`() = runTest {
        val fixture = fixture(this, initial = versioned(GameStatus.Completed))

        runCurrent()

        assertTrue(fixture.viewModel.state.value.terminal)
        assertFalse(fixture.viewModel.state.value.canEdit)
        assertTrue(fixture.viewModel.state.value.actions.isEmpty())
    }

    @Test
    fun `refresh replaces snapshot with latest authoritative version`() = runTest {
        val fixture = fixture(this)
        runCurrent()
        fixture.gateway.readResult = SaqzResult.Success(
            versioned(GameStatus.Published, version = "\"8\""),
        )

        fixture.viewModel.onIntent(GameDetailIntent.Refresh)
        runCurrent()

        assertEquals(GameStatus.Published, fixture.viewModel.state.value.game?.status)
        assertEquals(GameVersionToken("\"8\""), fixture.viewModel.state.value.version)
    }

    private fun fixture(
        scope: CoroutineScope,
        role: GroupRole = GroupRole.OWNER,
        initial: VersionedGame = versioned(),
        readResult: SaqzResult<VersionedGame, GameError> = SaqzResult.Success(initial),
        lifecycleResult: SaqzResult<VersionedGame, GameError> =
            SaqzResult.Success(versioned(GameStatus.Published)),
    ): Fixture {
        val gateway = FakeGateway(readResult, lifecycleResult)
        return Fixture(
            viewModel = GameDetailViewModel(gateway, "group", "game", role, scope),
            gateway = gateway,
        )
    }

    private fun versioned(
        status: GameStatus = GameStatus.Draft,
        version: String = "\"7\"",
    ) = VersionedGame(
        game = Game(
            id = "game",
            groupId = GroupId("group"),
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
            status = status,
            version = 7,
            confirmedCount = 3,
            availableSpots = 21,
            waitlistCount = 2,
            financeReviewRequired = status == GameStatus.Cancelled,
        ),
        version = GameVersionToken(version),
    )

    private data class LifecycleCall(
        val version: GameVersionToken,
        val action: DomainGameLifecycleAction,
    )

    private class FakeGateway(
        var readResult: SaqzResult<VersionedGame, GameError>,
        var lifecycleResult: SaqzResult<VersionedGame, GameError>,
    ) : GameGateway {
        val lifecycleCalls = mutableListOf<LifecycleCall>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun read(groupId: GroupId, gameId: String) = readResult

        override suspend fun lifecycle(
            groupId: GroupId,
            gameId: String,
            version: GameVersionToken,
            action: DomainGameLifecycleAction,
        ): SaqzResult<VersionedGame, GameError> {
            lifecycleCalls += LifecycleCall(version, action)
            gate?.await()
            return lifecycleResult
        }

        override suspend fun list(groupId: GroupId) = unused<List<Game>>()

        override suspend fun create(groupId: GroupId, command: GameWriteCommand) =
            unused<VersionedGame>()

        override suspend fun edit(
            groupId: GroupId,
            gameId: String,
            version: GameVersionToken,
            command: GameWriteCommand,
        ) = unused<VersionedGame>()

        override suspend fun createSeries(groupId: GroupId, command: WeeklySeriesWriteCommand) =
            unused<VersionedSeries>()

        override suspend fun readSeries(groupId: GroupId, seriesId: String) =
            unused<VersionedSeries>()

        override suspend fun boundary(
            groupId: GroupId,
            seriesId: String,
            version: GameVersionToken,
            command: SeriesBoundaryCommand,
        ) = unused<VersionedSeries>()

        private fun <T> unused(): SaqzResult<T, GameError> = error("unused")
    }

    private data class Fixture(
        val viewModel: GameDetailViewModel,
        val gateway: FakeGateway,
    )
}
