package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.access.presentation.newpassword.NewPasswordState
import br.com.saqz.access.ui.NewPasswordScreen
import br.com.saqz.access.ui.PasswordChangedScreen
import br.com.saqz.designsystem.theme.SaqzTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * As duas telas do VUL-90, para comparar com `1g-nova-senha.png` e `1h-senha-alterada.png`
 * do export.
 *
 * Gravar:   ./gradlew :android-app:recordRoborazziDevDebug
 * Saída:    android-app/screenshots/ — ignorada pelo git; os PNGs vivem na branch órfã
 *           `screenshots` e o PR embute o raw. Seção 11 do AGENTS.md.
 *
 * Arquivo próprio, e não uma cena no `SaqzScreenshotTest`: aquele é o catálogo do design
 * system e tem dono. O prefixo `1g`/`1h` no nome da cena é o que impede dois tickets de
 * tela em paralelo de gravarem por cima um do outro.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class NewPassword1gScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    // Depois dos 400ms da entrada do check do 1h: o obturador pega o selo assentado, e
    // não no meio do crescimento.
    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { SaqzTheme { content() } }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test
    fun novaSenha() = capture("1g-nova-senha") {
        NewPasswordScreen(state = NewPasswordState(), onIntent = {}, onBack = {})
    }

    @Test
    fun senhaAlterada() = capture("1h-senha-alterada") {
        PasswordChangedScreen(onSignIn = {}, onBack = {})
    }

    private companion object {
        const val SHUTTER_MILLIS = 600L
    }
}
