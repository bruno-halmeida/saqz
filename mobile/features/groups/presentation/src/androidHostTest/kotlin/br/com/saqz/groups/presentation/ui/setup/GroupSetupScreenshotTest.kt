package br.com.saqz.groups.presentation.ui.setup

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.PromotionMode
import br.com.saqz.groups.presentation.setup.GroupSetupDefaults
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupSheet
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.GroupSetupStep
import br.com.saqz.groups.presentation.setup.validate
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
class GroupSetupScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun createForm() = capture("2a-criar-formulario") {
        GroupSetupScreen(state = createState(), onIntent = {}, onBack = {})
    }

    @Test
    fun beachScenario() = capture("2b-areia-avulso") {
        GroupSetupScreen(
            state = GroupSetupState(
                mode = GroupSetupMode.Create,
                form = GroupSetupForm(
                    name = "Beach da Vila",
                    modality = GroupModality.BEACH_VOLLEYBALL,
                    composition = GroupComposition.WOMEN,
                    level = GroupLevel.ADVANCED,
                    defaultCapacity = 4,
                    defaultConfirmationLeadMinutes = 360,
                ),
                photoUrl = PhotoUrl,
                recurring = false,
            ),
            onIntent = {},
            onBack = {},
        )
    }

    @Test
    fun slotPickerSheet() = capture("2c-picker-dia-horario") {
        GroupSetupScreen(
            state = createState(sheet = GroupSetupSheet.Slot(index = null)),
            onIntent = {},
            onBack = {},
        )
    }

    @Test
    fun review() = capture("2d-revisao") {
        GroupReviewScreen(
            state = createState(step = GroupSetupStep.Review),
            onIntent = {},
        )
    }

    @Test
    fun missingFields() = capture("2g-campos-obrigatorios") {
        GroupSetupScreen(state = PreviewErrorState, onIntent = {}, onBack = {})
    }

    /** Modalidade e público em falta: os dois campos que o gateway exige. */
    @Test
    fun missingGatewayRequiredFields() = capture("2g-modalidade-e-publico") {
        val state = GroupSetupState(
            mode = GroupSetupMode.Create,
            form = PreviewCourtForm.copy(modality = null, composition = null),
        )
        GroupSetupScreen(state = state.copy(errors = validate(state)), onIntent = {}, onBack = {})
    }

    @Test
    @Config(qualifiers = "+h1400dp")
    fun editForm() = capture("2i-editar-grupo") {
        GroupSetupScreen(
            state = createState(
                mode = GroupSetupMode.Edit(groupId = "grp-1"),
                photoUrl = PhotoUrl,
                form = PreviewCourtForm.copy(
                    regularSlots = PreviewCourtForm.regularSlots.map { it.copy(durationMinutes = 90) },
                ),
                durationMinutes = 90,
            ),
            onIntent = {},
            onBack = {},
        )
    }

    @Test
    fun gameConfigOverrides() = capture("config-jogo-alterado", directory = "vul-157") {
        GroupGameConfigSection(
            mensalistaPriority = false,
            promotionMode = PromotionMode.MANUAL,
            confirmationLeadMinutes = 1_440,
            autoConfirmEnabled = true,
            onMensalistaPriorityChange = {},
            onPromotionModeSelect = {},
            onConfirmationLeadSelect = {},
            onAutoConfirmChange = {},
        )
    }

    @Test
    fun pixFields() = capture("pix-do-grupo", directory = "vul-182") {
        GroupPixSection(
            pixKey = "racha@saqz.test",
            pixLabel = "Lucas Prado · Nubank",
            onPixKeyChange = {},
            onPixLabelChange = {},
        )
    }

    @Test
    fun gameConfigDefaults() = capture("config-jogo-padrao", directory = "vul-157") {
        GroupGameConfigSection(
            mensalistaPriority = true,
            promotionMode = PromotionMode.FIFO,
            confirmationLeadMinutes = GroupSetupDefaults.ConfirmationLeadMinutes,
            autoConfirmEnabled = false,
            onMensalistaPriorityChange = {},
            onPromotionModeSelect = {},
            onConfirmationLeadSelect = {},
            onAutoConfirmChange = {},
        )
    }

    @Test
    fun deleteSheet() = capture("2j-excluir-grupo") {
        GroupSetupScreen(
            state = createState(
                mode = GroupSetupMode.Edit(groupId = "grp-1"),
                photoUrl = PhotoUrl,
                sheet = GroupSetupSheet.ConfirmDelete,
            ),
            onIntent = {},
            onBack = {},
        )
    }

    private fun createState(
        mode: GroupSetupMode = GroupSetupMode.Create,
        step: GroupSetupStep = GroupSetupStep.Form,
        photoUrl: String? = null,
        sheet: GroupSetupSheet? = null,
        form: GroupSetupForm = PreviewCourtForm,
        durationMinutes: Int = GroupSetupDefaults.DurationMinutes,
    ) = GroupSetupState(
        mode = mode,
        step = step,
        form = form,
        photoUrl = photoUrl,
        memberCount = 26,
        canDelete = mode is GroupSetupMode.Edit,
        canManageGameConfig = mode is GroupSetupMode.Edit,
        durationMinutes = durationMinutes,
        sheet = sheet,
        pixKey = if (mode is GroupSetupMode.Edit) "racha@saqz.test" else null,
        pixLabel = if (mode is GroupSetupMode.Edit) "Lucas Prado · Nubank" else null,
    )

    private fun capture(name: String, directory: String = "vul-68", content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/$directory/$name.png")
    }

    private companion object {
        const val PhotoUrl = "https://saqz.example/ceret.png"
    }
}
