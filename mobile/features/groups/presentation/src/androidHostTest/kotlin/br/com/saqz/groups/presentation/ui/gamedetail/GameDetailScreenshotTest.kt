package br.com.saqz.groups.presentation.ui.gamedetail

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.gamedetail.GameDetailAttendanceUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailConfirmedUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailResponseUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
import br.com.saqz.groups.presentation.gamedetail.GameDetailWaitlistUi
import br.com.saqz.groups.presentation.gamedetail.GameGuestRowUi
import br.com.saqz.groups.presentation.gamedetail.GameGuestUi
import br.com.saqz.groups.presentation.gamedetail.GamePositionUi
import br.com.saqz.groups.presentation.gamedetail.GameReminderUi
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A tela do jogo em tela cheia (DEB-116): uma cena por tela da fileira escolhida do canvas
 * (`design/tela-de-jogo/previa/Cheia-*.html`), as folhas novas e os estados que continuam (carga, erro, cancelado,
 * resposta).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
@Suppress("TooManyFunctions")
class GameDetailScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    // ---- a fileira escolhida (Cheia-*)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun main() = capture("cheia-main", GameDetailScenes.athleteMain)

    @Test
    @Config(qualifiers = "+h1600dp")
    fun cincoUmAtleta() = capture("cheia-cinco-um-atleta", GameDetailScenes.fiveOneAthlete)

    @Test
    @Config(qualifiers = "+h1000dp")
    fun confirmadoNaoVao() = capture("cheia-confirmado-nao-vao", GameDetailScenes.confirmed, tab = GameDetailTags.TabDeclined)

    @Test
    @Config(qualifiers = "+h1000dp")
    fun naEspera() = capture("cheia-na-espera", GameDetailScenes.waitlisted)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun organizador() = capture("cheia-organizador", GameDetailScenes.organizer)

    @Test
    @Config(qualifiers = "+h1100dp")
    fun organizadorEspera() = capture("cheia-organizador-espera", GameDetailScenes.organizer, tab = GameDetailTags.TabWaiting)

    @Test
    fun menuDoJogo() = capture("cheia-menu-do-jogo", GameDetailScenes.organizer.copy(gameMenuOpen = true))

    @Test
    fun rascunho() = capture("cheia-rascunho", GameDetailScenes.draft)

    @Test
    @Config(qualifiers = "+h1000dp")
    fun encerrado() = capture("cheia-encerrado", GameDetailScenes.completed)

    // ---- folhas novas e variações

    @Test
    fun acoesDaPessoa() = capture("acoes-da-pessoa", GameDetailScenes.organizer.copy(personActionsId = "5"))

    @Test
    fun acoesDoConvidado() = capture("acoes-do-convidado", GameDetailScenes.organizerGuest.copy(personActionsId = "2#1"))

    @Test
    fun menuSemVagas() = capture("menu-sem-ajustar-vagas", GameDetailScenes.organizer.copy(
        gameMenuOpen = true,
        header = GameDetailScenes.organizer.header?.copy(statusTone = GameDetailStatusTone.Completed),
    ))

    @Test
    @Config(qualifiers = "+h1400dp")
    fun avisoEnviado() = capture(
        "organizador-aviso-enviado",
        GameDetailScenes.organizer.copy(reminder = GameReminderUi(notifiedCount = 4)),
    )

    @Test
    @Config(qualifiers = "+h1400dp")
    fun avisoFalhou() = capture("organizador-aviso-falhou", GameDetailScenes.organizer.copy(reminder = GameReminderUi(failed = true)))

    @Test
    @Config(qualifiers = "+h1400dp")
    fun avisoRecusado() = capture(
        "organizador-aviso-recusado",
        GameDetailScenes.organizer.copy(reminder = GameReminderUi(refused = true)),
    )

    @Test
    @Config(qualifiers = "+h1400dp")
    fun mapaFalhou() = capture("mapa-falhou", GameDetailScenes.athleteMain.copy(mapFailed = true))

    @Test
    @Config(qualifiers = "+h1400dp")
    fun semEndereco() = capture(
        "sem-endereco",
        GameDetailScenes.athleteMain.copy(hero = GameDetailPreviewData.hero.copy(address = "")),
    )

    @Test
    @Config(qualifiers = "+h1400dp")
    fun organizadorSemPosicoes() = capture("organizador-sem-posicoes", GameDetailScenes.organizerFlat)

    @Test
    @Config(qualifiers = "+h1000dp")
    fun naoVaoServidorAntigo() = capture(
        "nao-vao-servidor-antigo",
        GameDetailScenes.confirmed.copy(declinedRoster = null),
        tab = GameDetailTags.TabDeclined,
    )

    @Test
    @Config(qualifiers = "+h1000dp")
    fun naoVaoVazia() = capture(
        "nao-vao-vazia",
        GameDetailScenes.confirmed.copy(declinedRoster = emptyList()),
        tab = GameDetailTags.TabDeclined,
    )

    @Test
    @Config(qualifiers = "+h1000dp")
    fun esperaVazia() = capture("espera-vazia", GameDetailScenes.confirmed.copy(waitlist = emptyList()), tab = GameDetailTags.TabWaiting)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun muitasVagas() = capture(
        "muitas-vagas",
        GameDetailScenes.athleteMain.copy(
            attendance = GameDetailAttendanceUi(4, 12, 8, 1, 2),
            confirmedRoster = GameDetailPreviewData.confirmed.take(4),
        ),
    )

    @Test
    fun capacitySheet() = capture(
        "capacidade",
        GameDetailScenes.organizerFlat.copy(capacitySheetOpen = true, capacityDraft = 14),
    )

    @Test
    fun loading() = capture("carregando", GameDetailState())

    @Test
    fun failed() = capture(
        "falhou",
        GameDetailState(isLoading = false, loadFailed = true, error = GroupUiError.AccessDenied),
    )

    @Test
    @Config(qualifiers = "+h1000dp")
    fun cancelled() = capture(
        "cancelado",
        GameDetailScenes.organizerFlat.copy(
            header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Cancelled, financeReviewRequired = true),
        ),
    )

    @Test
    fun cancelling() = capture("cancelando", GameDetailScenes.organizerFlat.copy(cancelDialogOpen = true, cancelling = true))

    @Test
    fun draftPublishFailed() = capture("rascunho-falhou", GameDetailScenes.draft.copy(publishFailed = true))

    // A resposta de presença: um estado por cena, o convidado junto (ele depende da resposta).
    @Test
    @Config(qualifiers = "+h1400dp")
    fun responseDeclined() = capture("resposta-nao-vou", GameDetailResponsePreviewData.memberDeclined)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun responseWaitlistAvulso() = capture("resposta-espera-avulso", GameDetailResponsePreviewData.memberAvulso)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun responseFailed() = capture("resposta-falhou", GameDetailResponsePreviewData.memberFailed)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun responseStarted() = capture("resposta-em-andamento", GameDetailResponsePreviewData.memberStartedConfirmed)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun responseClosedByDeadline() = capture("resposta-prazo-encerrado", GameDetailResponsePreviewData.memberClosedByDeadline)

    private fun capture(name: String, state: GameDetailState, tab: String? = null) {
        compose.setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    GameDetailScreen(state = state, onBack = {}, onIntent = {})
                }
            }
        }
        if (tab != null) compose.onNodeWithTag(tab).performClick()
        compose.onRoot().captureRoboImage("screenshots/deb-116/$name.png")
    }
}

/** As telas do canvas com os dados dele: "Vôlei do CERET", terça 28/07, 19h30. */
internal object GameDetailScenes {
    private val base = GameDetailPreviewData.athlete
    private val confirmedResponse = GameDetailResponseUi(visible = true, status = AttendanceStatus.Confirmed)

    val athleteMain = base

    val fiveOneAthlete = base.copy(
        attendance = GameDetailAttendanceUi(10, 12, 2, 2, 3),
        header = GameDetailPreviewData.header.copy(availableSpots = 2),
        positions = listOf(
            GamePositionUi(AthletePosition.PONTA, 4, 4, 0, 1),
            GamePositionUi(AthletePosition.CENTRAL, 4, 3, 1, 0),
            GamePositionUi(AthletePosition.OPOSTO, 2, 1, 1, 0),
            GamePositionUi(AthletePosition.LEVANTADOR, 2, 2, 0, 0),
        ),
        confirmedRoster = listOf(
            GameDetailConfirmedUi("2", "Bia Souza", false, AthletePosition.PONTA),
            GameDetailConfirmedUi("5", "Felipe Nunes", false, AthletePosition.PONTA),
            GameDetailConfirmedUi("9", "Marcos Lima", false, AthletePosition.PONTA),
            GameDetailConfirmedUi(
                "2#1",
                "Júlia Rocha",
                false,
                AthletePosition.PONTA,
                guest = GameGuestRowUi("2", 1, "Bia Souza", isYours = false, canRemove = false),
            ),
            GameDetailConfirmedUi("3", "Thiago Melo", false, AthletePosition.CENTRAL),
            GameDetailConfirmedUi("7", "Igor Santos", false, AthletePosition.CENTRAL),
            GameDetailConfirmedUi("10", "Paulo Reis", false, AthletePosition.CENTRAL),
            GameDetailConfirmedUi("4", "Carla Mendes", false, AthletePosition.OPOSTO),
            GameDetailConfirmedUi("1", "Lucas Prado", false, AthletePosition.LEVANTADOR),
            GameDetailConfirmedUi("6", "Gabi Torres", false, AthletePosition.LEVANTADOR),
        ),
        waitlist = listOf(GameDetailWaitlistUi("wait-2", "Duda Alves", 1, AthletePosition.PONTA, false)),
        declinedRoster = GameDetailPreviewData.declined.take(2),
    )

    /** Confirmado (10/12), olhando a aba Não vão. */
    val confirmed = base.copy(
        attendance = GameDetailAttendanceUi(10, 12, 2, 3, 2),
        header = GameDetailPreviewData.header.copy(availableSpots = 2),
        confirmedRoster = base.confirmedRoster + GameDetailConfirmedUi("me", "Ana Ribeiro", true, AthletePosition.OPOSTO),
        response = confirmedResponse,
        guest = GameGuestUi(visible = true, enabled = true, feeLabel = "R$ 25,00"),
    )

    /** Jogo lotado, quem olha é o 2º da espera: a aba abre em Na espera. */
    val waitlisted = base.copy(
        attendance = GameDetailAttendanceUi(12, 12, 0, 1, 0),
        header = GameDetailPreviewData.header.copy(availableSpots = 0),
        waitlist = listOf(
            GameDetailWaitlistUi("wait-1", "Caio Lima", 1, AthletePosition.CENTRAL, false),
            GameDetailWaitlistUi("me", "Duda Alves", 2, AthletePosition.PONTA, false, isYou = true),
        ),
        response = GameDetailResponseUi(
            visible = true,
            status = AttendanceStatus.Waitlisted,
            waitlistKind = HomeWaitlistKind.Reserva,
            waitlistPosition = 2,
        ),
        guest = GameGuestUi(visible = true, enabled = true),
    )

    /** Organizador no 5-1, escolha manual, 4 sem resposta e uma central faltando. */
    val organizer = GameDetailPreviewData.positionsGame.copy(
        guest = GameGuestUi(visible = true, enabled = true, feeLabel = "R$ 25,00"),
        declinedRoster = GameDetailPreviewData.declined.take(2),
    )

    val organizerGuest = organizer.copy(
        confirmedRoster = organizer.confirmedRoster + GameDetailConfirmedUi(
            "2#1",
            "Júlia Rocha",
            false,
            null,
            guest = GameGuestRowUi("2", 1, "Bia Souza", isYours = false, canRemove = true),
        ),
    )

    /** Organizador em jogo sem posições, ordem de chegada. */
    val organizerFlat = GameDetailPreviewData.admin.copy(
        response = confirmedResponse,
        confirmedRoster = GameDetailPreviewData.admin.confirmedRoster.mapIndexed { index, person ->
            if (index == 0) person.copy(isYou = true, canRemove = false) else person
        },
    )

    val draft = GameDetailPreviewData.admin.copy(
        header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Draft, confirmationOpen = false),
        hero = GameDetailPreviewData.hero.copy(date = "4 de agosto"),
        response = GameDetailResponseUi(),
        confirmedRoster = emptyList(),
        waitlist = emptyList(),
    )

    val completed = GameDetailPreviewData.admin.copy(
        header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Completed, confirmationOpen = false),
        hero = GameDetailPreviewData.hero.copy(date = "21 de julho", address = ""),
        attendance = GameDetailAttendanceUi(11, 12, 1, 3, 0),
        response = GameDetailResponseUi(),
        confirmedRoster = GameDetailPreviewData.confirmed.take(3).mapIndexed { index, person ->
            person.copy(isYou = index == 0, canRemove = false)
        },
        waitlist = emptyList(),
    )
}
