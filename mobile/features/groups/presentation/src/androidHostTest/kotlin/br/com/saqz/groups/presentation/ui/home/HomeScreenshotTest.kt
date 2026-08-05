package br.com.saqz.groups.presentation.ui.home

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.home.HomeGroupUi
import br.com.saqz.groups.presentation.home.HomeAdminGroupUi
import br.com.saqz.groups.presentation.home.HomeAdminReadModelUi
import br.com.saqz.groups.presentation.home.HomeGameToSettleUi
import br.com.saqz.groups.presentation.home.HomeMonthlyChargesUi
import br.com.saqz.groups.presentation.home.HomeLastCompletedGameUi
import br.com.saqz.groups.presentation.home.HomeMemberUi
import br.com.saqz.groups.presentation.home.HomeNextGameUi
import br.com.saqz.groups.presentation.home.HomeOwnChargesUi
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
@Suppress("TooManyFunctions")
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

    @Test
    fun adminWithPendingItems() = captureAdmin("home-admin-pending", adminState())

    @Test
    fun adminWithoutPendingItems() = captureAdmin("home-admin-empty", adminState(withPendingItems = false))

    @Test
    fun adminAndMemberMixed() = captureAdmin("home-admin-member-mixed", mixedAdminState())

    // VUL-202: os quatro estados do aviso de cobrança em aberto. "Sem pendência" é a mesma
    // Home de sempre e entra na pasta do ticket de propósito — é a foto do que precisa
    // continuar igual quando o admin baixa a cobrança.
    @Test
    fun withoutOwnCharges() = captureOwnCharges("home-sem-cobranca", state())

    @Test
    fun ownChargesOnTime() = captureOwnCharges(
        "home-cobranca-no-prazo",
        state().copy(ownCharges = previewOwnCharges()),
    )

    @Test
    fun ownChargesOverdue() = captureOwnCharges(
        "home-cobranca-vencida",
        state().copy(ownCharges = previewOwnChargesOverdue()),
    )

    /**
     * O caso que a nomenclatura precisa resolver: quem recebe e deve na mesma tela. Sem o
     * card "Da última vez" — ele não tem nada com o assunto e empurrava o "Esperando você"
     * para fora do quadro, que é justamente o rótulo que esta cena existe para comparar.
     */
    @Test
    fun ownChargesForAnAdminWhoAlsoOwes() = captureOwnCharges(
        "home-cobranca-admin-que-deve",
        adminState().let { admin ->
            admin.copy(
                ownCharges = previewOwnCharges(),
                member = checkNotNull(admin.member).copy(lastCompletedGame = null),
            )
        },
    )

    @Test
    fun ownChargesBannerOnTime() = captureBanner("aviso-no-prazo", previewOwnCharges())

    @Test
    fun ownChargesBannerOverdue() = captureBanner("aviso-vencido", previewOwnChargesOverdue())

    private fun captureBanner(name: String, charges: HomeOwnChargesUi) {
        compose.setContent {
            SaqzTheme {
                HomeOwnChargesBanner(charges = charges, onClick = {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/vul-202/$name.png")
    }

    private fun captureOwnCharges(name: String, state: HomeState) {
        capture(name, state, "vul-202")
    }

    private fun capture(name: String, state: HomeState) {
        capture(name, state, "vul-191")
    }

    private fun captureAdmin(name: String, state: HomeState) {
        capture(name, state, "vul-192")
    }

    private fun capture(name: String, state: HomeState, directory: String) {
        compose.setContent {
            SaqzTheme {
                HomeScreen(state = state, onIntent = {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$directory/$name.png")
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
        confirmedCountTotal = 9,
    )

    private fun adminState(withPendingItems: Boolean = true): HomeState {
        val admin = HomeAdminGroupUi(
            id = "ceret",
            name = "Vôlei do CERET",
            entryRequestCount = if (withPendingItems) 3 else 0,
            monthlyCharges = if (withPendingItems) {
                HomeMonthlyChargesUi(count = 2, formattedTotal = "R$ 640,00", month = "JUL")
            } else {
                null
            },
            gameToSettle = if (withPendingItems) {
                HomeGameToSettleUi(
                    gameId = "game-1",
                    formattedDate = "28/07",
                    diaristCount = 4,
                    formattedTotal = "R$ 320,00",
                )
            } else {
                null
            },
        )
        val memberState = state().copy(
            member = checkNotNull(state().member).copy(
                nextGame = checkNotNull(state().member).nextGame?.copy(
                    declinedCount = 1,
                    pendingCount = 2,
                    adminHeroDeadlineLabel = "Encerra 28/07 · 18h",
                ),
            ),
        )
        return memberState.copy(
            member = checkNotNull(memberState.member).copy(
                groups = checkNotNull(memberState.member).groups.map { group ->
                    if (group.id == "ceret") group.copy(isAdmin = true) else group
                },
                subtitle = if (withPendingItems) {
                    "2 grupos · 3 coisas esperando você"
                } else {
                    "Terça tem jogo. Confirma?"
                },
                adminSubtitle = if (withPendingItems) "2 grupos · 3 coisas esperando você" else null,
                admin = HomeAdminReadModelUi(listOf(admin)),
            ),
        )
    }

    private fun mixedAdminState() = state().copy(
        member = checkNotNull(state().member).copy(
            subtitle = "2 grupos · 1 coisas esperando você",
            adminSubtitle = "2 grupos · 1 coisas esperando você",
            groups = checkNotNull(state().member).groups.map { group ->
                if (group.id == "pacaembu") group.copy(isAdmin = true) else group
            },
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
