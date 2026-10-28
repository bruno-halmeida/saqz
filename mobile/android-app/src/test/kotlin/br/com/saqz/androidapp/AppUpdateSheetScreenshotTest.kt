package br.com.saqz.androidapp

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.access.domain.appupdate.AppUpdatePrompt
import br.com.saqz.access.presentation.login.LoginState
import br.com.saqz.access.ui.AppUpdateSheet
import br.com.saqz.access.ui.AppUpdateStore
import br.com.saqz.access.ui.LoginScreen
import br.com.saqz.designsystem.theme.SaqzTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** DEB-117: o pop-up de atualização sobre o login, que é onde ele aparece antes de entrar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class AppUpdateSheetScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun required() = capture("obrigatoria") {
        AppUpdateSheet(AppUpdatePrompt.Required, AppUpdateStore.GooglePlay, onOpenStore = {}, onDismiss = {})
    }

    @Test
    fun recommended() = capture("recomendada") {
        AppUpdateSheet(AppUpdatePrompt.Recommended("1.0.16"), AppUpdateStore.GooglePlay, onOpenStore = {}, onDismiss = {})
    }

    private fun capture(name: String, sheet: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize()) {
                    LoginScreen(LoginState(email = "ana@exemplo.com"), {}, {}, {})
                    sheet()
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("screenshots/deb-117/$name.png")
    }
}
