package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.ui.setup.GroupSetupScreen
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
class TrialGroupSetupScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun firstGroupOffer() {
        compose.setContent {
            SaqzTheme {
                GroupSetupScreen(GroupSetupState(mode = GroupSetupMode.Create), {}, {}, showTrialOffer = true)
            }
        }
        compose.onRoot().captureRoboImage("screenshots/trial/group-setup.png")
    }
}
