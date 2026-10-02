package br.com.saqz.groups.presentation.ui.moderation

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.moderation.ReportReason
import br.com.saqz.groups.domain.moderation.ReportTargetType
import br.com.saqz.groups.presentation.communication.GroupThreadScreen
import br.com.saqz.groups.presentation.communication.GroupThreadState
import br.com.saqz.groups.presentation.communication.ThreadMessageUi
import br.com.saqz.groups.presentation.moderation.BlockPromptUi
import br.com.saqz.groups.presentation.moderation.ModerationFeedback
import br.com.saqz.groups.presentation.moderation.ModerationState
import br.com.saqz.groups.presentation.moderation.ReportDraftUi
import br.com.saqz.groups.presentation.moderation.ReportTargetUi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** As telas de denúncia e bloqueio que a Apple vê no vídeo da revisão (1.2). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class ModerationScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val messages = listOf(
        ThreadMessageUi(
            "m1",
            "Marina Costa",
            "Bem-vindos ao Vôlei de Quinta! Confirme sua presença até as 18h.",
            "Hoje, 17:42",
            authorId = "marina",
        ),
        ThreadMessageUi("m2", "Você", "Combinado!", "Hoje, 17:50", authorId = "me", own = true),
    )

    @Test
    fun noticesWithMenu() = captureThread("notices", GroupThreadState(loading = false, messages = messages))

    @Test
    fun noticeActions() = captureThread(
        "notice-actions",
        GroupThreadState(loading = false, messages = messages, actionsFor = messages[0]),
    )

    @Test
    fun reportSheet() = captureOverlay(
        "report-sheet",
        ModerationState(report = ReportDraftUi(
            ReportTargetUi("g", ReportTargetType.MESSAGE, "m1", "Marina Costa"),
            reason = ReportReason.OFFENSIVE,
            details = "Mensagem ofensiva para a turma.",
        )),
    )

    @Test
    fun blockSheet() = captureOverlay("block-sheet", ModerationState(block = BlockPromptUi("g", "marina", "Marina Costa")))

    @Test
    fun reportSent() = captureOverlay("report-sent", ModerationState(feedback = ModerationFeedback.ReportSent))

    private fun captureThread(name: String, state: GroupThreadState) {
        compose.setContent { SaqzTheme { GroupThreadScreen(state, notices = true, onBack = {}, onIntent = {}) } }
        settle(name)
    }

    private fun captureOverlay(name: String, state: ModerationState) {
        compose.setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    ModerationOverlay(state, onIntent = {})
                }
            }
        }
        settle(name)
    }

    private fun settle(name: String) {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/apple-review/$name.png")
    }
}
