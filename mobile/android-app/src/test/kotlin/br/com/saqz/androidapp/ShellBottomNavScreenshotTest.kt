package br.com.saqz.androidapp

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.shell_nav_finance
import br.com.saqz.composeapp.resources.shell_nav_groups
import br.com.saqz.composeapp.resources.shell_nav_home
import br.com.saqz.composeapp.resources.shell_nav_profile
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
 * VUL-200: os dois estados da barra do 10q depois que Jogos saiu — três abas para membro
 * comum, quatro para quem administra algum grupo.
 *
 * A cena monta o `SaqzBottomNav` direto porque o `SaqzAppShell` é `internal` do
 * `:compose-app`: os rótulos vêm dos mesmos `shell_nav_*` que o shell usa, então o que pode
 * divergir daqui é só a ordem. Quem manda continua sendo o `shellNavItems` do shell.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class ShellBottomNavScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun memberBar() = capture("bottom-nav-membro") { SaqzBottomNav(memberNavItems(), "inicio", {}) }

    @Test
    fun administratorBar() = capture("bottom-nav-admin") {
        SaqzBottomNav(administratorNavItems(), "inicio", {})
    }

    private fun capture(name: String, bar: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Column(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    Spacer(Modifier.weight(1f))
                    bar()
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-200/$name.png")
    }
}

/** Membro comum: Início, Grupos, Perfil. */
@Composable
internal fun memberNavItems() = listOf(
    SaqzNavItem("inicio", stringResource(Res.string.shell_nav_home), SaqzIcons.Home),
    SaqzNavItem("grupos", stringResource(Res.string.shell_nav_groups), SaqzIcons.Users),
    SaqzNavItem("perfil", stringResource(Res.string.shell_nav_profile), SaqzIcons.User),
)

/** Quem administra algum grupo: as mesmas três mais a Caixa. */
@Composable
internal fun administratorNavItems() = listOf(
    SaqzNavItem("inicio", stringResource(Res.string.shell_nav_home), SaqzIcons.Home),
    SaqzNavItem("grupos", stringResource(Res.string.shell_nav_groups), SaqzIcons.Users),
    SaqzNavItem("financeiro", stringResource(Res.string.shell_nav_finance), SaqzIcons.CreditCard),
    SaqzNavItem("perfil", stringResource(Res.string.shell_nav_profile), SaqzIcons.User),
)
