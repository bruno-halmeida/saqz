package br.com.saqz.groups.presentation.ui.setup

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
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.pix.PixKeyType
import br.com.saqz.groups.presentation.setup.GroupSlotDraft
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.PromotionMode
import br.com.saqz.groups.presentation.setup.GroupSetupDefaults
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupSheet
import br.com.saqz.groups.presentation.setup.GroupSetupState
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

    // ---- Criar ----

    @Test
    @Config(qualifiers = TALL)
    fun createEmpty() = capture("criar-vazio") {
        GroupSetupScreen(state = GroupSetupState(mode = GroupSetupMode.Create), onIntent = {}, onBack = {}, showTrialOffer = true)
    }

    @Test
    @Config(qualifiers = TALL)
    fun createWithErrors() = capture("criar-com-erro") {
        val state = GroupSetupState(mode = GroupSetupMode.Create)
        GroupSetupScreen(state = state.copy(errors = validate(state)), onIntent = {}, onBack = {}, showTrialOffer = true)
    }

    @Test
    @Config(qualifiers = TALL)
    fun createRecurringFilled() = capture("criar-sim-toda-semana") {
        GroupSetupScreen(
            state = createState(sheet = null).copy(recurring = true),
            onIntent = {},
            onBack = {},
            showTrialOffer = true,
        )
    }

    @Test
    @Config(qualifiers = TALL)
    fun createDetailsOpen() = capture("criar-mais-detalhes-aberto", open = true) {
        GroupSetupScreen(state = createState(), onIntent = {}, onBack = {}, showTrialOffer = true)
    }

    @Test
    @Config(qualifiers = TALL)
    fun createFiveOneWrongSum() = capture("criar-5-1-soma-errada", open = true) {
        GroupSetupScreen(state = fiveOne(wrong = true), onIntent = {}, onBack = {}, showTrialOffer = true)
    }

    @Test
    @Config(qualifiers = TALL)
    fun createFiveOneRightSum() = capture("criar-5-1-soma-certa", open = true) {
        GroupSetupScreen(state = fiveOne(wrong = false), onIntent = {}, onBack = {}, showTrialOffer = true)
    }

    @Test
    @Config(qualifiers = TALL)
    fun createFiveOneRefused() = capture("criar-5-1-recusado") {
        val wrong = fiveOne(wrong = true)
        GroupSetupScreen(
            state = wrong.copy(errors = validate(wrong)),
            onIntent = {},
            onBack = {},
            showTrialOffer = true,
        )
    }

    // ---- Folha de dias e horário ----

    @Test
    fun slotSheetTwoDays() = capture("folha-dois-dias") {
        GroupSetupScreen(
            state = createState(sheet = GroupSetupSheet.Slot(index = null))
                .copy(recurring = true, slotDraft = GroupSlotDraft(setOf(GroupWeekday.TUESDAY, GroupWeekday.THURSDAY), 19, 30)),
            onIntent = {},
            onBack = {},
        )
    }

    @Test
    fun slotSheetEditingOneDay() = capture("folha-editar-um-dia") {
        GroupSetupScreen(
            state = createState(sheet = GroupSetupSheet.Slot(index = 0)).copy(recurring = true),
            onIntent = {},
            onBack = {},
        )
    }

    // ---- Editar ----

    @Test
    @Config(qualifiers = HUGE)
    fun editWithGameConfig() = capture("editar-com-configuracao-de-jogo") {
        GroupSetupScreen(
            state = createState(mode = GroupSetupMode.Edit(groupId = "grp-1"), photoUrl = PhotoUrl),
            onIntent = {},
            onBack = {},
            onOpenWhatsApp = {},
        )
    }

    @Test
    @Config(qualifiers = HUGE)
    fun editWithoutGameConfig() = capture("editar-sem-configuracao-de-jogo") {
        GroupSetupScreen(
            state = createState(mode = GroupSetupMode.Edit(groupId = "grp-1"), photoUrl = PhotoUrl)
                .copy(canManageGameConfig = false, canDelete = false),
            onIntent = {},
            onBack = {},
        )
    }

    @Test
    fun deleteSheet() = capture("excluir-grupo") {
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

    // ---- Seções soltas ----

    @Test
    fun gameConfigPriorityOnAndOff() {
        capture("config-jogo-prioridade-ligada") {
            GroupGameConfigSection(
                mensalistaPriority = true,
                promotionMode = PromotionMode.FIFO,
                confirmationLeadMinutes = GroupSetupDefaults.ConfirmationLeadMinutes,
                onMensalistaPriorityChange = {},
                onPromotionModeSelect = {},
                onConfirmationLeadSelect = {},
            )
        }
    }

    @Test
    fun gameConfigManualWithoutPriority() = capture("config-jogo-aviso-manual") {
        GroupGameConfigSection(
            mensalistaPriority = false,
            promotionMode = PromotionMode.MANUAL,
            confirmationLeadMinutes = 1_440,
            onMensalistaPriorityChange = {},
            onPromotionModeSelect = {},
            onConfirmationLeadSelect = {},
        )
    }

    @Test
    fun gameConfigFifoWithoutPriorityHidesTheLead() = capture("config-jogo-ordem-de-chegada-sem-prioridade") {
        GroupGameConfigSection(
            mensalistaPriority = false,
            promotionMode = PromotionMode.FIFO,
            confirmationLeadMinutes = 360,
            onMensalistaPriorityChange = {},
            onPromotionModeSelect = {},
            onConfirmationLeadSelect = {},
        )
    }

    @Test
    fun pixCpf() = capture("pix-cpf") {
        GroupPixSection(
            pixKey = "52998224725",
            pixType = PixKeyType.CPF,
            onPixTypeSelect = {},
            onPixKeyChange = {},
        )
    }

    @Test
    fun pixLegacyWithoutType() = capture("pix-chave-antiga-sem-tipo") {
        GroupPixSection(pixKey = "chave antiga", pixType = null, onPixTypeSelect = {}, onPixKeyChange = {})
    }

    @Test
    fun pixPhoneInvalid() = capture("pix-telefone-invalido") {
        GroupPixSection(
            pixKey = "1198",
            pixType = PixKeyType.PHONE,
            onPixTypeSelect = {},
            onPixKeyChange = {},
            pixKeyError = "Informe o celular com DDD.",
        )
    }

    private fun fiveOne(wrong: Boolean) = createState().copy(
        form = PreviewCourtForm.copy(playStyle = GroupPlayStyle.FIVE_ONE, defaultCapacity = 14),
        positionSlotsReady = true,
        positionSlots = mapOf(
            AthletePosition.LEVANTADOR to 2,
            AthletePosition.PONTA to 4,
            AthletePosition.CENTRAL to 2,
            AthletePosition.OPOSTO to 2,
            AthletePosition.LIBERO to if (wrong) 2 else 4,
        ),
    )

    private fun createState(
        mode: GroupSetupMode = GroupSetupMode.Create,
        photoUrl: String? = null,
        sheet: GroupSetupSheet? = null,
        form: GroupSetupForm = PreviewCourtForm,
        durationMinutes: Int = GroupSetupDefaults.DurationMinutes,
    ) = GroupSetupState(
        mode = mode,
        form = form,
        photoUrl = photoUrl,
        memberCount = 26,
        canDelete = mode is GroupSetupMode.Edit,
        canManageGameConfig = mode is GroupSetupMode.Edit,
        durationMinutes = durationMinutes,
        sheet = sheet,
        pixKey = if (mode is GroupSetupMode.Edit) "racha@saqz.test" else null,
        pixType = if (mode is GroupSetupMode.Edit) PixKeyType.EMAIL else null,
    )

    private fun capture(name: String, directory: String = "criar-grupo", open: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    content()
                }
            }
        }
        if (open) compose.onNodeWithTag(GroupSetupTags.Advanced).performClick()
        compose.onRoot().captureRoboImage("screenshots/$directory/$name.png")
    }

    private companion object {
        const val PhotoUrl = "https://saqz.example/ceret.png"
        const val TALL = "+h2000dp"
        const val HUGE = "+h2800dp"
    }
}
