package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.access.presentation.identitycompletion.IdentityCompletionState
import br.com.saqz.access.ui.IdentityCompletionScreen
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
 * O print da 1c (VUL-87), para comparar com `fluxo-1/1c-completar-cadastro.png` do export.
 *
 * Arquivo próprio, e não uma cena no `SaqzScreenshotTest`: aquele é o catálogo do design
 * system e tem dono (VUL-92). O prefixo `1c-` no nome da cena é o que impede colisão com
 * os outros seis tickets de tela da onda.
 *
 * Gravar: `./gradlew :android-app:recordRoborazziDevDebug`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class Identity1cScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun identityCompletion() = capture("1c-completar-cadastro") {
        IdentityCompletionScreen(state = IdentityCompletionState(name = "Ana Costa"), onIntent = {})
    }

    // A foto que não subiu: o aviso entra acima dos campos e o botão continua clicável.
    @Test
    fun identityCompletionPhotoFailed() = capture("1c-completar-cadastro-foto-recusada") {
        IdentityCompletionScreen(
            state = IdentityCompletionState(
                name = "Ana Costa",
                phone = "(11) 99999-0000",
                photoFailed = true,
            ),
            onIntent = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        // Mesmo obturador do catálogo: o relógio só anda quando eu mando, então a entrada
        // do alerta (280ms) já terminou quando a foto é tirada.
        compose.mainClock.autoAdvance = false
        compose.setContent { SaqzTheme { content() } }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private companion object {
        const val SHUTTER_MILLIS = 600L
    }
}
