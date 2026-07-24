package br.com.saqz.groups.ui.route

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
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
import br.com.saqz.groups.presentation.games.list.GamesEffect
import br.com.saqz.groups.presentation.games.list.GamesViewModel
import br.com.saqz.groups.ui.games.list.GamesTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Games route renders the real list for the selected group (not a placeholder) and
 * turns its list actions into the one-shot effects the navigation host translates into
 * back-stack pushes.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class GamesRootTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `renders the loaded upcoming games of the selected group`() = runComposeUiTest {
        setContent { SaqzTheme { GamesRoot(viewModel(), groupId = GROUP, role = GroupRole.ATHLETE) } }
        waitForIdle()

        onNodeWithTag(GamesTags.card("future")).assertIsDisplayed()
        onNodeWithText("Treino de quinta").assertIsDisplayed()
    }

    @Test
    fun `history tab shows the past games of the same group`() = runComposeUiTest {
        setContent { SaqzTheme { GamesRoot(viewModel(), groupId = GROUP, role = GroupRole.ATHLETE) } }
        waitForIdle()

        onNodeWithTag(GamesTags.Past).performClick()

        onNodeWithTag(GamesTags.card("past")).assertIsDisplayed()
    }

    @Test
    fun `opening a game emits the navigation effect for that game`() = runComposeUiTest {
        val effects = mutableListOf<GamesEffect>()
        val viewModel = viewModel()
        setContent {
            SaqzTheme { GamesRoot(viewModel, groupId = GROUP, role = GroupRole.ATHLETE) }
        }
        collect(viewModel, effects)
        waitForIdle()

        onNodeWithTag(GamesTags.card("future")).performClick()
        waitForIdle()

        assertEquals(listOf<GamesEffect>(GamesEffect.OpenGame(GROUP, "future")), effects)
    }

    @Test
    fun `owner create action emits the create effect`() = runComposeUiTest {
        val effects = mutableListOf<GamesEffect>()
        val viewModel = viewModel()
        setContent {
            SaqzTheme { GamesRoot(viewModel, groupId = GROUP, role = GroupRole.OWNER) }
        }
        collect(viewModel, effects)
        waitForIdle()

        onNodeWithTag(GamesTags.Create).performClick()
        waitForIdle()

        assertEquals(listOf<GamesEffect>(GamesEffect.OpenCreate(GROUP)), effects)
    }

    private fun collect(viewModel: GamesViewModel, into: MutableList<GamesEffect>) {
        CoroutineScope(Dispatchers.Main).launch {
            viewModel.effects.collect { into += it }
        }
    }

    private fun viewModel() = GamesViewModel(FakeGameGateway)

    private companion object {
        const val GROUP = "group-1"

        /** Fixed far-past/far-future dates keep the today boundary deterministic. */
        val games = listOf(
            game("future", "2999-08-13", "Treino de quinta"),
            game("past", "2000-01-15", "Amistoso antigo"),
        )

        fun game(id: String, localDate: String, title: String) = Game(
            id = id,
            groupId = GroupId(GROUP),
            title = title,
            venue = GameVenue(name = "Arena Central", address = "Rua 1"),
            localDate = localDate,
            localTime = "19:30",
            zoneId = "America/Sao_Paulo",
            startsAt = "${localDate}T22:30:00Z",
            durationMinutes = 90,
            capacity = 24,
            confirmationDeadline = "${localDate}T20:00:00Z",
            status = GameStatus.Published,
            version = 1,
            confirmedCount = 3,
            availableSpots = 21,
            waitlistCount = 0,
        )
    }

    private object FakeGameGateway : GameGateway {
        override suspend fun list(groupId: GroupId): SaqzResult<List<Game>, GameError> =
            SaqzResult.Success(games)

        override suspend fun read(groupId: GroupId, gameId: String): SaqzResult<VersionedGame, GameError> =
            SaqzResult.Failure(GameError.HiddenResource)

        override suspend fun create(groupId: GroupId, command: GameWriteCommand): SaqzResult<VersionedGame, GameError> =
            SaqzResult.Failure(GameError.HiddenResource)

        override suspend fun edit(
            groupId: GroupId,
            gameId: String,
            version: GameVersionToken,
            command: GameWriteCommand,
        ): SaqzResult<VersionedGame, GameError> = SaqzResult.Failure(GameError.HiddenResource)

        override suspend fun lifecycle(
            groupId: GroupId,
            gameId: String,
            version: GameVersionToken,
            action: GameLifecycleAction,
        ): SaqzResult<VersionedGame, GameError> = SaqzResult.Failure(GameError.HiddenResource)

        override suspend fun createSeries(
            groupId: GroupId,
            command: WeeklySeriesWriteCommand,
        ): SaqzResult<VersionedSeries, GameError> = SaqzResult.Failure(GameError.HiddenResource)

        override suspend fun readSeries(groupId: GroupId, seriesId: String): SaqzResult<VersionedSeries, GameError> =
            SaqzResult.Failure(GameError.HiddenResource)

        override suspend fun boundary(
            groupId: GroupId,
            seriesId: String,
            version: GameVersionToken,
            command: SeriesBoundaryCommand,
        ): SaqzResult<VersionedSeries, GameError> = SaqzResult.Failure(GameError.HiddenResource)
    }
}
