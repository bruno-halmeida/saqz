package br.com.saqz.profile.presentation.deletion

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class AccountDeletionScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun confirmation() {
        compose.setContent {
            SaqzTheme {
                AccountDeletionScreen(
                    AccountDeletionState(userId = "profile", email = "ana@example.test", isLoading = false, supportsApple = true),
                    {}, {},
                )
            }
        }
        compose.onRoot().captureRoboImage("/tmp/saqz-account-deletion.png")
    }
}
