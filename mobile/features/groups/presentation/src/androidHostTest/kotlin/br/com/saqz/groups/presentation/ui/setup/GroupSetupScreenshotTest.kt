package br.com.saqz.groups.presentation.ui.setup

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzOfflineBanner
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupSheet
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.GroupSetupStep
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_setup_error_venue_address
import br.com.saqz.groups.resources.group_system_creating
import br.com.saqz.groups.resources.group_system_offline
import br.com.saqz.groups.resources.group_system_offline_title
import br.com.saqz.groups.resources.group_system_save_failure_title
import br.com.saqz.groups.resources.group_system_sending_title
import br.com.saqz.groups.resources.group_system_session_expired_title
import br.com.saqz.groups.resources.group_system_toast
import br.com.saqz.groups.resources.group_system_toast_title
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.jetbrains.compose.resources.stringResource
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
        GroupSetupScreen(
            state = GroupSetupState(
                mode = GroupSetupMode.Create,
                form = GroupSetupForm(
                    modality = GroupModality.COURT_VOLLEYBALL,
                    composition = GroupComposition.MIXED,
                    level = GroupLevel.CUSTOM,
                    playStyle = GroupPlayStyle.SIX_ZERO,
                    defaultVenue = GroupVenueForm(name = "CERET — Quadra 2", address = ""),
                    defaultCapacity = 1,
                    defaultConfirmationLeadMinutes = 360,
                ),
                errors = GroupSetupError.entries.toSet(),
            ),
            onIntent = {},
            onBack = {},
        )
    }

    @Test
    fun editForm() = capture("2i-editar-grupo") {
        GroupSetupScreen(
            state = createState(mode = GroupSetupMode.Edit(groupId = "grp-1"), photoUrl = PhotoUrl),
            onIntent = {},
            onBack = {},
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
        isOffline: Boolean = false,
        saveFailed: Boolean = false,
    ) = GroupSetupState(
        mode = mode,
        step = step,
        form = PreviewCourtForm,
        photoUrl = photoUrl,
        memberCount = 26,
        sheet = sheet,
        isOffline = isOffline,
        saveFailed = saveFailed,
    )

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-68/$name.png")
    }

    private companion object {
        const val PhotoUrl = "https://saqz.example/ceret.png"
    }
}
