package br.com.saqz.groups.presentation.positionpicker

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.attendance.share.AttendanceLinkDestination
import br.com.saqz.groups.presentation.attendancelink.AttendanceLinkGame
import br.com.saqz.groups.presentation.attendancelink.AttendanceLinkPhase
import br.com.saqz.groups.presentation.attendancelink.AttendanceLinkPosition
import br.com.saqz.groups.presentation.attendancelink.AttendanceLinkScreen
import br.com.saqz.groups.presentation.attendancelink.AttendanceLinkState
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.positionpicker.ui.PositionPickerPreviewData
import br.com.saqz.groups.presentation.positionpicker.ui.PositionPickerScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val Main = PositionPickerPreviewData.main

/** DEB-107: todos os estados da tela de escolher posição, no tamanho dos quadros (390 × 844). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class PositionPickerScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun sugerida() = capture("sugerida", Main)
    @Test fun cheia() = capture("cheia", Main.copy(selected = AthletePosition.PONTA))
    @Test fun semSugestao() = capture(
        "sem-sugestao",
        Main.copy(selected = null, options = PositionPickerPreviewData.options(suggested = false)),
    )
    @Test fun enviando() = capture("enviando", Main.copy(sending = true))
    @Test fun lotou() = capture(
        "lotou",
        Main.copy(options = PositionPickerPreviewData.options(centralFull = true), raced = AthletePosition.CENTRAL),
    )
    @Test fun todasCheias() = capture("todas-cheias", Main.copy(options = PositionPickerPreviewData.options(allFull = true)))
    @Test fun erroAoEnviar() = capture("erro-ao-enviar", Main.copy(submitFailed = true))
    @Test fun carregando() = capture("carregando", PositionPickerState())
    @Test fun falhaAoCarregar() = capture("falha-ao-carregar", PositionPickerState(loading = false, loadError = GroupUiError.Network))

    private fun capture(name: String, state: PositionPickerState) {
        compose.mainClock.autoAdvance = false
        compose.setContent { SaqzTheme { PositionPickerScreen(state, onBack = {}, onIntent = {}) } }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("screenshots/deb-107/posicao-$name.png")
    }
}

/** Quadro `Pequeno`: 320 × 568 com a posição cheia escolhida (aviso aberto); a lista rola e a barra não a cobre. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h568dp-xhdpi", application = Application::class)
class PositionPickerSmallPhoneScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun pequenoCheia() {
        compose.setContent { SaqzTheme { PositionPickerScreen(Main.copy(selected = AthletePosition.PONTA), onBack = {}, onIntent = {}) } }
        compose.onRoot().captureRoboImage("screenshots/deb-107/posicao-pequeno-cheia.png")
    }
}

/** Quadro `FonteGrande`: fonte a 130%, os selos descem para baixo do nome e nada corta. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi", fontScale = 1.3f, application = Application::class)
class PositionPickerLargeFontScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun fonteGrande() {
        compose.setContent { SaqzTheme { PositionPickerScreen(Main.copy(selected = AthletePosition.PONTA), onBack = {}, onIntent = {}) } }
        compose.onRoot().captureRoboImage("screenshots/deb-107/posicao-fonte-130.png")
    }
}

/** Quadro `Confirmado` (DEB-107/110): o resultado do link com a posição, confirmado em verde e na espera da posição. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class AttendanceLinkPositionScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val destination = AttendanceLinkDestination(GroupId("group"), "game")
    private val game = AttendanceLinkGame("TUESDAY", "28/07", "19h30", "CERET", "Quadra 2", capacity = 12, confirmedCount = 12)

    @Test fun confirmado() = capture(
        "link-confirmado",
        AttendanceLinkState(
            AttendanceLinkPhase.Confirmed, destination, game,
            position = AttendanceLinkPosition(AthletePosition.CENTRAL, 4, 4),
        ),
    )

    @Test fun espera() = capture(
        "link-espera",
        AttendanceLinkState(
            AttendanceLinkPhase.Waitlisted, destination, game, waitlistPosition = 2,
            position = AttendanceLinkPosition(AthletePosition.PONTA, 4, 4),
        ),
    )

    @Test fun escolhendo() = capture("link-escolhendo", AttendanceLinkState(AttendanceLinkPhase.ChoosingPosition, destination, game))

    private fun capture(name: String, state: AttendanceLinkState) {
        compose.setContent { SaqzTheme { AttendanceLinkScreen(state, {}, {}, {}) } }
        compose.onRoot().captureRoboImage("screenshots/deb-107/$name.png")
    }
}
