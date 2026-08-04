package br.com.saqz.groups.presentation.ui.home

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeState
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
class HomeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loading() = capture("home-loading", HomeState())

    @Test
    fun failure() = capture("home-failure", HomeState(isLoading = false, loadFailed = true))

    @Test
    fun content() = capture(
        "home-content",
        HomeState(isLoading = false, displayName = "Bruna"),
    )

    private fun capture(name: String, state: HomeState) {
        compose.setContent {
            SaqzTheme {
                HomeScreen(state = state, onAction = {})
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-189/$name.png")
    }
}
