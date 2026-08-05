package br.com.saqz.groups.presentation.ui.home

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.home.HomeGroupUi
import br.com.saqz.groups.presentation.home.HomeLastCompletedGameUi
import br.com.saqz.groups.presentation.home.HomeMemberUi
import br.com.saqz.groups.presentation.home.HomeNextGameUi
import br.com.saqz.groups.presentation.home.HomeState
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.home.HomeWaitlistRowUi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class HomeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loading() = capture("home-loading", HomeState())

    @Test
    fun failure() = capture("home-failure", HomeState(isLoading = false, loadFailed = true))

    @Test
    fun content() = capture(
        "home-content",
        state(),
    )

    @Test
    fun confirmed() = capture("home-content-confirmed", state(AttendanceStatus.Confirmed))

    @Test
    fun declined() = capture("home-content-declined", state(AttendanceStatus.Declined))

    @Test
    fun waitlisted() = capture("home-content-waitlisted", state(AttendanceStatus.Waitlisted))

    @Test
    fun reserva() = capture("home-reserva", state(AttendanceStatus.Waitlisted, reservaGame()))

    @Test
    fun avulsoList() = capture("home-avulso-list", state(AttendanceStatus.Waitlisted, avulsoListGame()))

    @Test
    fun empty() = capture("home-empty", state(nextGame = null))

    private fun capture(name: String, state: HomeState) {
        compose.setContent {
            SaqzTheme {
                HomeScreen(state = state, onIntent = {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/vul-191/$name.png")
    }

    private fun state(
        attendance: AttendanceStatus? = null,
        nextGame: HomeNextGameUi? = nextGame(attendance),
    ) = HomeState(
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
            groups = listOf(
                HomeGroupUi("ceret", "Vôlei do CERET", "26 pessoas · 18 jogos"),
                HomeGroupUi("pacaembu", "Vôlei Pacaembu", "14 pessoas · 6 jogos"),
            ),
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
        rosterNames = listOf("Ana Souza", "Bruna Lima", "Caio", "Duda"),
        ownAttendance = status,
        weekday = "terça",
        time = "19h30",
    )

    private fun reservaGame() = nextGame(AttendanceStatus.Waitlisted).copy(
        confirmedSummary = "12 de 12 confirmados",
        confirmedCount = 12,
        waitlistKind = HomeWaitlistKind.Reserva,
        waitlistPosition = 1,
        confirmedRoster = listOf("Ana Souza", "Bruna Lima", "Caio", "Duda", "Eva Lima", "Tiago Moraes"),
        deadlineBellLabel = "Avisamos você se abrir vaga até 18h00 de 28/07.",
    )

    private fun avulsoListGame() = nextGame(AttendanceStatus.Waitlisted).copy(
        waitlistKind = HomeWaitlistKind.AvulsoList,
        waitlistPosition = 2,
        confirmedRoster = listOf("Ana Souza", "Bruna Lima", "Caio"),
        waitlistedRoster = listOf(
            HomeWaitlistRowUi(name = "Lucas Pereira", position = 1, isSelf = false),
            HomeWaitlistRowUi(name = "Bruna Silva", position = 2, isSelf = true),
            HomeWaitlistRowUi(name = "Tiago Moraes", position = 3, isSelf = false),
        ),
        mensalistaConfirmedCount = 9,
    )
}
