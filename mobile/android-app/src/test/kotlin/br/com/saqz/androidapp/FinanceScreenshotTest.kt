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
import br.com.saqz.designsystem.SaqzBottomNav
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
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class FinanceScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    // VUL-200: a barra de quem administra grupo — é a única em que o item Caixa existe.
    @Test
    fun shellFinanceItem() = capture("finance-shell-item") {
        Column(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
            Spacer(Modifier.weight(1f))
            SaqzBottomNav(items = administratorNavItems(), activeId = "grupos", onSelect = {})
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { SaqzTheme { content() } }
        compose.onRoot().captureRoboImage("screenshots/vul-178/$name.png")
    }
}
