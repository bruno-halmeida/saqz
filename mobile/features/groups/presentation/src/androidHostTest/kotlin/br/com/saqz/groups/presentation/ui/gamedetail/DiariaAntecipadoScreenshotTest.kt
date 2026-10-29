package br.com.saqz.groups.presentation.ui.gamedetail

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.presentation.GameFeeChoice
import br.com.saqz.groups.presentation.GameFeeProblem
import br.com.saqz.groups.presentation.details.GameFeeUndefinedUi
import br.com.saqz.groups.presentation.gamedetail.AdvancePaymentUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailAttendanceUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailResponseUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailWaitlistUi
import br.com.saqz.groups.presentation.gamedetail.GameGuestRowUi
import br.com.saqz.groups.presentation.gamedetail.WaitlistPaymentUi
import br.com.saqz.groups.presentation.gameeditor.GameEditorFieldError
import br.com.saqz.groups.presentation.gameeditor.GameEditorFields
import br.com.saqz.groups.presentation.gameeditor.GameEditorState
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.ui.components.GameFeeBlock
import br.com.saqz.groups.presentation.ui.details.GroupDetailsPreviewData
import br.com.saqz.groups.presentation.ui.details.GroupDetailsScreen
import br.com.saqz.groups.presentation.ui.gameeditor.GameEditorScreen
import br.com.saqz.groups.presentation.ui.setup.GroupSetupScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val Directory = "diaria-antecipado"

/**
 * DEB-146 e DEB-150: as telas das linhas 4 e 5 do canvas do financeiro (9SGq9SxGvzAbeHCaGEtM1F), para comparar com as
 * pranchas Valor-A, Aviso-grupo, Avulso-diaria, Antecipado-config, Antecipado-avulso, Antecipado-espera e
 * Antecipado-folha. As de `w360dp` e `fontScale` conferem a largura mínima e a fonte grande.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
@Suppress("TooManyFunctions")
class DiariaAntecipadoScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    // ---- Valor-A: o bloco nos três estados

    @Test
    @Config(qualifiers = "w360dp-h900dp")
    fun valorBlocoEstados() = capture("valor-a-estados") { FeeBlockStates() }

    @Test
    @Config(qualifiers = "w360dp-h1100dp", fontScale = 1.3f)
    fun valorBlocoEstadosFonteGrande() = capture("valor-a-estados-fonte-grande") { FeeBlockStates() }

    @Test
    @Config(qualifiers = "+h1600dp")
    fun editorNadaEscolhido() = capture("editor-nada-escolhido") {
        GameEditorScreen(
            state = editor.copy(validationErrors = setOf(GameEditorFieldError.GameFeeMissing), submitAttempts = 1),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    @Config(qualifiers = "+h1600dp")
    fun editorCobrarDiaria() = capture("editor-cobrar-diaria") {
        GameEditorScreen(
            state = editor.copy(
                monthlyFeeCents = 8000,
                form = editor.form.copy(gameFeeChoice = GameFeeChoice.Charge, gameFeeText = "20,00", gameFeeCents = 2000),
            ),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    @Config(qualifiers = "+h2400dp")
    fun criarGrupoNadaEscolhido() = capture("criar-grupo-nada-escolhido") {
        GroupSetupScreen(
            state = GroupSetupState(
                mode = GroupSetupMode.Create,
                form = GroupSetupState(mode = GroupSetupMode.Create).form.copy(
                    name = "Vôlei de quinta",
                    modality = GroupModality.COURT_VOLLEYBALL,
                    composition = GroupComposition.MIXED,
                ),
                errors = setOf(GroupSetupError.GameFeeMissing),
            ),
            onIntent = {},
            onBack = {},
        )
    }

    @Test
    @Config(qualifiers = "+h2400dp")
    fun editarGrupoAntecipado() = capture("antecipado-config") {
        val base = GroupSetupState(mode = GroupSetupMode.Edit("grp-1"), canManageGameConfig = true)
        GroupSetupScreen(
            state = base.copy(
                form = base.form.copy(
                    name = "Vôlei de quinta",
                    modality = GroupModality.COURT_VOLLEYBALL,
                    composition = GroupComposition.MIXED,
                    promotionMode = br.com.saqz.groups.model.PromotionMode.MANUAL,
                    advancePaymentRequired = true,
                    defaultGameFeeCents = 2000,
                    monthlyFeeCents = 8000,
                ),
                gameFeeChoice = GameFeeChoice.Charge,
            ),
            onIntent = {},
            onBack = {},
        )
    }

    @Test
    @Config(qualifiers = "+h2400dp")
    fun editarGrupoVoltouOrdemDeChegada() = capture("antecipado-config-desligado") {
        val base = GroupSetupState(mode = GroupSetupMode.Edit("grp-1"), canManageGameConfig = true)
        GroupSetupScreen(
            state = base.copy(
                form = base.form.copy(name = "Vôlei de quinta", modality = GroupModality.COURT_VOLLEYBALL),
                gameFeeChoice = GameFeeChoice.NoFee,
                advancePaymentTurnedOff = true,
            ),
            onIntent = {},
            onBack = {},
        )
    }

    // ---- Aviso-grupo

    @Test
    @Config(qualifiers = "+h1800dp")
    fun cartaoDoGrupo() = capture("aviso-grupo") {
        GroupDetailsScreen(
            state = GroupDetailsPreviewData.admin.copy(gameFeeUndefined = GameFeeUndefinedUi("R$ 10")),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h1800dp", fontScale = 1.3f)
    fun cartaoDoGrupoFonteGrande() = capture("aviso-grupo-360-fonte-grande") {
        GroupDetailsScreen(
            state = GroupDetailsPreviewData.admin.copy(gameFeeUndefined = GameFeeUndefinedUi("R$ 10")),
            onBack = {},
            onIntent = {},
        )
    }

    // ---- Avulso-diaria

    @Test
    @Config(qualifiers = "+h1400dp")
    fun linhaDaDiaria() = capture("avulso-diaria") {
        GameDetailScreen(state = GameDetailScenes.athleteMain.copy(feeNoteAmount = "R$ 20"), onBack = {}, onIntent = {})
    }

    // ---- Antecipado-avulso

    @Test
    @Config(qualifiers = "+h1200dp")
    fun avulsoPendente() = capture("antecipado-avulso") {
        GameDetailScreen(state = avulso(paid = false), onBack = {}, onIntent = {})
    }

    @Test
    @Config(qualifiers = "w360dp-h1400dp", fontScale = 1.3f)
    fun avulsoPendenteFonteGrande() = capture("antecipado-avulso-360-fonte-grande") {
        GameDetailScreen(state = avulso(paid = false), onBack = {}, onIntent = {})
    }

    @Test
    @Config(qualifiers = "+h1200dp")
    fun avulsoPago() = capture("antecipado-avulso-pago") {
        GameDetailScreen(state = avulso(paid = true), onBack = {}, onIntent = {})
    }

    // ---- Antecipado-espera e Antecipado-folha

    @Test
    @Config(qualifiers = "+h1400dp")
    fun esperaDoOrganizador() = capture("antecipado-espera", tab = GameDetailTags.TabWaiting) {
        GameDetailScreen(state = organizerWaitlist, onBack = {}, onIntent = {})
    }

    @Test
    @Config(qualifiers = "w360dp-h1600dp", fontScale = 1.3f)
    fun esperaDoOrganizadorFonteGrande() = capture("antecipado-espera-360-fonte-grande", tab = GameDetailTags.TabWaiting) {
        GameDetailScreen(state = organizerWaitlist, onBack = {}, onIntent = {})
    }

    @Test
    @Config(qualifiers = "+h1400dp")
    fun folhaDeColocar() = capture("antecipado-folha") {
        GameDetailScreen(state = organizerWaitlist.copy(advancePlaceRowId = "rafa"), onBack = {}, onIntent = {})
    }

    private fun capture(name: String, tab: String? = null, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) { content() }
            }
        }
        if (tab != null) compose.onNodeWithTag(tab).performClick()
        compose.onRoot().captureRoboImage("screenshots/$Directory/$name.png")
    }

    private val editor = GameEditorState(
        groupName = "Vôlei do CERET",
        zoneId = "America/Sao_Paulo",
        form = GameEditorFields(
            localDate = "2026-08-04",
            localTime = "19:30",
            durationMinutes = 120,
            venue = GameVenue(name = "CERET", address = "R. Canuto Abreu"),
            capacity = 12,
            confirmationLeadMinutes = 360,
        ),
    )

    private fun avulso(paid: Boolean): GameDetailState = GameDetailScenes.waitlisted.copy(
        response = GameDetailResponseUi(
            visible = true,
            status = AttendanceStatus.Waitlisted,
            waitlistKind = HomeWaitlistKind.Reserva,
            waitlistPosition = 2,
        ),
        advancePayment = AdvancePaymentUi("R$ 20", paid = paid, pixKey = "pix@volei.com"),
    )

    private val organizerWaitlist: GameDetailState = GameDetailScenes.organizer.copy(
        promotionMode = PromotionMode.MANUAL,
        attendance = GameDetailAttendanceUi(10, 12, 2, 3, 0),
        waitlist = listOf(
            GameDetailWaitlistUi(
                "caio", "Caio Lima", 1, AthletePosition.CENTRAL, false,
                payment = WaitlistPaymentUi("R$ 20", paid = true, chargeId = "c-1", version = 2),
            ),
            GameDetailWaitlistUi(
                "rafa", "Rafa Nunes", 2, AthletePosition.PONTA, false,
                payment = WaitlistPaymentUi("R$ 20", paid = false, chargeId = "c-2", version = 1),
            ),
            GameDetailWaitlistUi(
                "bia#1", "Júlia Rocha", 3, null, false,
                guest = GameGuestRowUi("bia", 1, "Bia Souza", isYours = false, canRemove = true),
                payment = WaitlistPaymentUi("R$ 20", paid = false, chargeId = "c-3", version = 1, hostName = "Bia"),
            ),
        ),
    )
}

@Composable
private fun FeeBlockStates() {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzCard {
            GameFeeBlock(null, "", {}, {}, problem = GameFeeProblem.Missing)
        }
        SaqzCard {
            GameFeeBlock(GameFeeChoice.Charge, "20,00", {}, {}, problem = null, monthlyFeeCents = 8000)
        }
        SaqzCard {
            GameFeeBlock(GameFeeChoice.NoFee, "", {}, {}, problem = null)
        }
    }
}
