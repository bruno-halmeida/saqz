package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.access.presentation.login.LoginState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.login_error_credentials
import br.com.saqz.access.resources.login_error_email_invalid
import br.com.saqz.access.resources.login_error_password
import br.com.saqz.access.ui.LoginScreen
import br.com.saqz.designsystem.UiText
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
 * As duas cenas da tela de entrar — 1a limpa e 1i recusada — para comparar com o export.
 *
 * Arquivo próprio, e não uma cena a mais no `SaqzScreenshotTest`: aquele é o catálogo do
 * design system e tem dono; tela de jornada mora no arquivo da tela, com o código dela no
 * prefixo do nome para nenhum ticket da onda colidir com outro.
 *
 * Gravar: `./gradlew :android-app:recordRoborazziDevDebug`. Saída em
 * `android-app/screenshots/`, ignorada pelo git — os PNGs vivem na branch órfã
 * `screenshots` e o PR embute o raw. Seção 11 do AGENTS.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Application puro: o SaqzApplication real inicia Koin/Firebase, estáticos na JVM, e
// quebram a partir do segundo teste. Screenshot não precisa de DI.
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class Login1aScreenshotTest {

    private companion object {
        // Mesma fase de obturador do catálogo: as transições de entrada — inclusive os
        // 280ms do `SaqzInlineAlert` do 1i — já terminaram.
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

    // Formulário vazio, que é como o export desenha o 1a: os dois campos mostrando o
    // placeholder, e não um valor digitado.
    @Test
    fun login1a() = capture("1a-login") {
        LoginScreen(
            state = LoginState(),
            onIntent = {},
            onCreateAccount = {},
            onForgotPassword = {},
        )
    }

    // O 1i do export mostra as quatro diferenças de uma vez: sem subtítulo, alerta acima
    // dos campos, erro em cada campo e o contador embaixo do botão. Em uso real o erro de
    // e-mail (local) e a recusa do provedor não coincidem — a cena os junta porque é o que
    // o desenho desenha, e é assim que a comparação lado a lado fica possível.
    @Test
    fun login1i() = capture("1i-erro-login") {
        LoginScreen(
            state = LoginState(
                email = "ana@exemplo",
                password = "12345678",
                error = UiText.Res(Res.string.login_error_credentials),
                emailError = UiText.Res(Res.string.login_error_email_invalid),
                passwordError = UiText.Res(Res.string.login_error_password),
                failedAttempts = 2,
            ),
            onIntent = {},
            onCreateAccount = {},
            onForgotPassword = {},
        )
    }
}
