package br.com.saqz.androidapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.access.presentation.register.RegisterEmailError
import br.com.saqz.access.presentation.register.RegisterInviteContext
import br.com.saqz.access.presentation.register.RegisterPasswordError
import br.com.saqz.access.presentation.register.RegisterState
import br.com.saqz.access.ui.RegisterScreen
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
 * Os dois quadros da 1b/1j, para comparar com `fluxo-1/1b-criar-conta.png` e
 * `fluxo-1/1j-erro-criar-conta.png`.
 *
 * Arquivo próprio, e não uma cena a mais no `SaqzScreenshotTest`: aquele é o catálogo do
 * design system, tem dono (VUL-92) e os sete tickets de tela desta onda mergeiam em
 * paralelo. Os nomes de cena são prefixados pelo código da tela pelo mesmo motivo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class Register1bScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun register1b() = capture("1b-criar-conta") {
        RegisterScreen(
            state = RegisterState(phone = "11999990000"),
            onIntent = {},
            onBack = {},
            onSignIn = {},
        )
    }

    // O mockup do 1j diz "Revise 3 campos" e desenha quatro campos errados; a tela conta em
    // tempo de execução, então este print sai com 4 — de propósito.
    @Test
    fun register1j() = capture("1j-erro-criar-conta") {
        RegisterScreen(
            state = RegisterState(
                email = "rafa@galera.com",
                phone = "(11) 9999",
                password = "12345",
                invalidName = true,
                emailError = RegisterEmailError.Taken,
                invalidPhone = true,
                passwordError = RegisterPasswordError.TooShort,
            ),
            onIntent = {},
            onBack = {},
            onSignIn = {},
        )
    }

    @Test
    fun register3mWithInvitePreview() = capture("3m-convite-com-preview") {
        RegisterScreen(
            state = RegisterState(),
            inviteContext = RegisterInviteContext.preview(
                groupName = "Vôlei do CERET",
                inviterName = "Ana",
                entryRequiresApproval = false,
            ),
            onIntent = {},
            onBack = {},
            onSignIn = {},
        )
    }

    @Test
    fun register3mWithoutInvitePreview() = capture("3m-convite-sem-preview") {
        RegisterScreen(
            state = RegisterState(),
            inviteContext = RegisterInviteContext.Generic,
            onIntent = {},
            onBack = {},
            onSignIn = {},
        )
    }

    // Mesmo obturador do catálogo: o relógio só anda quando eu mando, senão a entrada de
    // 280ms do alerta é fotografada no meio do fade.
    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { SaqzTheme { content() } }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private companion object {
        const val SHUTTER_MILLIS = 600L
    }
}
