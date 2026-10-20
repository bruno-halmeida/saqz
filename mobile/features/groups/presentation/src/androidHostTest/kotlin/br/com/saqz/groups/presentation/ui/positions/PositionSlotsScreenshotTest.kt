package br.com.saqz.groups.presentation.ui.positions

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameGuestUi
import br.com.saqz.groups.presentation.gamedetail.GamePositionPickerUi
import br.com.saqz.groups.presentation.gamedetail.GamePositionPurpose
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.ui.gamedetail.GameDetailPreviewData
import br.com.saqz.groups.presentation.ui.gamedetail.GameDetailScreen
import br.com.saqz.groups.presentation.ui.setup.GroupSetupScreen
import br.com.saqz.groups.presentation.ui.setup.GroupSetupTags
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DEB-102: vaga por posição no celular comum. Cenas: configuração (fecha, faltam, sobram), lista agrupada,
 * seletor do "Vou" (com e sem escolha), convidado e entrada forçada. Os mesmos estados em tela estreita estão em
 * [PositionSlotsSmallPhoneScreenshotTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi", application = Application::class)
class PositionSlotsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun configFecha() = capture("config-fecha") { setup(SETUP_OK) }
    @Test fun configFaltam() = capture("config-faltam") { setup(SETUP_FALTAM) }
    @Test fun configSobram() = capture("config-sobram") { setup(SETUP_SOBRAM) }
    @Test fun listaAgrupada() = capture("lista-agrupada") { game(GameDetailPreviewData.positionsGame) }
    @Test fun seletorSugerida() = capture("seletor-sugerida") {
        game(GameDetailPreviewData.positionsGame.copy(positionPicker = PICKER_SUGERIDA))
    }
    @Test fun seletorSemEscolha() = capture("seletor-sem-escolha") {
        game(GameDetailPreviewData.positionsGame.copy(positionPicker = PICKER_VAZIO))
    }
    @Test fun convidadoComPosicao() = capture("convidado-com-posicao") {
        game(GameDetailPreviewData.positionsGame.copy(guest = GUEST_SHEET))
    }
    @Test fun forcarEntrada() = capture("forcar-entrada") {
        game(
            GameDetailPreviewData.positionsGame.copy(
                positionPicker = FORCE,
            ),
        )
    }
    @Test fun forcarEntradaComErro() = capture("forcar-entrada-erro") {
        game(
            GameDetailPreviewData.positionsGame.copy(
                positionPicker = FORCE_ERRO,
            ),
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { SaqzTheme { content() } }
        compose.onRoot().captureRoboImage("screenshots/deb-102/$name.png")
    }
}

/** Criação do grupo 5-1 (DEB-102): card aberto nas opções avançadas, faltando e fechando, em tela comum. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi", application = Application::class)
class PositionSlotsCreationScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun criacaoFaltam() = capture("criacao-faltam", CREATE_FALTAM)
    @Test fun criacaoFecha() = capture("criacao-fecha", CREATE_OK)

    private fun capture(name: String, state: GroupSetupState) {
        compose.setContent { SaqzTheme { setup(state) } }
        compose.onNodeWithTag(GroupSetupTags.Advanced).performScrollTo().performClick()
        compose.onRoot().captureRoboImage("screenshots/deb-102/$name.png")
    }
}

/** Mesmas cenas no celular pequeno (320 dp) com fonte grande, onde as frases quebram. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h2400dp-xhdpi", fontScale = 1.3f, application = Application::class)
class PositionSlotsSmallPhoneScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun configFaltam() = capture("pequeno-config-faltam") { setup(SETUP_FALTAM) }
    @Test fun criacaoFaltam() {
        compose.setContent { SaqzTheme { setup(CREATE_FALTAM) } }
        compose.onNodeWithTag(GroupSetupTags.Advanced).performScrollTo().performClick()
        compose.onRoot().captureRoboImage("screenshots/deb-102/pequeno-criacao-faltam.png")
    }
    @Test fun listaAgrupada() = capture("pequeno-lista-agrupada") { game(GameDetailPreviewData.positionsGame) }
    @Test fun seletor() = capture("pequeno-seletor") {
        game(GameDetailPreviewData.positionsGame.copy(positionPicker = PICKER_SUGERIDA))
    }
    @Test fun forcarEntrada() = capture("pequeno-forcar-entrada") {
        game(
            GameDetailPreviewData.positionsGame.copy(
                positionPicker = FORCE,
            ),
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { SaqzTheme { content() } }
        compose.onRoot().captureRoboImage("screenshots/deb-102/$name.png")
    }
}

private val SETUP_OK = GroupSetupState(
    mode = GroupSetupMode.Edit("group-1"),
    canManageGameConfig = true,
    positionSlotsReady = true,
    form = GroupSetupForm(
        name = "Vôlei do CERET", modality = GroupModality.COURT_VOLLEYBALL, composition = GroupComposition.MIXED,
        playStyle = GroupPlayStyle.FIVE_ONE, defaultCapacity = 18,
    ),
    positionSlots = mapOf(
        AthletePosition.LEVANTADOR to 3, AthletePosition.OPOSTO to 3, AthletePosition.PONTA to 6, AthletePosition.CENTRAL to 6,
    ),
)

private val CREATE_OK = SETUP_OK.copy(mode = GroupSetupMode.Create, canManageGameConfig = false)
private val CREATE_FALTAM =
    CREATE_OK.copy(positionSlots = CREATE_OK.positionSlots + (AthletePosition.PONTA to 4))
private val SETUP_FALTAM = SETUP_OK.copy(positionSlots = SETUP_OK.positionSlots + (AthletePosition.PONTA to 4))
private val SETUP_SOBRAM = SETUP_OK.copy(positionSlots = SETUP_OK.positionSlots + (AthletePosition.LIBERO to 1))
private val PICKER_SUGERIDA = GamePositionPickerUi(GamePositionPurpose.Self, AthletePosition.CENTRAL, AthletePosition.CENTRAL)
private val PICKER_VAZIO = GamePositionPickerUi(GamePositionPurpose.Self, null, null)
private val GUEST_SHEET = GameGuestUi(visible = true, enabled = true, sheetOpen = true, name = "Rafa", position = AthletePosition.CENTRAL)
private val FORCE_PURPOSE = GamePositionPurpose.Force("wait-2", "Duda Alves", AthletePosition.PONTA)
private val FORCE = GamePositionPickerUi(FORCE_PURPOSE, null, AthletePosition.PONTA)
private val FORCE_ERRO = FORCE.copy(failed = true)

@Composable
private fun setup(state: GroupSetupState) = GroupSetupScreen(state, {}, {}, modifier = Modifier.fillMaxSize())

@Composable
private fun game(state: GameDetailState) = GameDetailScreen(state, {}, {}, modifier = Modifier.fillMaxSize())
