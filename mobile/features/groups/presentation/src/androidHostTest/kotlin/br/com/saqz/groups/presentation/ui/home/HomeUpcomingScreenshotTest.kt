package br.com.saqz.groups.presentation.ui.home

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.home.HomeAdminGroupUi
import br.com.saqz.groups.presentation.home.HomeAdminReadModelUi
import br.com.saqz.groups.presentation.home.HomeGameToSettleUi
import br.com.saqz.groups.presentation.home.HomeGroupUi
import br.com.saqz.groups.presentation.home.HomeMemberUi
import br.com.saqz.groups.presentation.home.HomeMonthlyChargesUi
import br.com.saqz.groups.presentation.home.HomeNextGameUi
import br.com.saqz.groups.presentation.home.HomeState
import br.com.saqz.groups.presentation.home.HomeUpcomingGameUi
import br.com.saqz.groups.presentation.home.HomeUpcomingStatus
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * VUL-221 — a seção "Próximos jogos" nos dois papéis e nos quatro estados do chip:
 * "Sem resposta" e "Você vai" nas duas primeiras cenas, "Na espera" e "Não vai" na
 * terceira. Estado que não está na cena não está sendo conferido.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class HomeUpcomingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun athleteWithUpcomingGames() = capture("home-proximos-atleta", state(upcomingGames = pendingAndGoing()))

    @Test
    fun adminWithUpcomingGames() = capture("home-proximos-gestor", adminState(pendingAndGoing()))

    @Test
    fun waitlistedAndDeclinedUpcomingGames() =
        capture("home-proximos-espera-e-fora", state(upcomingGames = waitlistedAndOut()))

    private fun capture(name: String, state: HomeState) {
        compose.setContent {
            SaqzTheme {
                HomeScreen(state = state, onIntent = {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/vul-221/$name.png")
    }

    private fun state(
        attendance: AttendanceStatus? = null,
        nextGame: HomeNextGameUi? = nextGame(attendance),
        upcomingGames: List<HomeUpcomingGameUi> = emptyList(),
    ) = HomeState(
        isLoading = false,
        displayName = "Bruna",
        member = HomeMemberUi(
            nextGame = nextGame,
            groups = listOf(
                HomeGroupUi("ceret", "Vôlei do CERET", "26 pessoas · 18 jogos"),
                HomeGroupUi("pacaembu", "Vôlei Pacaembu", "14 pessoas · 6 jogos"),
            ),
            upcomingGames = upcomingGames,
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
        display = "Terça, 19h30",
        meta = "28 de julho · CERET — Quadra 2 · Tatuapé",
    )

    private fun adminState(upcomingGames: List<HomeUpcomingGameUi>): HomeState {
        val admin = HomeAdminGroupUi(
            id = "ceret",
            name = "Vôlei do CERET",
            entryRequestCount = 3,
            monthlyCharges = HomeMonthlyChargesUi(count = 2, formattedTotal = "R$ 640,00", month = "JUL"),
            gameToSettle = HomeGameToSettleUi(
                gameId = "game-1",
                formattedDate = "28/07",
                diaristCount = 4,
                formattedTotal = "R$ 320,00",
            ),
        )
        val base = state(upcomingGames = upcomingGames)
        val member = checkNotNull(base.member)
        return base.copy(
            member = member.copy(
                nextGame = member.nextGame?.copy(
                    declinedCount = 1,
                    pendingCount = 2,
                    adminHeroDeadlineLabel = "Encerra 28/07 · 18h",
                ),
                groups = member.groups.map { group ->
                    if (group.id == "ceret") group.copy(isAdmin = true) else group
                },
                adminSubtitle = "2 grupos · 3 coisas esperando você",
                admin = HomeAdminReadModelUi(listOf(admin)),
            ),
        )
    }

    private fun pendingAndGoing() = listOf(
        HomeUpcomingGameUi(
            groupId = "pacaembu",
            gameId = "game-3",
            day = "30",
            month = "JUL",
            title = "Vôlei Pacaembu · 20h00",
            meta = "Quinta · 6 confirmados",
            status = HomeUpcomingStatus.Pending,
            statusLabel = "Sem resposta",
            contentDescription = "Vôlei Pacaembu, 30/07 às 20h00, Sem resposta",
        ),
        HomeUpcomingGameUi(
            groupId = "ceret",
            gameId = "game-4",
            day = "4",
            month = "AGO",
            title = "Vôlei do CERET · 19h30",
            meta = "Terça · 3 confirmados",
            status = HomeUpcomingStatus.Going,
            statusLabel = "Você vai",
            contentDescription = "Vôlei do CERET, 04/08 às 19h30, Você vai",
        ),
    )

    private fun waitlistedAndOut() = listOf(
        HomeUpcomingGameUi(
            groupId = "ceret",
            gameId = "game-5",
            day = "11",
            month = "AGO",
            title = "Vôlei do CERET · 19h30",
            meta = "Terça · 12 confirmados",
            status = HomeUpcomingStatus.Waitlisted,
            statusLabel = "Na espera",
            contentDescription = "Vôlei do CERET, 11/08 às 19h30, Na espera",
        ),
        HomeUpcomingGameUi(
            groupId = "pacaembu",
            gameId = "game-6",
            day = "13",
            month = "AGO",
            title = "Vôlei Pacaembu · 20h00",
            meta = "Quinta · 4 confirmados",
            status = HomeUpcomingStatus.Out,
            statusLabel = "Não vai",
            contentDescription = "Vôlei Pacaembu, 13/08 às 20h00, Não vai",
        ),
    )
}
