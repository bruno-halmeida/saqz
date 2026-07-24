package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.access.presentation.login.LoginState
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

/**
 * Screenshots de tela em JVM (Roborazzi + Robolectric) — sem emulador.
 *
 * Gravar:   ./gradlew :android-app:recordRoborazziDevDebug
 * Saída:    android-app/screenshots/
 *
 * ponytail: sem verify/CI por enquanto — só o loop visual. Gate de regressão
 * visual entra quando os goldens estabilizarem.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Application puro: o SaqzApplication real inicia Koin/Firebase, que são estáticos
// na JVM e quebram a partir do segundo teste. Screenshot não precisa de DI.
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class SaqzScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { SaqzTheme { content() } }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test
    fun login() = capture("login") {
        LoginScreen(state = LoginState(email = "ana@saqz.app"), onIntent = {})
    }
}
