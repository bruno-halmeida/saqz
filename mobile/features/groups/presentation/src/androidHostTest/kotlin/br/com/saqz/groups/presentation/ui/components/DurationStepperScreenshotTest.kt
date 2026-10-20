package br.com.saqz.groups.presentation.ui.components

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.presentation.gameeditor.GameEditorFieldError
import br.com.saqz.groups.presentation.gameeditor.GameEditorFields
import br.com.saqz.groups.presentation.gameeditor.GameEditorState
import br.com.saqz.groups.presentation.schedule.GroupScheduleState
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.ui.gameeditor.GameEditorScreen
import br.com.saqz.groups.presentation.ui.schedule.GroupScheduleScreen
import br.com.saqz.groups.presentation.ui.schedule.previewScheduleState
import br.com.saqz.groups.presentation.ui.setup.GroupSetupScreen
import br.com.saqz.groups.presentation.ui.setup.PreviewCourtForm
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * O contador de duração nos três lugares (editor de jogo, duração do grupo e horários regulares), com valor da grade,
 * valor salvo fora dela (3h15) e erro. Em celular pequeno (320x568dp) confere que a linha [–] valor [+] cabe.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
@Suppress("TooManyFunctions")
class DurationStepperScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    // ---- Editor de jogo ----

    @Test
    fun gameGrid() = captureGame("game-grid-2h", 120)

    @Test
    fun gameError() = captureGame("game-error", 45, error = true)

    @Test
    fun gameOffGrid() = captureGame("game-off-grid-3h15", 195)

    @Test
    @Config(sdk = [35], qualifiers = SMALL_PHONE, application = Application::class)
    fun gameSmallPhone() = captureGame("game-small-phone", 195, error = true)

    // ---- Duração padrão do grupo ----

    @Test
    fun groupGrid() = captureGroup("group-grid-5h", 300)

    @Test
    fun groupError() = captureGroup("group-error", 540, error = true)

    @Test
    @Config(sdk = [35], qualifiers = SMALL_PHONE, application = Application::class)
    fun groupSmallPhone() = captureGroup("group-small-phone", 150)

    // ---- Horários regulares ----

    @Test
    fun scheduleGrid() = captureSchedule("schedule-grid-4h", 240)

    @Test
    fun scheduleError() = captureSchedule("schedule-error", 45, error = true)

    @Test
    @Config(sdk = [35], qualifiers = SMALL_PHONE, application = Application::class)
    fun scheduleSmallPhone() = captureSchedule("schedule-small-phone", 195, error = true)

    private fun captureGame(name: String, minutes: Int, error: Boolean = false) {
        val state = GameEditorState(
            isLoading = false,
            groupName = "Vôlei do CERET",
            zoneId = "America/Sao_Paulo",
            form = GameEditorFields(
                localDate = "2026-08-04",
                localTime = "19:30",
                durationMinutes = minutes,
                venue = GameVenue(name = "CERET", address = "R. Canuto Abreu"),
                capacity = 12,
                confirmationLeadMinutes = 360,
            ),
            validationErrors = if (error) setOf(GameEditorFieldError.DurationInvalid) else emptySet(),
        )
        capture(name) {
            GameEditorScreen(state = state, onBack = {}, onIntent = {})
        }
    }

    private fun captureGroup(name: String, minutes: Int, error: Boolean = false) {
        val state = GroupSetupState(
            mode = GroupSetupMode.Edit("grp-1"),
            form = PreviewCourtForm,
            canManageGameConfig = true,
            durationMinutes = minutes,
            errors = if (error) setOf(GroupSetupError.DurationOutOfRange) else emptySet(),
        )
        capture(name) { GroupSetupScreen(state = state, onIntent = {}, onBack = {}) }
    }

    private fun captureSchedule(name: String, minutes: Int, error: Boolean = false) {
        val state: GroupScheduleState = previewScheduleState.copy(
            durationMinutes = minutes,
            durationError = error,
        )
        capture(name) {
            GroupScheduleScreen(state = state, onIntent = {}, onBack = {})
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Column(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                }
            }
        }
        compose.onNodeWithContentDescription("Aumentar a duração").performScrollTo()
        compose.onRoot().captureRoboImage("screenshots/duration-stepper/$name.png")
    }

    private companion object {
        const val SMALL_PHONE = "w320dp-h568dp-normal-notlong-port-xhdpi"
    }
}
