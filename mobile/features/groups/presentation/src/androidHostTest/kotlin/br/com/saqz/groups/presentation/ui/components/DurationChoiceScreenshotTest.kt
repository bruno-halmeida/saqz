package br.com.saqz.groups.presentation.ui.components

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.presentation.duration.DurationDraft
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
 * DEB-105: o "Outros" (horas cheias de 3 a 8, teclado numérico) nos três lugares. Em celular pequeno (320x568dp) a cena
 * reserva o espaço do teclado numérico (a tela já usa `imePadding`; o Robolectric não abre teclado de verdade).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
@Suppress("TooManyFunctions")
class DurationChoiceScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    // ---- Editor de jogo ----

    @Test
    fun gameOther() = captureGame("game-other", DurationDraft(180, "3"))

    @Test
    fun gameError() = captureGame("game-error", DurationDraft(0, "9"), error = true)

    @Test
    fun gameLegacy() = captureGame("game-legacy-3h15", DurationDraft(195))

    @Test
    @Config(sdk = [35], qualifiers = SMALL_PHONE, application = Application::class)
    fun gameSmallPhoneKeyboard() = captureGame("game-small-phone-keyboard", DurationDraft(0, "9"), error = true, keyboard = true)

    // ---- Duração padrão do grupo ----

    @Test
    fun groupOther() = captureGroup("group-other", DurationDraft(300, "5"))

    @Test
    fun groupError() = captureGroup("group-error", DurationDraft(0, ""), error = true)

    @Test
    @Config(sdk = [35], qualifiers = SMALL_PHONE, application = Application::class)
    fun groupSmallPhoneKeyboard() = captureGroup("group-small-phone-keyboard", DurationDraft(0, "9"), error = true, keyboard = true)

    // ---- Horários regulares ----

    @Test
    fun scheduleOther() = captureSchedule("schedule-other", DurationDraft(240, "4"))

    @Test
    fun scheduleError() = captureSchedule("schedule-error", DurationDraft(0, "2"), error = true)

    @Test
    @Config(sdk = [35], qualifiers = SMALL_PHONE, application = Application::class)
    fun scheduleSmallPhoneKeyboard() =
        captureSchedule("schedule-small-phone-keyboard", DurationDraft(0, "2"), error = true, keyboard = true)

    private fun captureGame(name: String, draft: DurationDraft, error: Boolean = false, keyboard: Boolean = false) {
        val state = GameEditorState(
            isLoading = false,
            groupName = "Vôlei do CERET",
            zoneId = "America/Sao_Paulo",
            form = GameEditorFields(
                localDate = "2026-08-04",
                localTime = "19:30",
                durationMinutes = draft.minutes,
                durationText = draft.text,
                venue = GameVenue(name = "CERET", address = "R. Canuto Abreu"),
                capacity = 12,
                confirmationLeadMinutes = 360,
            ),
            validationErrors = if (error) setOf(GameEditorFieldError.DurationInvalid) else emptySet(),
        )
        capture(name, keyboard, scrollTarget(draft)) {
            GameEditorScreen(state = state, onBack = {}, onIntent = {})
        }
    }

    private fun captureGroup(name: String, draft: DurationDraft, error: Boolean = false, keyboard: Boolean = false) {
        val state = GroupSetupState(
            mode = GroupSetupMode.Edit("grp-1"),
            form = PreviewCourtForm,
            canManageGameConfig = true,
            durationMinutes = draft.minutes,
            durationText = draft.text,
            errors = if (error) setOf(GroupSetupError.DurationOutOfRange) else emptySet(),
        )
        capture(name, keyboard, scrollTarget(draft)) { GroupSetupScreen(state = state, onIntent = {}, onBack = {}) }
    }

    private fun captureSchedule(name: String, draft: DurationDraft, error: Boolean = false, keyboard: Boolean = false) {
        val state: GroupScheduleState = previewScheduleState.copy(
            durationMinutes = draft.minutes,
            durationText = draft.text,
            durationError = error,
        )
        capture(name, keyboard, scrollTarget(draft)) {
            GroupScheduleScreen(state = state, onIntent = {}, onBack = {})
        }
    }

    /** O campo quando o "Outros" está em edição (é onde o teclado está); senão o chip. */
    private fun scrollTarget(draft: DurationDraft) =
        if (draft.text != null) DurationChoiceTags.Field else DurationChoiceTags.Other

    private fun capture(name: String, keyboard: Boolean, scrollTag: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Column(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    Box(Modifier.weight(1f).fillMaxWidth().imePadding()) { content() }
                    if (keyboard) Box(Modifier.fillMaxWidth().height(KEYBOARD_HEIGHT.dp).background(SaqzTheme.colors.border))
                }
            }
        }
        compose.onNodeWithTag(scrollTag).performScrollTo()
        compose.onRoot().captureRoboImage("screenshots/deb-105/$name.png")
    }

    private companion object {
        const val SMALL_PHONE = "w320dp-h568dp-normal-notlong-port-xhdpi"
        const val KEYBOARD_HEIGHT = 216
    }
}
