package br.com.saqz.groups.presentation.ui.schedule

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.presentation.schedule.GroupScheduleState
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
class GroupScheduleScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun recurring() = capture("group-schedule-recurring") {
        GroupScheduleScreen(state = previewScheduleState, onIntent = {}, onBack = {})
    }

    @Test
    fun withoutRecurrence() = capture("group-schedule-sem-recorrencia") {
        GroupScheduleScreen(
            state = previewScheduleState.copy(recurring = false, slots = emptyList()),
            onIntent = {},
            onBack = {},
        )
    }

    // Sem recorrência para o card de pausa caber na tela: com as pílulas de slot ele fica
    // abaixo da dobra e o estado "Retomar a agenda" não apareceria na captura.
    @Test
    fun paused() = capture("group-schedule-pausada") {
        GroupScheduleScreen(
            state = previewScheduleState.copy(recurring = false, slots = emptyList(), isPaused = true),
            onIntent = {},
            onBack = {},
        )
    }

    @Test
    fun loading() = capture("group-schedule-carregando") {
        GroupScheduleScreen(state = GroupScheduleState(), onIntent = {}, onBack = {})
    }

    @Test
    fun slotSheet() = capture("group-schedule-sheet-slot") {
        GroupScheduleScreen(
            state = previewScheduleState.copy(slotSheet = GroupScheduleState.NEW_SLOT),
            onIntent = {},
            onBack = {},
        )
    }

    @Test
    fun saving() = capture("group-schedule-salvando") {
        GroupScheduleScreen(
            state = previewScheduleState.copy(isSaving = true),
            onIntent = {},
            onBack = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { SaqzTheme { content() } }
        compose.onRoot().captureRoboImage("screenshots/vul-71/$name.png")
    }
}
