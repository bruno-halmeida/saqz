package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
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
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = android.app.Application::class)
class AthleteOnboardingScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun responseSaved() = capture(false, "athlete")
    @Test fun shareFailure() = capture(true, "athlete-share-failure")
    private fun capture(failed: Boolean, name: String) {
        compose.setContent { SaqzTheme { AthleteOnboardingCard(failed, {}) } }
        compose.onRoot().captureRoboImage("screenshots/onboarding/$name.png")
    }
}
