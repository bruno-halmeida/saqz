package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.access.presentation.forgotpassword.ForgotPasswordState
import br.com.saqz.access.ui.ForgotPasswordScreen
import br.com.saqz.designsystem.UiText
import br.com.saqz.access.resources.Res as AccessRes
import br.com.saqz.access.resources.auth_error_network
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
 * As cenas da 1d (VUL-88). Arquivo próprio, e não mais um bloco do `SaqzScreenshotTest`:
 * aquele é o catálogo do design system (VUL-92) e os sete tickets de tela desta onda rodam
 * em paralelo. Nome de cena prefixado com o código da tela pelo mesmo motivo.
 *
 * Gravar: ./gradlew :android-app:recordRoborazziDevDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class Forgot1dScreenshotTest {

    private companion object {
        const val SHUTTER_MILLIS = 600L
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { SaqzTheme { content() } }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test
    fun forgotPassword() = capture("1d-esqueci-a-senha") {
        ForgotPasswordScreen(ForgotPasswordState(), {}, {}, {})
    }

    // A falha de rede: a única coisa que a 1d escreve de volta, e ela fica na tela em vez
    // de virar navegação. Resposta aceita não tem cena porque não tem tela — sai daqui.
    @Test
    fun forgotPasswordNetworkError() = capture("1d-esqueci-a-senha-erro-de-rede") {
        ForgotPasswordScreen(
            state = ForgotPasswordState(
                email = "ana@exemplo.com",
                error = UiText.Res(AccessRes.string.auth_error_network),
            ),
            onIntent = {},
            onBack = {},
            onSignIn = {},
        )
    }
}
