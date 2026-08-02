package br.com.saqz.groups.presentation.ui.invite

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.invite.InviteLandingState
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
class InviteLandingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h1400dp")
    fun previewApproval() = capture("invite-3d-preview-approval", InviteLandingSamples.preview)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun requestSent() = capture("invite-3e-request-sent", InviteLandingSamples.requestSent)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun invalid() = capture("invite-3f-invalid", InviteLandingSamples.invalid)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun expired() = capture("invite-3f-expired", InviteLandingSamples.expired)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun rateLimited() = capture("invite-3f-rate-limited", InviteLandingSamples.rateLimited)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun planLimit() = capture("invite-3f-plan-limit", InviteLandingSamples.planLimit)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun network() = capture("invite-3f-network", InviteLandingSamples.network)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun entryOpen() = capture("invite-3l-entry-open", InviteLandingSamples.openPreview)

    private fun capture(name: String, state: InviteLandingState) = capture(name) {
        InviteLandingScreen(state = state, onBack = {}, onIntent = {})
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaqzTheme.colors.background),
                ) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-142/$name.png")
    }
}
