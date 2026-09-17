package br.com.saqz.groups.presentation.ui.home

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import br.com.saqz.groups.presentation.home.HomeToast
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
 * VUL-222 — a jornada da Home nova, uma cena por passo, na ordem em que a pessoa vive:
 * atleta sem resposta → confirmou (toast) → trocando a resposta → dívida vencida →
 * chave copiada → gestor com pendências → gestor sem jogo → quem é atleta num grupo e
 * gestor no outro. Estado direto no [HomeScreen]; nada de ViewModel ou Koin.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class HomeJourneyScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun s01AthleteWithoutResponse() = capture("flow6b-01-atleta-sem-resposta", athlete())

    @Test
    fun s02AthleteConfirmedWithToast() =
        capture("flow6b-02-atleta-confirmado-toast", athlete(AttendanceStatus.Confirmed).copy(toast = HomeToast.Confirmed))

    @Test
    fun s03AthleteChangingResponse() = captureChanging("flow6b-03-atleta-trocando-resposta")

    @Test
    fun s04AthleteOverdueCharge() =
        capture("flow6b-04-atleta-cobranca-vencida", athlete(AttendanceStatus.Confirmed).copy(ownCharges = previewOwnChargesOverdue()))

    @Test
    fun s05AthletePixCopied() = capture(
        "flow6b-05-atleta-chave-copiada",
        athlete(AttendanceStatus.Confirmed).copy(
            ownCharges = previewOwnChargesOverdue(),
            pixCopiedGroupId = "ceret",
            toast = HomeToast.PixCopied,
        ),
    )

    @Test
    fun s06AdminWithPendingItems() = capture("flow6b-06-gestor-pendencias", admin())

    @Test
    fun s07AdminWithoutGame() = capture(
        "flow6b-07-gestor-sem-jogo",
        admin(upcoming = emptyList()).let { it.copy(member = checkNotNull(it.member).copy(nextGame = null)) },
    )

    @Test
    fun s08AthleteHereAdminThere() = capture("flow6b-08-misto", mixed())

    private fun capture(name: String, state: HomeState) {
        compose.setContent {
            SaqzTheme {
                HomeScreen(state = state, onIntent = {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/vul-222/$name.png")
    }

    private fun captureChanging(name: String) {
        compose.setContent {
            SaqzTheme {
                HomeScreen(state = athlete(AttendanceStatus.Confirmed), onIntent = {})
            }
        }
        compose.onNodeWithTag(HomeTags.ResponseChange).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/vul-222/$name.png")
    }

    private fun athlete(
        attendance: AttendanceStatus? = null,
        upcoming: List<HomeUpcomingGameUi> = upcoming(),
    ) = HomeState(
        isLoading = false,
        displayName = "Bruna",
        member = HomeMemberUi(
            nextGame = nextGame(attendance),
            groups = listOf(
                HomeGroupUi("ceret", "Vôlei do CERET", "26 pessoas · 18 jogos"),
                HomeGroupUi("pacaembu", "Vôlei Pacaembu", "14 pessoas · 6 jogos"),
            ),
            upcomingGames = upcoming,
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

    private fun admin(upcoming: List<HomeUpcomingGameUi> = upcoming()): HomeState {
        val group = HomeAdminGroupUi(
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
        val base = athlete(upcoming = upcoming)
        val member = checkNotNull(base.member)
        return base.copy(
            member = member.copy(
                nextGame = member.nextGame?.copy(
                    declinedCount = 1,
                    pendingCount = 2,
                    adminHeroDeadlineLabel = "Encerra 28/07 · 18h",
                ),
                groups = member.groups.map { if (it.id == "ceret") it.copy(isAdmin = true) else it },
                adminSubtitle = "2 grupos · 3 coisas esperando você",
                admin = HomeAdminReadModelUi(listOf(group)),
            ),
        )
    }

    private fun mixed(): HomeState {
        val base = athlete(AttendanceStatus.Confirmed)
        val member = checkNotNull(base.member)
        return base.copy(
            member = member.copy(
                adminSubtitle = "2 grupos · 1 coisas esperando você",
                groups = member.groups.map { if (it.id == "pacaembu") it.copy(isAdmin = true) else it },
                admin = HomeAdminReadModelUi(
                    listOf(
                        HomeAdminGroupUi(
                            id = "pacaembu",
                            name = "Vôlei Pacaembu",
                            entryRequestCount = 1,
                            monthlyCharges = null,
                            gameToSettle = null,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun upcoming() = listOf(
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
}
