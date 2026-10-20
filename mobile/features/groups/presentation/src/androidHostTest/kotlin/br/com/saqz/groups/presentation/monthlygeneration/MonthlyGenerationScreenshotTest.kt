package br.com.saqz.groups.presentation.monthlygeneration

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GroupUiError
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
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class MonthlyGenerationScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val form = MonthlyForm("2026-08", "123,45", "12/08/2026", setOf("ana"))
    private val loaded = MonthlyGenerationState(
        isLoading = false, members = listOf(MonthlyMemberUi("ana", "Ana Souza"), MonthlyMemberUi("bia", "Bia Santos")), form = form,
    )
    @Test fun form() = capture("form", loaded)
    @Test fun review() = capture("review", loaded.copy(reviewing = true))
    @Test fun saving() = capture("saving", loaded.copy(reviewing = true, isSaving = true))
    @Test fun failure() = capture("failure", loaded.copy(reviewing = true, error = GroupUiError.Network))
    @Test fun empty() = capture("empty", loaded.copy(members = emptyList(), form = form.copy(selectedIds = emptySet())))
    @Test fun duePicker() = captureDuePicker("due-picker")
    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h568dp-normal-notlong-port-xhdpi", application = Application::class)
    fun duePickerSmallPhone() = captureDuePicker("due-picker-small-phone")
    private fun captureDuePicker(name: String) {
        compose.setContent { SaqzTheme { MonthlyGenerationScreen(loaded, {}, {}) } }
        compose.onNodeWithTag(MonthlyGenerationTags.Due).performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("screenshots/deb-101/monthly-generation-$name.png")
    }
    private fun capture(name: String, state: MonthlyGenerationState) {
        compose.setContent { SaqzTheme { MonthlyGenerationScreen(state, {}, {}) } }
        compose.onRoot().captureRoboImage("screenshots/monthly-generation-$name.png")
    }
}
