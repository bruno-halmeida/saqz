package br.com.saqz.groups.presentation.whatsappbinding

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.communication.GroupWhatsAppStatus
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** O vínculo com o WhatsApp: vazio com o link "Como funciona", a folha aberta e o grupo vinculado. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class WhatsAppBindingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun empty() = capture("whatsapp-binding-empty", WhatsAppBindingState(loading = false))

    @Test
    fun howItWorks() = capture("whatsapp-binding-how-it-works", WhatsAppBindingState(loading = false, howItWorksOpen = true))

    @Test
    fun active() = capture(
        "whatsapp-binding-active",
        WhatsAppBindingState(loading = false, bound = true, groupName = "Vôlei do CERET", status = GroupWhatsAppStatus.ACTIVE),
    )

    private fun capture(name: String, state: WhatsAppBindingState) {
        compose.setContent {
            SaqzTheme {
                WhatsAppBindingScreen(state = state, onIntent = {}, onBack = {})
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/onboarding/$name.png")
    }
}
