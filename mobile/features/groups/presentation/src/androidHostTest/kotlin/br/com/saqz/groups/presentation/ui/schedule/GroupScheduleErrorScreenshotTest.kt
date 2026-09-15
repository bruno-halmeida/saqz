package br.com.saqz.groups.presentation.ui.schedule

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.schedule.GroupScheduleState
import br.com.saqz.groups.presentation.schedule.UpcomingGameStatus
import br.com.saqz.groups.presentation.schedule.UpcomingGameUi
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
class GroupScheduleErrorScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun saveError() = capture("group-schedule-erro-salvar") {
        GroupScheduleScreen(
            state = previewScheduleState.copy(error = br.com.saqz.groups.presentation.GroupUiError.Network),
            onIntent = {}, onBack = {},
        )
    }

    @Test
    fun conflict() = capture("group-schedule-conflito") {
        GroupScheduleScreen(
            state = previewScheduleState.copy(error = br.com.saqz.groups.presentation.GroupUiError.Conflict),
            onIntent = {}, onBack = {},
        )
    }

    @Test
    fun validation() = capture("group-schedule-validacao") {
        GroupScheduleScreen(
            state = previewScheduleState.copy(slots = emptyList(), error = br.com.saqz.groups.presentation.GroupUiError.Validation),
            onIntent = {}, onBack = {},
        )
    }

    @Test
    fun loadFailure() = capture("group-schedule-falha-carregar") {
        GroupScheduleScreen(
            state = GroupScheduleState(isLoading = false, loadFailed = true),
            onIntent = {}, onBack = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { SaqzTheme { content() } }
        compose.onRoot().captureRoboImage("screenshots/vul-71/$name.png")
    }
}
