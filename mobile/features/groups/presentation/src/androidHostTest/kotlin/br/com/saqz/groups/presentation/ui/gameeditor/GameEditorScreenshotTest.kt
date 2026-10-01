package br.com.saqz.groups.presentation.ui.gameeditor

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
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.presentation.gameeditor.GameEditorFields
import br.com.saqz.groups.presentation.gameeditor.GameEditorFieldError
import br.com.saqz.groups.presentation.gameeditor.GameEditorState
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
@Suppress("TooManyFunctions")
class GameEditorScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun createForm() = capture("4a-editor-form") {
        GameEditorScreen(state = previewState(), onBack = {}, onIntent = {})
    }

    @Test
    fun missingDateTime() = capture("4e-editor-missing-date-time") {
        GameEditorScreen(
            state = previewState().copy(
                form = GameEditorFields(),
                validationErrors = setOf(GameEditorFieldError.DateMissing, GameEditorFieldError.TimeMissing),
            ),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    fun missingVenue() = capture("4e-editor-missing-venue") {
        GameEditorScreen(
            state = previewState().copy(
                form = previewState().form.copy(
                    venue = GameVenue(name = "A", address = "Rua"),
                ),
                validationErrors = setOf(
                    GameEditorFieldError.VenueNameMissing,
                    GameEditorFieldError.VenueAddressMissing,
                ),
            ),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    fun conflict() = capture("4e-editor-conflict") {
        GameEditorScreen(
            state = previewState().copy(hasConflict = true, conflictGameId = "game-existing"),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    fun saveFailure() = capture("4e-editor-save-failure") {
        GameEditorScreen(state = previewState().copy(saveFailed = true), onBack = {}, onIntent = {})
    }

    @Test
    fun recurrenceOff() = capture("4a-editor-recurrence-off") {
        GameEditorScreen(state = previewState().copy(recurrenceOffered = true, groupHasVenue = true), onBack = {}, onIntent = {})
    }

    @Test
    fun recurrenceOn() = capture("4a-editor-recurrence-on") {
        GameEditorScreen(state = recurringState(), onBack = {}, onIntent = {})
    }

    @Test
    fun recurrenceOnWithoutGroupVenue() = capture("4a-editor-recurrence-on-new-venue") {
        GameEditorScreen(state = recurringState().copy(groupHasVenue = false), onBack = {}, onIntent = {})
    }

    @Test
    fun recurrenceAlreadyRecurring() = capture("4a-editor-recurrence-already") {
        GameEditorScreen(
            state = recurringState().copy(form = recurringState().form.copy(localDate = "2026-08-04", localTime = "19:30")),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    fun recurrenceWithoutDate() = capture("4a-editor-recurrence-no-date") {
        GameEditorScreen(
            state = recurringState().copy(form = recurringState().form.copy(localDate = "", localTime = "")),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    fun recurrenceFailure() = capture("4e-editor-recurrence-failure") {
        GameEditorScreen(state = recurringState().copy(recurrenceFailed = true), onBack = {}, onIntent = {})
    }

    @Test
    fun dateTimePicker() {
        compose.setContent { Themed { GameEditorScreen(state = previewState(), onBack = {}, onIntent = {}) } }
        compose.onNodeWithTag(GameEditorTags.Date).performClick()
        compose.onRoot().captureRoboImage("screenshots/vul-153/4b-editor-date-time-picker.png")
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { Themed(content) }
        compose.onRoot().captureRoboImage("screenshots/vul-153/$name.png")
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        SaqzTheme {
            Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) { content() }
        }
    }

    private fun recurringState() = previewState().copy(
        form = previewState().form.copy(localDate = "2026-08-06", localTime = "20:00", recurring = true),
        recurrenceOffered = true,
        groupHasVenue = true,
        regularSlots = listOf(
            GroupRegularSlot(weekday = GroupWeekday.TUESDAY, startTime = "19:30", durationMinutes = 120),
        ),
    )

    private fun previewState() = GameEditorState(
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
}
