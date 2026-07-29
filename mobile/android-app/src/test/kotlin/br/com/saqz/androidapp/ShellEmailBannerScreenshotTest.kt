package br.com.saqz.androidapp

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.shell_email_resent
import br.com.saqz.composeapp.resources.shell_email_unverified
import br.com.saqz.composeapp.shell.EmailVerificationBannerContent
import br.com.saqz.designsystem.SaqzBottomNav
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzNavItem
import br.com.saqz.designsystem.theme.SaqzTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * O print da faixa de e-mail não confirmado (VUL-91). A faixa não está no export — nenhuma
 * das 11 telas previa este estado —, então a cena existe para o PR poder mostrar de onde o
 * formato veio: é a posição e o desenho do `SaqzOfflineBanner`, sem o spinner.
 *
 * Arquivo próprio: o `SaqzScreenshotTest` é o catálogo do design system e tem dono (VUL-92).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class ShellEmailBannerScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun unverified() = capture("shell-email-faixa") {
        EmailVerificationBannerContent(
            message = stringResource(Res.string.shell_email_unverified),
            onResend = {},
        )
    }

    // Depois do reenvio: confirmação curta e a ação travada, para não virar botão de spam.
    @Test
    fun resent() = capture("shell-email-faixa-reenviada") {
        EmailVerificationBannerContent(
            message = stringResource(Res.string.shell_email_resent),
            onResend = null,
        )
    }

    // A faixa mora no topo do conteúdo autenticado, com a barra do 10q no rodapé: a cena
    // monta o vão do shell para o print mostrar onde ela cai.
    private fun capture(name: String, banner: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Column(
                    modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    banner()
                    SaqzBottomNav(items = NavItems, activeId = "grupos", onSelect = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-91/$name.png")
    }

    private companion object {
        val NavItems = listOf(
            SaqzNavItem("inicio", "Início", SaqzIcons.Home),
            SaqzNavItem("jogos", "Jogos", SaqzIcons.Calendar),
            SaqzNavItem("grupos", "Grupos", SaqzIcons.Users),
            SaqzNavItem("perfil", "Perfil", SaqzIcons.User),
        )
    }
}
