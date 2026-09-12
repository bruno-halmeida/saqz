package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.access.domain.appaccess.AppAccessError
import br.com.saqz.access.presentation.appaccess.AppOnboardingAuthState
import br.com.saqz.access.presentation.appaccess.AppOnboardingState
import br.com.saqz.access.ui.AppOnboardingScreen
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
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class AppOnboardingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun intro() = capture("app-onboarding-intro") {
        AppOnboardingScreen(AppOnboardingState(), {})
    }

    @Test
    fun confirmation() = capture("app-onboarding-confirmation") {
        AppOnboardingScreen(
            state = AppOnboardingState(),
            onIntent = {},
            authState = AppOnboardingAuthState.NeedsAccountConfirmation("target-owner"),
            currentAccountName = "Ana",
        )
    }

    @Test
    fun loading() = capture("app-onboarding-loading") {
        AppOnboardingScreen(
            state = AppOnboardingState(),
            onIntent = {},
            authState = AppOnboardingAuthState.Redeeming,
        )
    }

    @Test
    fun error() = capture("app-onboarding-error") {
        AppOnboardingScreen(
            state = AppOnboardingState(),
            onIntent = {},
            authState = AppOnboardingAuthState.Failed(AppAccessError.CodeInvalid),
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { SaqzTheme { content() } }
        compose.mainClock.advanceTimeBy(600L)
        compose.onRoot().captureRoboImage("screenshots/t8a-r2/$name.png")
    }
}
