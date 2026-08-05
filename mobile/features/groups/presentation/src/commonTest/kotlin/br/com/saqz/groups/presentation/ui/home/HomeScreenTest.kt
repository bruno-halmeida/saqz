package br.com.saqz.groups.presentation.ui.home

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.home.HomeGroupUi
import br.com.saqz.groups.presentation.home.HomeAdminGroupUi
import br.com.saqz.groups.presentation.home.HomeAdminReadModelUi
import br.com.saqz.groups.presentation.home.HomeGameToSettleUi
import br.com.saqz.groups.presentation.home.HomeMonthlyChargesUi
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeLastCompletedGameUi
import br.com.saqz.groups.presentation.home.HomeMemberUi
import br.com.saqz.groups.presentation.home.HomeNextGameUi
import br.com.saqz.groups.presentation.home.HomeState
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    @Test
    fun `loading state renders the home skeleton`() = runComposeUiTest {
        setScreen(HomeState())

        onNodeWithTag(HomeTags.Loading).assertIsDisplayed()
    }

    @Test
    fun `error state renders the standard retry action`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(HomeState(isLoading = false, loadFailed = true), intents::add)

        onNodeWithTag(HomeTags.Error).assertIsDisplayed()
        onNodeWithText("Não foi possível carregar sua Home").assertIsDisplayed()
        onNodeWithText("Tentar novamente").performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.Retry), intents)
    }

    @Test
    fun `next game state renders hero presence actions status and groups`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(nextGameState(), intents::add)

        onNodeWithText("Fala, Bruna! 👋").assertIsDisplayed()
        onNodeWithText("Terça tem jogo. Confirma?").assertIsDisplayed()
        onNodeWithTag(HomeTags.NextGame).assertIsDisplayed()
        onNodeWithText("PRÓXIMO JOGO").assertIsDisplayed()
        onNodeWithText("CERET — Quadra 2 · Tatuapé").assertIsDisplayed()
        onNodeWithText("9 de 12 confirmados").assertIsDisplayed()
        onNodeWithText("Vou").performClick()
        onNodeWithText("Não vou").performClick()
        onNodeWithTag(HomeTags.Groups).assertIsDisplayed()
        onNodeWithTag(HomeTags.group("ceret")).performClick()

        assertEquals(
            listOf(
                HomeIntent.Respond(AttendanceIntent.Confirm),
                HomeIntent.Respond(AttendanceIntent.Decline),
                HomeIntent.OpenGroup("ceret"),
            ),
            intents,
        )
    }

    @Test
    fun `empty state renders last game and opens groups from both actions`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(nextGameState(nextGame = null), intents::add)

        onNodeWithTag(HomeTags.Empty).assertIsDisplayed()
        onNodeWithText("Nenhum jogo marcado por enquanto").assertIsDisplayed()
        onNodeWithText("Da última vez").assertIsDisplayed()
        onNodeWithText("21").assertIsDisplayed()
        onNodeWithText("Você jogou · 12 confirmados").assertIsDisplayed()
        onNodeWithText("Ver meus grupos").performClick()
        onNodeWithText("Ver todos").performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.OpenGroups, HomeIntent.OpenGroups), intents)
    }

    @Test
    fun `waitlisted reserva state renders the reserva chip box and leave and view game actions`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(
            nextGameState(nextGame = nextGame(AttendanceStatus.Waitlisted).copy(waitlistKind = HomeWaitlistKind.Reserva, waitlistPosition = 1)),
            intents::add,
        )

        onNodeWithText("Reserva · 1º").assertIsDisplayed()
        onNodeWithText("As vagas acabaram. Você entrou na reserva.").assertIsDisplayed()
        onAllNodesWithText("Vou").assertCountEquals(0)
        onAllNodesWithText("Não vou").assertCountEquals(0)
        onNodeWithTag(HomeWaitlistTags.ReservaLeave).assertIsDisplayed()
        onNodeWithTag(HomeWaitlistTags.ReservaViewGame).performClick()
        onNodeWithTag(HomeWaitlistTags.ConfirmedSection).assertIsDisplayed()

        assertEquals(listOf<HomeIntent>(HomeIntent.OpenGame("ceret", "game-1")), intents)
    }

    @Test
    fun `waitlisted avulso list state renders the queue section with the self row highlighted`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(
            nextGameState(
                nextGame = nextGame(AttendanceStatus.Waitlisted).copy(
                    waitlistKind = HomeWaitlistKind.AvulsoList,
                    waitlistPosition = 2,
                    waitlistedRoster = listOf(
                        br.com.saqz.groups.presentation.home.HomeWaitlistRowUi("Lucas Pereira", 1, false),
                        br.com.saqz.groups.presentation.home.HomeWaitlistRowUi("Bruna Silva", 2, true),
                    ),
                    confirmedCountTotal = 9,
                ),
            ),
            intents::add,
        )

        onNodeWithText("Lista de espera").assertIsDisplayed()
        onNodeWithText("Os mensalistas entram primeiro.").assertIsDisplayed()
        onNodeWithText("2º na lista · 9 confirmados").assertIsDisplayed()
        onNodeWithText("Quer entrar direto nos próximos?").assertIsDisplayed()
        onNodeWithTag(HomeWaitlistTags.QueueSection).assertIsDisplayed()
        onNodeWithText("Você").assertIsDisplayed()
        onNodeWithTag(HomeWaitlistTags.AvulsoLeave).performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.Respond(AttendanceIntent.Decline)), intents)
    }

    @Test
    fun `closed confirmation deadline disables the two response buttons`() = runComposeUiTest {
        setScreen(nextGameState(nextGame = nextGame().copy(confirmationOpen = false)))

        onNodeWithText("Vou").assertIsNotEnabled()
        onNodeWithText("Não vou").assertIsNotEnabled()
    }

    @Test
    fun `closed deadline disables the waitlist leave button but keeps view game enabled`() = runComposeUiTest {
        setScreen(
            nextGameState(
                nextGame = nextGame(AttendanceStatus.Waitlisted).copy(
                    waitlistKind = HomeWaitlistKind.Reserva,
                    waitlistPosition = 1,
                    confirmationOpen = false,
                ),
            ),
        )

        onNodeWithTag(HomeWaitlistTags.ReservaLeave).assertIsNotEnabled()
        onNodeWithTag(HomeWaitlistTags.ReservaViewGame).assertIsDisplayed()
    }

    @Test
    fun `admin hero renders the three-way score without member response actions`() = runComposeUiTest {
        setScreen(adminState())

        onNodeWithTag(HomeAdminTags.Hero).assertIsDisplayed()
        onNodeWithText("Vão").assertIsDisplayed()
        onNodeWithText("Não vão").assertIsDisplayed()
        onNodeWithText("Pendentes").assertIsDisplayed()
        onAllNodesWithText("Vou").assertCountEquals(0)
        onAllNodesWithText("Não vou").assertCountEquals(0)
    }

    @Test
    fun `admin waiting rows expose the matching navigation intents`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(adminState(), intents::add)

        onNodeWithTag(HomeAdminTags.entryRequests("ceret")).performClick()
        onNodeWithTag(HomeAdminTags.monthly("ceret")).performClick()
        onNodeWithTag(HomeAdminTags.settle("ceret")).performClick()

        assertEquals(
            listOf(
                HomeIntent.OpenInvite("ceret"),
                HomeIntent.OpenCashbox("ceret"),
                HomeIntent.OpenGameSettlement("ceret", "game-1"),
            ),
            intents,
        )
    }

    @Test
    fun `admin without pending items hides the waiting section`() = runComposeUiTest {
        setScreen(adminState(withPendingItems = false))

        onNodeWithTag(HomeAdminTags.Shortcuts).assertIsDisplayed()
        onAllNodesWithTag(HomeAdminTags.Waiting).assertCountEquals(0)
    }

    @Test
    fun `toast state renders the confirmation feedback`() = runComposeUiTest {
        setScreen(nextGameState().copy(toast = br.com.saqz.groups.presentation.home.HomeToast.Confirmed))

        onNodeWithTag(HomeTags.Toast).assertIsDisplayed()
        onNodeWithText("Presença confirmada. Bom jogo!").assertIsDisplayed()
    }

    private fun ComposeUiTest.setScreen(
        state: HomeState,
        onIntent: (HomeIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            HomeScreen(state = state, onIntent = onIntent)
        }
    }
}

private fun nextGameState(nextGame: HomeNextGameUi? = nextGame()) = HomeState(
    isLoading = false,
    displayName = "Bruna",
    member = HomeMemberUi(
        subtitle = if (nextGame == null) "Semana sem jogo por aqui." else "Terça tem jogo. Confirma?",
        nextGame = nextGame,
        lastCompletedGame = HomeLastCompletedGameUi(
            day = "21",
            month = "JUL",
            title = "Vôlei do CERET · 19h30",
            summary = "Você jogou · 12 confirmados",
        ),
        groups = listOf(HomeGroupUi("ceret", "Vôlei do CERET", "26 pessoas · 18 jogos")),
    ),
)

private fun nextGame(status: AttendanceStatus? = null) = HomeNextGameUi(
    groupId = "ceret",
    gameId = "game-1",
    groupName = "Vôlei do CERET",
    dateTime = "Ter, 28/07 · 19h30",
    local = "CERET — Quadra 2 · Tatuapé",
    deadline = "As confirmações encerram hoje às 18h.",
    confirmedSummary = "9 de 12 confirmados",
    confirmedCount = 9,
    capacity = 12,
    rosterNames = listOf("Ana Souza", "Bruna Lima", "Caio"),
    ownAttendance = status,
    weekday = "terça",
    time = "19h30",
)

private fun adminState(withPendingItems: Boolean = true) = nextGameState().copy(
    member = checkNotNull(nextGameState().member).copy(
        adminSubtitle = if (withPendingItems) "1 grupos · 3 coisas esperando você" else null,
        admin = HomeAdminReadModelUi(
            groups = listOf(
                HomeAdminGroupUi(
                    id = "ceret",
                    name = "Vôlei do CERET",
                    entryRequestCount = if (withPendingItems) 3 else 0,
                    monthlyCharges = if (withPendingItems) {
                        HomeMonthlyChargesUi(2, "R$ 640,00", "JUL")
                    } else {
                        null
                    },
                    gameToSettle = if (withPendingItems) {
                        HomeGameToSettleUi("game-1", "28/07", 4, "R$ 320,00")
                    } else {
                        null
                    },
                ),
            ),
        ),
    ),
)
